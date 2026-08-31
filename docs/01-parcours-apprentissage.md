# 01 — Parcours d'apprentissage en 7 étapes

Chaque étape a un **objectif**, une **manipulation** et une **question à
laquelle vous devez savoir répondre** avant de passer à la suivante. Ne sautez
pas les questions : c'est là qu'est l'apprentissage réel.

Comptez 2 à 3 jours si vous êtes déjà à l'aise avec Kubernetes, une semaine
sinon.

---

## Étape 1 — L'application seule, sans plateforme

**Objectif** : comprendre ce qu'une application *produit* avant de parler de
collecte.

```powershell
# Lancez l'API sans Kubernetes.
# On utilise le WRAPPER (mvnw) : aucun Maven a installer.
cd api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# Dans un autre terminal
curl http://localhost:8080/actuator/prometheus
```

Lisez cette sortie en entier. C'est du **texte au format OpenMetrics** : nom de
métrique, labels entre accolades, valeur. Rien de magique.

Créez une commande, puis re-regardez :

```powershell
curl -X POST http://localhost:8080/api/orders `
  -H "Content-Type: application/json" `
  -d '{\"customerId\":\"c1\",\"lines\":[{\"sku\":\"A\",\"quantity\":2,\"unitPrice\":10}]}'

curl http://localhost:8080/actuator/prometheus | Select-String "orders_"
```

**Questions à maîtriser**
1. Pourquoi `http_server_requests_seconds_count` est-il un *counter* et non une
   *gauge* ? *(Réponse : il ne fait que croître ; la valeur brute n'a aucun sens,
   seule sa dérivée `rate()` en a une.)*
2. Que se passe-t-il si le pod redémarre ? *(Le counter repart à 0. `rate()`
   détecte cette remise à zéro et la gère correctement — c'est précisément
   pourquoi on n'utilise jamais la valeur brute.)*
3. Trouvez `http_server_requests_seconds_bucket`. À quoi servent les `le="..."` ?

---

## Étape 2 — Mimir : stocker les métriques

```powershell
.\scripts\01-start-minikube.ps1
.\scripts\02-build-api.ps1
.\scripts\03-deploy.ps1
.\scripts\04-acces.ps1
```

Ouvrez **http://localhost:12345** : l'**UI d'Alloy**. Passez-y du temps.

- Onglet *Graph* : le pipeline complet. Chaque nœud est un composant, chaque
  flèche un flux de données.
- Cliquez sur `discovery.relabel.annotated_pods` → vous voyez les cibles
  **avant** et **après** relabeling. C'est le meilleur outil pédagogique de
  toute la stack.
- Cliquez sur `prometheus.remote_write.mimir` → l'état de la file d'envoi.

**Questions à maîtriser**
1. Combien de cibles `prometheus.scrape.pods` a-t-il trouvées ? Pourquoi
   celles-là et pas les autres ?
2. Retirez l'annotation `prometheus.io/scrape` d'un pod et observez la cible
   disparaître. *(C'est ça, la découverte dynamique : aucune configuration à
   modifier quand un service apparaît ou disparaît.)*

---

## Étape 3 — PromQL

Grafana → **Explore** → datasource **Mimir**.

Travaillez **[02-promql.md](02-promql.md)** requête par requête. Ne copiez pas :
tapez-les, changez les intervalles, cassez-les volontairement pour voir les
messages d'erreur.

**Le test de sortie** — vous devez pouvoir expliquer, sans hésiter, pourquoi
ceci est **faux** :

```promql
avg(histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])))
```

et pourquoi ceci est **juste** :

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
```

*(Un quantile ne se moyenne pas. Il faut agréger les buckets — qui sont additifs —
**avant** de calculer le quantile. C'est la question PromQL la plus posée en
entretien.)*

---

## Étape 4 — Loki et la corrélation

Explore → datasource **Loki**.

```logql
{namespace="apps", app="orders-api"}
{namespace="apps", app="orders-api"} | json | level="ERROR"
{namespace="apps", app="orders-api"} | json | event="payment_declined"
```

Puis la manipulation qui compte : **Split view** (bouton en haut à droite).
Métriques à gauche, logs à droite, **même fenêtre de temps**. Provoquez une
erreur :

```powershell
curl -X POST http://localhost:8080/api/chaos `
  -H "Content-Type: application/json" -d '{\"httpErrorRatePercent\":30}'
```

Vous voyez le pic à gauche **et** les logs correspondants à droite, à la seconde
près. C'est le cœur de la proposition de valeur de Grafana : une seule interface,
plusieurs signaux, la même fenêtre de temps.

**Question à maîtriser** : pourquoi `traceId` n'est-il **pas** un label Loki,
alors que `level` en est un ? *(Cardinalité : `level` a 5 valeurs, `traceId` en
a une infinité. Chaque combinaison de labels crée un stream avec ses propres
chunks.)*

---

## Étape 5 — k6 et la boucle complète

```powershell
.\scripts\05-k6.ps1 smoke        # 1 min — ça marche ?
.\scripts\05-k6.ps1 load         # 3 min — ça tient la charge ?
.\scripts\05-k6.ps1 stress       # 10 min — où ça casse ?
```

Pendant le **stress test**, gardez le dashboard ouvert et notez **l'ordre
d'apparition** des symptômes. Vous constaterez systématiquement :

> latence p99 → CPU throttling → pauses GC → **puis seulement** les erreurs

**C'est l'observation la plus importante de tout ce lab.** Elle justifie à elle
seule d'alerter sur la latence plutôt que sur les erreurs : quand les erreurs
arrivent, vos utilisateurs souffrent depuis plusieurs minutes.

---

## Étape 6 — SLO et alerting

Lisez **[04-alerting-slo.md](04-alerting-slo.md)**, puis dans Grafana :
**Alerting → Alert rules**. Les règles définies dans
`k8s/mimir/mimir-rules.yaml` sont évaluées par le **ruler de Mimir**, pas par
Grafana.

Déclenchez une alerte volontairement :

```powershell
curl -X POST http://localhost:8080/api/chaos `
  -H "Content-Type: application/json" -d '{\"httpErrorRatePercent\":20}'
```

Observez le cycle de vie complet : `Normal` → `Pending` (pendant le `for: 2m`)
→ `Firing` → puis `Normal` après réparation.

**Question à maîtriser** : à quoi sert `for: 2m` ? Que se passerait-il sans lui ?
*(Anti-rebond. Sans lui, un pic d'une seconde réveille un astreinte. Une alerte
qui crie pour rien est une alerte qu'on finit par ignorer — c'est ainsi qu'on
rate le vrai incident.)*

---

## Étape 7 — La démonstration complète

C'est l'étape qui vous prépare réellement à l'entretien.

```powershell
.\scripts\05-k6.ps1 simulation
```

15 minutes de trafic réaliste, avec une panne injectée automatiquement à T+6.
Entraînez-vous à **raconter** ce qui se passe à l'écran, à voix haute, comme si
un client vous écoutait. Le déroulé est décrit dans
[06-entretien-se.md](06-entretien-se.md).

Faites-le **trois fois**. La première pour comprendre, la deuxième pour trouver
vos mots, la troisième pour tenir le rythme sans regarder vos notes.

---

## Après le parcours

Ajoutez **Tempo** vous-même. Tout est déjà préparé : Alloy reçoit l'OTLP,
l'application envoie ses traces, les `derivedFields` de Loki et les exemplars
de Mimir pointent vers un UID `tempo`. Il ne manque qu'un StatefulSet et une
datasource.

Le faire seul, à partir de la documentation officielle, est le meilleur moyen
de vérifier que vous avez compris l'ensemble.
