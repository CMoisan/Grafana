// =============================================================================
// TEST 5/5 : SIMULATION D'USAGE REALISTE - le script a montrer en entretien
// =============================================================================
// Ce n'est pas un test de performance : c'est un GENERATEUR DE TRAFIC CREDIBLE,
// concu pour alimenter les dashboards Grafana avec des donnees qui ressemblent
// a de la vraie vie. C'est exactement ce qu'un Solutions Engineer construit
// pour une demo ou un POC client.
//
// CE QU'IL REPRODUIT :
//   - plusieurs PERSONAS aux comportements differents ;
//   - une journee compressee (creux de nuit / pic de midi) ;
//   - des utilisateurs qui abandonnent leur panier ;
//   - des clients qui annulent, des visiteurs qui n'achetent pas, des bots ;
//   - une INJECTION DE PANNE programmee au milieu du run.
//
// Lancez-le, ouvrez le dashboard : vous avez une demo de 15 minutes cle en main.
// =============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, buildOrderPayload } from './lib/config.js';

const paniersAbandonnes = new Counter('paniers_abandonnes_total');
const tauxConversion = new Rate('taux_conversion');

export const options = {
  scenarios: {
    // --------------------------------------------------------------------
    // PERSONA 1 : l'acheteur. Parcours complet, il paie.
    // Profil en cloche : creux de nuit, montee, pic de midi, redescente.
    // --------------------------------------------------------------------
    acheteurs: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 30,
      maxVUs: 150,
      stages: [
        { target: 5,  duration: '2m' },
        { target: 20, duration: '3m' },
        { target: 45, duration: '3m' },
        { target: 20, duration: '3m' },
        { target: 8,  duration: '4m' },
      ],
      exec: 'acheteur',
      tags: { persona: 'acheteur' },
    },

    // --------------------------------------------------------------------
    // PERSONA 2 : le visiteur. Il consulte, il n'achete pas.
    // En e-commerce reel c'est 90 a 97% du trafic. L'oublier fausse
    // completement le dimensionnement des lectures.
    // --------------------------------------------------------------------
    visiteurs: {
      executor: 'constant-arrival-rate',
      rate: 40,
      timeUnit: '1s',
      duration: '15m',
      preAllocatedVUs: 50,
      maxVUs: 200,
      exec: 'visiteur',
      tags: { persona: 'visiteur' },
    },

    // --------------------------------------------------------------------
    // PERSONA 3 : l'indecis. Il cree un panier et disparait.
    // Genere des commandes PENDING qui s'accumulent : c'est ce qui rend
    // vivante la gauge orders_in_status{status="PENDING"}.
    // --------------------------------------------------------------------
    indecis: {
      executor: 'constant-arrival-rate',
      rate: 8,
      timeUnit: '1s',
      duration: '15m',
      preAllocatedVUs: 15,
      maxVUs: 60,
      exec: 'indecis',
      tags: { persona: 'indecis' },
    },

    // --------------------------------------------------------------------
    // PERSONA 4 : le bot mal code. Il tape des URLs inexistantes.
    // Genere des 404 : indispensable pour DEMONTRER que les 4xx ne
    // consomment pas le budget d'erreur du SLO. Sans ce trafic, la
    // demonstration reste theorique.
    // --------------------------------------------------------------------
    bots: {
      executor: 'constant-arrival-rate',
      rate: 3,
      timeUnit: '1s',
      duration: '15m',
      preAllocatedVUs: 5,
      maxVUs: 20,
      exec: 'bot',
      tags: { persona: 'bot' },
    },

    // --------------------------------------------------------------------
    // SCENARIO SPECIAL : l'injecteur de panne.
    // Un seul VU, une seule iteration, demarree apres 6 minutes (startTime).
    // Il appelle le chaos panel pour degrader le service, puis le repare.
    // C'est LA mecanique de demo : on annonce ce qui va se passer, le
    // dashboard vire au rouge en direct, l'alerte part, on repare, tout
    // revient au vert. Rien ne convainc plus efficacement un client.
    // --------------------------------------------------------------------
    injecteur_de_panne: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      startTime: '6m',
      maxDuration: '10m',
      exec: 'injecterPanne',
      tags: { persona: 'chaos' },
    },
  },

  thresholds: {
    // On tolere plus d'erreurs qu'ailleurs : la panne est VOLONTAIRE.
    http_req_failed: ['rate<0.15'],
    // Le seuil qui a du sens ici est METIER : le taux de conversion.
    taux_conversion: ['rate>0.5'],
  },

  tags: { testid: __ENV.TEST_RUN || 'simulation', test_type: 'simulation' },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 404, 409));

export function acheteur() {
  // 1. Il regarde la liste
  http.get(`${BASE_URL}/api/orders?limit=10`, { tags: { name: 'GET /api/orders' } });
  sleep(Math.random() * 3 + 1);

  // 2. Il cree son panier
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify(buildOrderPayload(`buyer-${__VU}`)),
    { headers: JSON_HEADERS, tags: { name: 'POST /api/orders' } }
  );
  if (res.status !== 201) {
    tauxConversion.add(false);
    return;
  }
  const id = res.json('id');

  // 3. Il hesite : temps de reflexion realiste, sans lequel on simule un robot.
  sleep(Math.random() * 5 + 2);

  // 4. Il paie
  const pay = http.post(`${BASE_URL}/api/orders/${id}/pay`, null, {
    headers: JSON_HEADERS,
    tags: { name: 'POST /api/orders/{id}/pay' },
  });
  const converti = pay.status === 200 && pay.json('status') === 'PAID';
  tauxConversion.add(converti);

  // 5. Une commande payee sur trois est expediee dans la foulee
  if (converti && Math.random() < 0.33) {
    sleep(1);
    http.post(`${BASE_URL}/api/orders/${id}/ship`, null, {
      headers: JSON_HEADERS,
      tags: { name: 'POST /api/orders/{id}/ship' },
    });
  }
}

export function visiteur() {
  const filtres = ['', '?status=PAID', '?status=PENDING', '?limit=50'];
  const q = filtres[Math.floor(Math.random() * filtres.length)];
  const res = http.get(`${BASE_URL}/api/orders${q}`, {
    tags: { name: 'GET /api/orders' },
  });
  check(res, { 'consultation ok': (r) => r.status === 200 });
  sleep(Math.random() * 4 + 1);
}

export function indecis() {
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify(buildOrderPayload(`hesitant-${__VU}`)),
    { headers: JSON_HEADERS, tags: { name: 'POST /api/orders' } }
  );
  if (res.status === 201) {
    paniersAbandonnes.add(1);
    tauxConversion.add(false);
    // 1 sur 4 annule explicitement, les autres laissent le panier en PENDING.
    if (Math.random() < 0.25) {
      sleep(Math.random() * 8 + 2);
      http.post(`${BASE_URL}/api/orders/${res.json('id')}/cancel`, null, {
        headers: JSON_HEADERS,
        tags: { name: 'POST /api/orders/{id}/cancel' },
      });
    }
  }
  sleep(Math.random() * 6 + 2);
}

export function bot() {
  const cibles = [
    '/api/orders/00000000-0000-0000-0000-000000000000',
    '/api/orders/admin',
    '/api/orders/999999',
    '/api/orders/undefined',
  ];
  const cible = cibles[Math.floor(Math.random() * cibles.length)];
  const res = http.get(`${BASE_URL}${cible}`, {
    // Tag fige : les 4 URLs sont regroupees sous un seul nom de metrique.
    tags: { name: 'GET /api/orders/{id} (bot)' },
  });
  check(res, { 'refuse proprement en 4xx': (r) => r.status >= 400 && r.status < 500 });
  sleep(Math.random() * 5 + 1);
}

/**
 * ===========================================================================
 * L'INJECTEUR DE PANNE : le scenario qui fait la demo.
 * ===========================================================================
 * Deroule, minute par minute, a partir de T+6min :
 *
 *   T+6   : latence du paiement +700 ms  -> le p95/p99 decroche, l'alerte
 *           OrdersApiHighLatency passe en Pending puis Firing apres 5 min.
 *   T+9   : la passerelle tombe a 40%     -> des 503 apparaissent, l'alerte
 *           PaymentGatewayDown se declenche, le burn rate SLO s'emballe.
 *   T+11  : on repare tout                -> les courbes redescendent, les
 *           alertes se resolvent d'elles-memes.
 *
 * PENDANT CE TEMPS, DANS GRAFANA, MONTREZ CE PARCOURS :
 *   1. le dashboard RED : le p99 decroche, les 5xx apparaissent ;
 *   2. l'onglet Alerting : l'alerte passe Pending -> Firing ;
 *   3. Explore / Loki, meme fenetre de temps :
 *        {namespace="apps", app="orders-api"} | json | level="WARN"
 *      -> on lit le log `event=chaos_updated` a l'instant exact du pic ;
 *   4. conclusion : "la metrique dit QUAND et COMBIEN, le log dit POURQUOI".
 *      C'est la phrase a retenir sur la complementarite des signaux.
 * ===========================================================================
 */
export function injecterPanne() {
  console.log('[CHAOS] T+6 : injection de 700 ms de latence sur le paiement');
  http.post(
    `${BASE_URL}/api/chaos`,
    JSON.stringify({ extraLatencyMs: 700 }),
    { headers: JSON_HEADERS, tags: { name: 'POST /api/chaos' } }
  );

  // sleep() accepte des secondes. 180 s = 3 minutes.
  sleep(180);

  console.log('[CHAOS] T+9 : la passerelle de paiement tombe (40% de pannes)');
  http.post(
    `${BASE_URL}/api/chaos`,
    JSON.stringify({ gatewayFailureRatePercent: 40, httpErrorRatePercent: 8 }),
    { headers: JSON_HEADERS, tags: { name: 'POST /api/chaos' } }
  );

  sleep(120);

  console.log('[CHAOS] T+11 : reparation, retour a la normale');
  http.post(`${BASE_URL}/api/chaos/reset`, null, {
    headers: JSON_HEADERS,
    tags: { name: 'POST /api/chaos/reset' },
  });
}

/**
 * ===========================================================================
 * `handleSummary` : personnaliser le rapport de fin de test.
 * ===========================================================================
 * Appelee une fois a la fin. La cle de l'objet retourne est un chemin de
 * fichier ("stdout" pour la console). C'est ici qu'on branche un rapport HTML,
 * un export JSON pour la CI, ou un envoi vers un systeme tiers.
 */
export function handleSummary(data) {
  const conversions = data.metrics.taux_conversion
    ? (data.metrics.taux_conversion.values.rate * 100).toFixed(1)
    : 'n/a';
  const p95 = data.metrics.http_req_duration
    ? data.metrics.http_req_duration.values['p(95)'].toFixed(0)
    : 'n/a';

  const texte = [
    '',
    '=========================================',
    ' RESUME METIER DE LA SIMULATION',
    '=========================================',
    ` Taux de conversion : ${conversions} %`,
    ` Latence p95        : ${p95} ms`,
    ` Requetes totales   : ${data.metrics.http_reqs.values.count}`,
    '=========================================',
    '',
  ].join('\n');

  return {
    stdout: texte,
    // Fichier JSON complet, exploitable par un job CI ou un script de
    // comparaison entre deux versions.
    'k6-results/simulation-summary.json': JSON.stringify(data, null, 2),
  };
}
