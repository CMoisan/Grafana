# 03 — LogQL : requêter les logs

LogQL ressemble volontairement à PromQL. Une requête a toujours **deux parties** :

```
{selecteur de flux}  |  pipeline de traitement
```

La première partie est **obligatoire** et détermine la performance. La seconde
est optionnelle et s'exécute par force brute sur les données sélectionnées.

---

## 1. Le sélecteur de flux (stream selector)

```logql
{namespace="apps"}
{namespace="apps", app="orders-api"}
{app=~"orders.*"}
{namespace="apps", container!="istio-proxy"}
```

**C'est ici que se joue toute la performance de Loki.** Le sélecteur choisit
quels *chunks* seront décompressés. Plus il est précis, moins Loki travaille.

`{namespace=~".+"}` sur un cluster entier = plusieurs Go décompressés pour rien.

---

## 2. Le pipeline

### Filtres de ligne — les plus rapides

```logql
{app="orders-api"} |= "error"        # contient
{app="orders-api"} != "healthcheck"  # ne contient pas
{app="orders-api"} |~ "erro?r|fail"  # regex
{app="orders-api"} !~ "debug|trace"  # regex négative
```

**Optimisation clé** : mettez les filtres de ligne **avant** les parsers. Loki
filtre alors sur le texte brut, sans avoir à parser chaque ligne :

```logql
# LENT : parse 1 000 000 de lignes, puis en garde 12
{app="orders-api"} | json | level="ERROR"

# RAPIDE : filtre à 12 lignes sur le texte brut, puis parse ces 12 lignes
{app="orders-api"} |= "ERROR" | json | level="ERROR"
```

Ce seul réflexe peut diviser un temps de requête par 100. C'est le conseil
LogQL le plus rentable à donner à un client.

### Parsers

```logql
| json                    # JSON → labels temporaires (notre cas)
| logfmt                  # format clé=valeur
| pattern "<_> <method> <path> <status>"   # positionnel, très rapide
| regexp "(?P<status>\d{3})"              # le plus souple, le plus lent
```

### Filtres d'étiquettes — après parsing

```logql
{app="orders-api"} | json | level="ERROR"
{app="orders-api"} | json | duration_ms > 500      # comparaison numérique
{app="orders-api"} | json | traceId="4bf92f3577b3"  # LA requête du support
```

### Formatage

```logql
{app="orders-api"} | json | line_format "{{.level}} {{.message}}"
{app="orders-api"} | json | label_format niveau=level
```

---

## 3. Requêtes métriques — transformer des logs en séries temporelles

C'est la fonctionnalité la plus sous-estimée de Loki.

```logql
# Volume de logs par seconde et par application
sum by (app) (rate({namespace="apps"}[5m]))

# Erreurs par minute
sum(count_over_time({app="orders-api"} |= "ERROR" [1m]))

# Taux d'erreur calculé UNIQUEMENT depuis les logs
sum(rate({app="orders-api"} |= "ERROR" [5m]))
/ sum(rate({app="orders-api"}[5m]))

# Extraire une VALEUR NUMÉRIQUE d'un log et en faire une métrique.
# `unwrap` est la fonction à connaître : elle permet de calculer un p95 de
# latence à partir de logs, pour une application qui n'expose AUCUNE métrique.
quantile_over_time(0.95,
  {app="orders-api"} | json | unwrap duration_ms [5m]) by (uri)

# Répartition par motif de refus, depuis les logs
sum by (reason) (count_over_time(
  {app="orders-api"} | json | event="payment_declined" [5m]))
```

**L'argument commercial** derrière `unwrap` : « votre application legacy
n'expose pas de métriques et vous ne pouvez pas la modifier ? Ses logs
suffisent. » C'est un déblocage immédiat pour beaucoup de clients.

---

## Requêtes du lab

```logql
# Tout ce que produit l'API
{namespace="apps", app="orders-api"}

# Uniquement les problèmes
{namespace="apps", app="orders-api"} |= "ERROR"

# Événements métier structurés
{namespace="apps", app="orders-api"} | json | event="payment_declined"
{namespace="apps", app="orders-api"} | json | event="order_created"

# LE moment de la démo : retrouver l'instant exact de l'injection de panne
{namespace="apps", app="orders-api"} | json | event=~"chaos_.*"

# Suivre une requête précise de bout en bout (traceId renvoyé au client
# dans les réponses d'erreur)
{namespace="apps"} | json | traceId="COLLEZ_LE_TRACE_ID_ICI"

# Volume de logs par niveau : sert à repérer un service trop bavard
sum by (level) (rate({namespace="apps"} | json [5m]))

# Surveiller la plateforme elle-même
{namespace="observability", app="alloy"} |= "error"
{namespace="observability", app="mimir"} |= "429"   # rate limiting !
```

---

## Cardinalité : la règle absolue

Chaque **combinaison unique de labels** crée un *stream*, avec ses propres
chunks et son propre index.

| ✅ Bon label | ❌ Mauvais label |
|---|---|
| `namespace` (10) | `traceId` (∞) |
| `app` (50) | `userId` (millions) |
| `level` (5) | `requestId` (∞) |
| `container` (100) | `timestamp` (∞) |
| `cluster` (5) | URL complète (∞) |

**Où mettre les valeurs à forte cardinalité :**

1. Dans le **corps** du log (JSON) → filtrées avec `| json | traceId="..."`.
   Coût : un scan, mais uniquement sur les chunks déjà sélectionnés.
2. En **structured metadata** (Loki 3+) → attachées à la ligne sans créer de
   stream. C'est ce que fait la config Alloy de ce lab pour `traceId`.

Un client qui se plaint que « Loki est lent » a, dans 90 % des cas, un problème
de cardinalité de labels. Vérifiez d'abord :

```logql
# Nombre de streams par label — diagnostic n°1
sum by (app) (count_over_time({namespace="apps"}[5m]))
```

et l'API `/loki/api/v1/index/stats`.

---

## Explore Logs : la nouvelle expérience

Grafana propose désormais **Explore Logs** (activé par le feature toggle
`lokiExploreLogs` dans ce lab) : une navigation **sans écrire de LogQL**, par
clics sur les labels, les motifs détectés et les champs.

C'est un pari produit majeur de Grafana : élargir l'observabilité au-delà des
experts. En démo client, montrez toujours les deux — l'interface guidée pour
l'équipe support, LogQL pour les SRE. Le message : *la plateforme s'adapte au
niveau de chacun.*
