// =============================================================================
// CONFIGURATION PARTAGEE DES TESTS k6
// =============================================================================
// k6 est ecrit en Go mais les scripts sont en JavaScript (moteur goja, ES6).
// IMPORTANT : ce n'est PAS Node.js. Pas de require(), pas de npm, pas de
// setTimeout, pas d'acces au systeme de fichiers a l'execution. Uniquement des
// imports ES6 et les modules k6 natifs (k6/http, k6/metrics...).
// C'est deroutant au debut : retenez que chaque VU (utilisateur virtuel) est
// une goroutine Go executant une VM JavaScript isolee.
// =============================================================================

// __ENV expose les variables d'environnement, seul moyen de parametrer un test
// depuis l'exterieur (CI, Makefile, k8s). Notez le fallback : un script k6 doit
// TOUJOURS pouvoir tourner sans configuration.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Catalogue de produits pour generer des paniers realistes.
export const CATALOG = [
  { sku: 'SKU-LAPTOP',   price: 1299.00, weight: 5  },
  { sku: 'SKU-MOUSE',    price: 29.90,   weight: 30 },
  { sku: 'SKU-KEYBOARD', price: 89.00,   weight: 20 },
  { sku: 'SKU-SCREEN',   price: 349.00,  weight: 10 },
  { sku: 'SKU-CABLE',    price: 12.50,   weight: 35 },
];

/**
 * Tirage pondere : les cables se vendent bien plus que les portables.
 * Une charge uniforme ne ressemble a AUCUN systeme reel. Un test qui ne
 * reproduit pas la distribution reelle ne prouve rien : c'est la premiere
 * critique a faire a un benchmark maison.
 */
export function pickProduct() {
  const totalWeight = CATALOG.reduce((sum, p) => sum + p.weight, 0);
  let r = Math.random() * totalWeight;
  for (const product of CATALOG) {
    r -= product.weight;
    if (r <= 0) return product;
  }
  return CATALOG[0];
}

/** Construit un panier de 1 a 4 lignes. */
export function buildOrderPayload(customerId) {
  const lineCount = 1 + Math.floor(Math.random() * 4);
  const lines = [];
  for (let i = 0; i < lineCount; i++) {
    const product = pickProduct();
    lines.push({
      sku: product.sku,
      quantity: 1 + Math.floor(Math.random() * 3),
      unitPrice: product.price,
    });
  }
  return { customerId: customerId, lines: lines };
}

// En-tetes communs. Le header `X-Test-Run` permet de retrouver dans Loki les
// requetes issues d'un test precis : tres pratique pour comparer deux runs.
export const JSON_HEADERS = {
  'Content-Type': 'application/json',
  'X-Test-Run': __ENV.TEST_RUN || 'local',
};

/**
 * =============================================================================
 * SEUILS (thresholds) : la difference entre "un graphe" et "un test qui echoue"
 * =============================================================================
 * Un threshold non respecte fait sortir k6 avec le code 99. En CI, cela FAIT
 * ECHOUER LE PIPELINE. C'est ce qui transforme un test de charge en garde-fou
 * automatique de non-regression de performance.
 *
 * Sans thresholds, un test de charge n'est qu'une jolie image que personne ne
 * regarde. C'est le conseil le plus utile a donner a un client qui debute.
 */
export const DEFAULT_THRESHOLDS = {
  // p(95) < 500ms ET p(99) < 1500ms.
  // On raisonne TOUJOURS en quantiles, jamais en moyenne : la moyenne masque
  // exactement les utilisateurs qu'on veut proteger.
  http_req_duration: ['p(95)<500', 'p(99)<1500'],

  // Moins de 1% de requetes en echec.
  http_req_failed: ['rate<0.01'],

  // Tous les checks fonctionnels doivent passer a plus de 99%.
  checks: ['rate>0.99'],
};
