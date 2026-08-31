// =============================================================================
// TEST 1/5 : SMOKE TEST
// =============================================================================
// OBJECTIF : verifier que le systeme fonctionne, avec une charge MINIMALE.
// Ce n'est PAS un test de performance : c'est un test fonctionnel sous k6.
//
// QUAND : apres chaque deploiement, avant tout autre test. Si le smoke echoue,
// inutile de lancer un test de charge : vous mesureriez un systeme casse.
//
// DUREE : 1 minute. 1 utilisateur virtuel.
// =============================================================================
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL, JSON_HEADERS, buildOrderPayload } from './lib/config.js';

export const options = {
  vus: 1,
  duration: '1m',
  thresholds: {
    // Sur un smoke test on est INTRANSIGEANT : aucune erreur toleree.
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    http_req_duration: ['p(95)<1000'],
  },
  // Ajoute des tags a TOUTES les metriques du run : indispensable pour
  // comparer plusieurs runs dans Grafana une fois les donnees dans Mimir.
  tags: {
    testid: __ENV.TEST_RUN || 'smoke',
    test_type: 'smoke',
  },
};

export default function () {
  let orderId;

  // `group` regroupe les metriques sous un nom : dans le resume final et dans
  // Grafana, vous verrez la latence PAR ETAPE FONCTIONNELLE, pas seulement par
  // URL. C'est ce qui permet de dire "c'est le paiement qui est lent", et pas
  // juste "POST est lent".
  group('creation de commande', function () {
    const payload = buildOrderPayload('smoke-user');
    const res = http.post(`${BASE_URL}/api/orders`, JSON.stringify(payload), {
      headers: JSON_HEADERS,
      // `name` fige le nom de la metrique. SANS CA, chaque URL avec un ID
      // different creerait une metrique differente : c'est le meme probleme
      // de cardinalite que cote Prometheus, mais cote k6.
      tags: { name: 'POST /api/orders' },
    });

    // `check` = assertion NON bloquante. Contrairement a un test unitaire,
    // un check qui echoue n'arrete pas le VU : il incremente le compteur
    // `checks` et le test continue. C'est voulu : on veut la statistique
    // sur des milliers d'iterations, pas un arret au premier echec.
    const ok = check(res, {
      'statut 201': (r) => r.status === 201,
      'a un id': (r) => r.json('id') !== undefined,
      'statut PENDING': (r) => r.json('status') === 'PENDING',
      'total > 0': (r) => r.json('total') > 0,
    });

    if (ok) {
      orderId = res.json('id');
    }
  });

  if (!orderId) {
    // On ne va pas plus loin si la creation a echoue : cela evite un flot
    // d'erreurs en cascade qui masquerait la vraie cause.
    return;
  }

  group('lecture de commande', function () {
    const res = http.get(`${BASE_URL}/api/orders/${orderId}`, {
      // ATTENTION : l'URL contient un ID mais le tag `name` est fige au
      // TEMPLATE. Meme principe que le label `uri` cote Micrometer.
      tags: { name: 'GET /api/orders/{id}' },
    });
    check(res, {
      'statut 200': (r) => r.status === 200,
      'bon id': (r) => r.json('id') === orderId,
    });
  });

  group('paiement', function () {
    const res = http.post(`${BASE_URL}/api/orders/${orderId}/pay`, null, {
      headers: JSON_HEADERS,
      tags: { name: 'POST /api/orders/{id}/pay' },
    });
    check(res, {
      'statut 200': (r) => r.status === 200,
      // Un refus banque est un resultat VALIDE : PAID ou PAYMENT_FAILED.
      // Traiter un refus comme un echec de test rendrait la suite instable.
      'statut final coherent': (r) =>
        ['PAID', 'PAYMENT_FAILED'].indexOf(r.json('status')) !== -1,
    });
  });

  group('404 attendu', function () {
    const res = http.get(`${BASE_URL}/api/orders/inexistante-xyz`, {
      tags: { name: 'GET /api/orders/{id} (404)' },
    });
    // On VERIFIE que l'erreur est bien un 404 et pas un 500 : c'est un test
    // de la qualite de vos codes HTTP, donc de la fiabilite de votre SLO.
    check(res, { 'statut 404': (r) => r.status === 404 });
  });

  sleep(1);
}

// =============================================================================
// PIEGE CLASSIQUE ET SA SOLUTION
// =============================================================================
// Par defaut, k6 considere toute reponse >= 400 comme un ECHEC et l'ajoute a
// la metrique `http_req_failed`. Or notre test verifie VOLONTAIREMENT un 404.
// Avec `http_req_failed: ['rate==0']`, le test echouerait alors que tout va bien.
//
// `setResponseCallback` redefinit ce qui compte comme succes. Ici : les 2xx/3xx
// PLUS le 404 et le 409, qui sont des reponses metier legitimes.
//
// Meme raisonnement que cote Prometheus : distinguer "erreur du client" de
// "panne du service". Si vous ne le faites pas, vos tests deviennent instables
// et l'equipe finit par les ignorer.
// =============================================================================
http.setResponseCallback(
  http.expectedStatuses({ min: 200, max: 399 }, 404, 409)
);
