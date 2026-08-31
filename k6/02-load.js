// =============================================================================
// TEST 2/5 : LOAD TEST (test de charge nominal)
// =============================================================================
// OBJECTIF : verifier que le systeme tient la charge ATTENDUE en production.
// On ne cherche pas la limite : on valide un niveau de service cible.
//
// LA QUESTION PREALABLE, TOUJOURS : "quelle est la charge reelle ?"
// Elle se lit dans vos metriques Prometheus, pas dans une intuition :
//     max_over_time(sum(rate(http_server_requests_seconds_count[5m]))[7d:5m])
// Un test de charge cale sur un chiffre invente ne prouve rien. C'est le
// premier reflexe a montrer : partir de la donnee de production.
// =============================================================================
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { BASE_URL, JSON_HEADERS, buildOrderPayload, DEFAULT_THRESHOLDS } from './lib/config.js';

// -----------------------------------------------------------------------------
// METRIQUES PERSONNALISEES
// -----------------------------------------------------------------------------
// k6 fournit 4 types, exactement comme Prometheus :
//   Counter : ne fait que monter
//   Gauge   : derniere valeur
//   Rate    : pourcentage de valeurs "vraies"
//   Trend   : distribution (min/avg/med/p90/p95/max) -> pour les durees
// Elles apparaissent dans le resume final ET dans Grafana si vous exportez
// vers Mimir. Definir des metriques METIER cote test est ce qui permet de dire
// "le tunnel d'achat complet prend 1.2 s au p95", ce qu'aucune metrique HTTP
// individuelle ne dira jamais.
// -----------------------------------------------------------------------------
const checkoutDuration = new Trend('checkout_duration', true); // true = c'est une duree
const paymentDeclined = new Rate('payment_declined_rate');
const ordersCreated = new Counter('orders_created_total');

export const options = {
  // ---------------------------------------------------------------------------
  // SCENARIOS : la fonctionnalite la plus puissante de k6.
  // Un scenario = un profil de charge + une fonction a executer. Plusieurs
  // scenarios peuvent tourner EN PARALLELE, avec des profils differents.
  // ---------------------------------------------------------------------------
  scenarios: {
    // ARRIVEE A DEBIT CONSTANT. C'EST LE BON EXECUTEUR DANS 90% DES CAS.
    //
    // Difference fondamentale avec `ramping-vus`, et LA question a poser en
    // entretien : avec des VUs, si le systeme ralentit, les VUs attendent et
    // le DEBIT BAISSE tout seul. Le test s'auto-regule et masque la degradation.
    // Avec `constant-arrival-rate`, k6 maintient le debit COUTE QUE COUTE et
    // ajoute des VUs si necessaire : c'est le comportement de vrais clients,
    // qui ne ralentissent pas parce que votre serveur souffre.
    // C'est ce qu'on appelle eviter le "coordinated omission".
    charge_nominale: {
      executor: 'constant-arrival-rate',
      rate: 30,                    // 30 iterations...
      timeUnit: '1s',              // ...par seconde
      duration: '3m',
      preAllocatedVUs: 50,         // VUs alloues au demarrage (cout memoire)
      maxVUs: 200,                 // plafond si le systeme ralentit
      // Si k6 doit depasser maxVUs, il affiche un avertissement
      // "insufficient VUs" : c'est deja un resultat de test en soi, cela
      // signifie que le systeme ne tient pas le debit demande.
      exec: 'parcoursAchat',
      tags: { scenario_type: 'nominal' },
    },

    // Trafic de lecture en parallele : des utilisateurs qui consultent la liste
    // sans rien acheter. Un systeme reel n'a jamais un seul type d'usage, et
    // les lectures peuvent saturer les memes ressources que les ecritures.
    consultation: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1s',
      duration: '3m',
      preAllocatedVUs: 20,
      maxVUs: 60,
      exec: 'parcoursConsultation',
      tags: { scenario_type: 'lecture' },
    },
  },

  thresholds: Object.assign({}, DEFAULT_THRESHOLDS, {
    // Seuil sur une metrique PERSONNALISEE : le tunnel complet doit rester
    // sous 2 s au p95. C'est ce chiffre-la qui interesse le metier.
    'checkout_duration': ['p(95)<2000'],
    // Seuil PAR SCENARIO grace aux tags : on peut exiger plus de la lecture.
    'http_req_duration{scenario_type:lecture}': ['p(95)<200'],
  }),

  tags: { testid: __ENV.TEST_RUN || 'load', test_type: 'load' },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 404, 409));

/** Parcours complet : creer puis payer. */
export function parcoursAchat() {
  const start = Date.now();
  // __VU (numero du VU) et __ITER (numero d'iteration) sont des variables
  // globales k6. Combinees, elles donnent un identifiant unique par requete.
  const customerId = `cust-${__VU}-${__ITER}`;

  const createRes = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify(buildOrderPayload(customerId)),
    { headers: JSON_HEADERS, tags: { name: 'POST /api/orders' } }
  );

  if (!check(createRes, { 'commande creee': (r) => r.status === 201 })) {
    return;
  }
  ordersCreated.add(1);
  const orderId = createRes.json('id');

  // TEMPS DE REFLEXION. Un humain ne paie pas 3 ms apres avoir valide son
  // panier. Sans sleep, vous testez un robot, pas des utilisateurs, et vous
  // sous-estimez enormement le nombre de connexions simultanees reelles.
  sleep(Math.random() * 2 + 0.5);   // entre 0.5 et 2.5 s

  const payRes = http.post(`${BASE_URL}/api/orders/${orderId}/pay`, null, {
    headers: JSON_HEADERS,
    tags: { name: 'POST /api/orders/{id}/pay' },
  });

  check(payRes, { 'paiement traite': (r) => r.status === 200 });
  if (payRes.status === 200) {
    // On mesure le TAUX DE REFUS comme une metrique metier a part entiere.
    paymentDeclined.add(payRes.json('status') === 'PAYMENT_FAILED');
  }

  checkoutDuration.add(Date.now() - start);
}

/** Parcours de consultation : uniquement des lectures. */
export function parcoursConsultation() {
  group('consultation', function () {
    const res = http.get(`${BASE_URL}/api/orders?limit=20`, {
      tags: { name: 'GET /api/orders' },
    });
    check(res, {
      'liste ok': (r) => r.status === 200,
      'limite respectee': (r) => r.json().length <= 20,
    });
  });
  sleep(Math.random() * 3);
}
