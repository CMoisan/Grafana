// =============================================================================
// TEST 4/5 : SOAK TEST (endurance)
// =============================================================================
// OBJECTIF : charge MODEREE mais LONGUE (30 min ici, 4 a 24 h en vrai).
//
// CE QU'IL DETECTE, ET QU'AUCUN AUTRE TEST NE VOIT :
//   - fuites memoire : jvm_memory_used_bytes{area="heap"} monte en escalier
//     sans jamais redescendre apres un GC complet ;
//   - fuites de connexions ou de descripteurs de fichiers ;
//   - degradation liee a l'accumulation de donnees (notre Map en memoire !) ;
//   - rotation de logs, disque plein, tokens qui expirent ;
//   - fuite de threads (jvm_threads_live_threads en croissance continue).
//
// UN STRESS TEST DE 10 MINUTES NE TROUVERA JAMAIS CA. Beaucoup d'equipes ne
// font que des stress tests et decouvrent leurs fuites en production, le
// week-end. Savoir expliquer cette difference vous positionne immediatement
// comme quelqu'un qui a vecu de la production.
// =============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, buildOrderPayload } from './lib/config.js';

// On suit la latence dans le temps pour comparer le debut et la fin du run.
const latenceParcours = new Trend('parcours_duration', true);

export const options = {
  scenarios: {
    endurance: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      // Passez a '4h' pour un vrai soak. 30 min suffisent en lab pour voir
      // la tendance memoire s'amorcer.
      duration: __ENV.SOAK_DURATION || '30m',
      preAllocatedVUs: 40,
      maxVUs: 100,
      exec: 'parcours',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    // LE seuil qui compte en soak : la latence doit rester stable du debut
    // a la fin. Si le p95 global passe, mais que la derniere demi-heure est
    // deux fois plus lente que la premiere, vous avez une fuite. Comparez
    // toujours les 5 premieres et les 5 dernieres minutes dans Grafana.
    http_req_duration: ['p(95)<800'],
  },
  tags: { testid: __ENV.TEST_RUN || 'soak', test_type: 'soak' },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 404, 409));

export function parcours() {
  const start = Date.now();

  const createRes = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify(buildOrderPayload(`soak-${__VU}`)),
    { headers: JSON_HEADERS, tags: { name: 'POST /api/orders' } }
  );

  if (check(createRes, { 'creee': (r) => r.status === 201 })) {
    const id = createRes.json('id');
    sleep(0.5);
    http.post(`${BASE_URL}/api/orders/${id}/pay`, null, {
      headers: JSON_HEADERS,
      tags: { name: 'POST /api/orders/{id}/pay' },
    });
  }

  latenceParcours.add(Date.now() - start);
  sleep(1);
}

// -----------------------------------------------------------------------------
// REQUETE PROMQL A GARDER SOUS LA MAIN PENDANT UN SOAK
// (a coller dans Grafana Explore, datasource Mimir) :
//
//   # tendance du heap sur 1h : si la pente reste positive, c'est une fuite
//   deriv(jvm_memory_used_bytes{area="heap", namespace="apps"}[1h])
//
//   # nombre de threads : doit se stabiliser sur un plateau
//   jvm_threads_live_threads{namespace="apps"}
//
//   # comparaison latence debut vs fin (offset regarde dans le passe)
//   histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
//     /
//   histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m] offset 25m)))
//   # un ratio > 1.5 signale une degradation dans le temps
// -----------------------------------------------------------------------------
