// =============================================================================
// TEST 3/5 : STRESS TEST (recherche du point de rupture)
// =============================================================================
// OBJECTIF : trouver OU le systeme casse, et surtout COMMENT il casse.
//
// LA VRAIE QUESTION N'EST PAS "combien de req/s ?" MAIS :
//   - le systeme se degrade-t-il PROGRESSIVEMENT ou s'effondre-t-il d'un coup ?
//   - RECUPERE-T-IL tout seul apres la surcharge ? (voir les paliers finaux)
//   - quel signal apparait EN PREMIER ? (latence, erreurs, CPU, GC, threads)
//     C'est ce signal precoce qui doit devenir votre alerte.
//
// C'est ici que le lab prend tout son sens : lancez ce test ET regardez le
// dashboard Grafana EN MEME TEMPS. Le test donne les chiffres cote client,
// Grafana donne l'explication cote serveur. Les deux ensemble = un diagnostic.
// =============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JSON_HEADERS, buildOrderPayload } from './lib/config.js';

export const options = {
  scenarios: {
    montee_en_charge: {
      // `ramping-arrival-rate` : on augmente le DEBIT par paliers.
      // Les paliers plats (et non une rampe continue) sont essentiels : ils
      // laissent le systeme se stabiliser et permettent de lire une valeur
      // fiable a chaque niveau. Une rampe continue ne mesure qu'un transitoire.
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 800,
      stages: [
        { target: 10,  duration: '30s' },  // echauffement (laisse le JIT chauffer)
        { target: 50,  duration: '1m'  },
        { target: 50,  duration: '1m'  },  // palier : on lit la valeur ici
        { target: 100, duration: '1m'  },
        { target: 100, duration: '1m'  },
        { target: 200, duration: '1m'  },
        { target: 200, duration: '1m'  },
        { target: 400, duration: '1m'  },  // au-dela du raisonnable
        { target: 400, duration: '1m'  },
        // LES DEUX DERNIERS PALIERS SONT LES PLUS INSTRUCTIFS :
        // on redescend au niveau nominal. Le systeme revient-il a une latence
        // normale ? Combien de temps met-il ? Un systeme qui ne recupere pas
        // seul apres une surcharge a un probleme structurel (file d'attente
        // sans limite, pool epuise, retry en tempete).
        { target: 30,  duration: '2m'  },
      ],
      exec: 'chargeMixte',
    },
  },

  // PAS de threshold bloquant ici : on S'ATTEND a ce que ca casse.
  // On declare quand meme des seuils avec `abortOnFail` desactive, pour
  // documenter la cible sans faire echouer volontairement le pipeline.
  thresholds: {
    http_req_duration: [{ threshold: 'p(95)<3000', abortOnFail: false }],
    http_req_failed: [{ threshold: 'rate<0.10', abortOnFail: false }],
  },

  tags: { testid: __ENV.TEST_RUN || 'stress', test_type: 'stress' },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 404, 409));

export function chargeMixte() {
  // 70% de lectures, 30% d'ecritures : proportion typique d'une API e-commerce.
  // Adaptez-la a VOS chiffres de production, lisibles dans Mimir :
  //   sum by (method) (rate(http_server_requests_seconds_count[1h]))
  if (Math.random() < 0.7) {
    const res = http.get(`${BASE_URL}/api/orders?limit=10`, {
      tags: { name: 'GET /api/orders' },
    });
    check(res, { 'lecture ok': (r) => r.status === 200 });
  } else {
    const res = http.post(
      `${BASE_URL}/api/orders`,
      JSON.stringify(buildOrderPayload(`stress-${__VU}`)),
      { headers: JSON_HEADERS, tags: { name: 'POST /api/orders' } }
    );
    check(res, { 'creation ok': (r) => r.status === 201 });
  }
  sleep(0.1);
}

// -----------------------------------------------------------------------------
// A OBSERVER DANS GRAFANA PENDANT CE TEST (dans cet ordre) :
//
// 1. La latence p99 decroche AVANT le p50 -> saturation du pool de threads
//    Tomcat (200 par defaut). Les requetes attendent dans la file d'acceptation.
//
// 2. container_cpu_cfs_throttled_seconds_total augmente -> vous avez atteint
//    `limits.cpu`. Le conteneur est ralenti par le noyau. Cause de latence
//    la plus sous-diagnostiquee sur Kubernetes.
//
// 3. jvm_gc_pause_seconds monte -> pression memoire, le GC vole du temps CPU.
//
// 4. Les erreurs apparaissent EN DERNIER. C'est le point cle a comprendre :
//    quand vos alertes d'erreur se declenchent, vos utilisateurs souffrent
//    depuis longtemps. C'est precisement pourquoi on alerte sur la LATENCE
//    (signal precoce) et pas seulement sur les erreurs (signal tardif).
// -----------------------------------------------------------------------------
