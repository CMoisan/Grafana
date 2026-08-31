# 02 — PromQL : l'essentiel et les pièges

PromQL est le langage de Prometheus, de Mimir, et de Grafana Cloud Metrics.
C'est **la** compétence technique la plus discriminante pour un Solutions
Engineer chez Grafana.

---

## Les 4 types de données

| Type | Ce que c'est | Exemple |
|---|---|---|
| **Instant vector** | une valeur par série, à un instant | `up` |
| **Range vector** | une *série de valeurs* sur une fenêtre | `up[5m]` |
| **Scalar** | un nombre simple | `42` |
| **String** | rarement utilisé | `"abc"` |

**Règle fondamentale** : la plupart des fonctions n'acceptent qu'un seul type.
`rate()` exige un *range vector* (`[5m]`), `sum()` exige un *instant vector*.
D'où `sum(rate(x[5m]))` et jamais `rate(sum(x)[5m])`.

---

## Les 3 types de métriques

### Counter — ne fait que monter

```promql
# FAUX : la valeur brute ne veut rien dire
http_server_requests_seconds_count

# JUSTE : la dérivée par seconde
rate(http_server_requests_seconds_count[5m])

# Le nombre d'événements sur une période (pour un panneau "total du jour")
increase(http_server_requests_seconds_count[1h])
```

`rate()` gère automatiquement les **remises à zéro** (redémarrage de pod). C'est
pour cela qu'on ne fait jamais la différence à la main.

### Gauge — monte et descend

```promql
jvm_memory_used_bytes{area="heap"}        # la valeur telle quelle
orders_in_status{status="PENDING"}
deriv(jvm_memory_used_bytes[1h])          # tendance : positive = fuite ?
```

### Histogram — une distribution

```promql
# Les buckets sont CUMULATIFS : le=0.3 compte aussi tout ce qui est sous 0.1
http_server_requests_seconds_bucket{le="0.3"}

# Le quantile
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
```

---

## Les 20 requêtes du lab

```promql
# --- TRAFIC ---------------------------------------------------------------
# 1. Requêtes par seconde, tous pods confondus
sum(rate(http_server_requests_seconds_count{namespace="apps"}[5m]))

# 2. Par endpoint
sum by (uri) (rate(http_server_requests_seconds_count{namespace="apps"}[5m]))

# 3. Par pod : révèle un déséquilibre de load balancing
sum by (pod) (rate(http_server_requests_seconds_count{namespace="apps"}[5m]))

# --- ERREURS --------------------------------------------------------------
# 4. Taux d'erreur 5xx. `or vector(0)` évite "No data" quand il n'y a aucun trafic.
(
  sum(rate(http_server_requests_seconds_count{namespace="apps", status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count{namespace="apps"}[5m]))
) or vector(0)

# 5. Répartition par code
sum by (status) (rate(http_server_requests_seconds_count{namespace="apps"}[5m]))

# 6. Les endpoints qui échouent le plus
topk(5, sum by (uri) (rate(http_server_requests_seconds_count{status=~"5.."}[5m])))

# --- LATENCE --------------------------------------------------------------
# 7. p95 global
histogram_quantile(0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket{namespace="apps"}[5m])))

# 8. p95 par endpoint : noter le `le` DANS le by()
histogram_quantile(0.95,
  sum by (le, uri) (rate(http_server_requests_seconds_bucket{namespace="apps"}[5m])))

# 9. Latence moyenne (_sum / _count). Utile mais trompeuse : à ne jamais
#    présenter seule, toujours accompagnée d'un quantile.
sum(rate(http_server_requests_seconds_sum[5m]))
/ sum(rate(http_server_requests_seconds_count[5m]))

# 10. Part des requêtes sous 300 ms — pas d'interpolation, chiffre exact
sum(rate(http_server_requests_seconds_bucket{le="0.3"}[5m]))
/ sum(rate(http_server_requests_seconds_count[5m]))

# --- MÉTIER ---------------------------------------------------------------
# 11. Chiffre d'affaires de l'heure
sum(increase(orders_revenue_euros_total[1h]))

# 12. Taux de refus de paiement
sum(rate(orders_payments_declined_total[5m]))
/ sum(rate(orders_created_total[5m]))

# 13. Motifs de refus
sum by (reason) (rate(orders_payments_declined_total[5m]))

# 14. Commandes en attente : une pile qui grossit = blocage en aval
sum(orders_in_status{status="PENDING"})

# --- RESSOURCES -----------------------------------------------------------
# 15. CPU par pod, en cœurs
sum by (pod) (rate(container_cpu_usage_seconds_total{namespace="apps"}[5m]))

# 16. CPU THROTTLING — la métrique la plus sous-utilisée de Kubernetes.
#     Si elle est > 0, votre pod est ralenti par sa limite CPU. C'est la
#     première chose à regarder devant une "latence inexpliquée".
sum by (pod) (rate(container_cpu_cfs_throttled_seconds_total{namespace="apps"}[5m]))

# 17. Mémoire réellement utilisée (working set, pas RSS)
sum by (pod) (container_memory_working_set_bytes{namespace="apps"})

# 18. Heap JVM en pourcentage du maximum
sum by (pod) (jvm_memory_used_bytes{area="heap"})
/ sum by (pod) (jvm_memory_max_bytes{area="heap"})

# 19. Temps passé en GC par seconde. Au-delà de 0.1 (10 %), c'est un problème.
sum by (pod) (rate(jvm_gc_pause_seconds_sum[5m]))

# 20. Redémarrages de pods sur 1 h : révèle les OOMKill et les crash loops
increase(kube_pod_container_status_restarts_total{namespace="apps"}[1h])
```

---

## Les 6 pièges classiques

### 1. Moyenner un quantile

```promql
# FAUX — mathématiquement absurde
avg(histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])))

# JUSTE — on agrège les buckets (additifs), PUIS on calcule le quantile
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))
```

La moyenne de « le p95 du pod A » et « le p95 du pod B » n'est le p95 de rien du
tout. **Question d'entretien quasi systématique.**

### 2. Fenêtre de `rate()` trop courte

Avec un scrape toutes les 15 s, `rate(x[15s])` n'a qu'un seul point : le
résultat est vide. **Règle : la fenêtre doit valoir au moins 4 × l'intervalle
de scrape.** Utilisez `$__rate_interval` dans Grafana, qui calcule cela tout
seul en fonction du zoom.

### 3. Confondre `rate` et `irate`

- `rate()` : moyenne sur toute la fenêtre → **lisse**, pour les alertes et les
  dashboards.
- `irate()` : uniquement les 2 derniers points → **nerveux**, pour du debug fin.

Ne jamais mettre `irate()` dans une alerte : le moindre soubresaut la déclenche.

### 4. Oublier que les buckets sont cumulatifs

`le="0.3"` compte **toutes** les requêtes sous 300 ms, y compris celles sous
100 ms. Pour obtenir la tranche 100–300 ms, il faut soustraire :

```promql
rate(...bucket{le="0.3"}[5m]) - rate(...bucket{le="0.1"}[5m])
```

### 5. `sum()` sans `by()` en présence de labels utiles

`sum(rate(...))` écrase tous les labels. Vous obtenez un seul chiffre et perdez
la capacité à identifier *quel* endpoint pose problème. Réflexe : demandez-vous
toujours quelle dimension vous voulez conserver.

### 6. Écrire des sélecteurs sans nom de métrique

```promql
{namespace="apps"}      # charge TOUTES les métriques du namespace : très lent
```

Toujours ancrer sur un nom de métrique. Dans Mimir, une requête sans nom force
le store-gateway à parcourir tous les blocs.

---

## Optimiser une requête lente

Ordre d'intervention, du plus efficace au moins efficace :

1. **Recording rules** — pré-calculer côté Mimir. Gain typique : ×50 à ×200.
   C'est le premier conseil à donner à un client dont « Grafana rame ».
2. **Réduire la cardinalité** — moins de séries, moins de travail.
3. **Filtrer tôt** — mettre les sélecteurs de labels dans la métrique, pas dans
   un `and` en fin de requête.
4. **`$__rate_interval`** au lieu d'un `[5m]` figé — évite de sur-échantillonner
   quand l'utilisateur dézoome sur 30 jours.
5. **Query sharding et cache** côté Mimir (query-frontend) — configuration
   plateforme, pas requête.
