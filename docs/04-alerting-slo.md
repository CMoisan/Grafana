# 04 — SLI, SLO, budget d'erreur et alerting

Le vocabulaire SRE est **attendu** en entretien Solutions Engineer. Il structure
toute la conversation avec un client sur « qu'est-ce qu'on surveille, et
pourquoi ».

---

## Le vocabulaire, sans jargon

| Terme | Définition | Exemple dans ce lab |
|---|---|---|
| **SLI** | *Service Level Indicator* — une mesure | % de requêtes sans erreur 5xx |
| **SLO** | *Objective* — la cible qu'on se fixe | 99 % sur 30 jours glissants |
| **SLA** | *Agreement* — le SLO + des pénalités contractuelles | 99 % ou remboursement |
| **Budget d'erreur** | 100 % − SLO | 1 %, soit ~7 h par mois |

**L'idée qui change tout** : le budget d'erreur n'est pas une honte, c'est une
**ressource à dépenser**. S'il reste du budget, on peut déployer vite et prendre
des risques. S'il est épuisé, on gèle les nouvelles fonctionnalités et on
stabilise. Cela transforme une discussion émotionnelle (« c'est trop instable »)
en décision chiffrée.

### Combien de temps d'indisponibilité par mois ?

| SLO | Budget mensuel | Réalité opérationnelle |
|---|---|---|
| 99 % | 7 h 18 min | confortable |
| 99,9 % | 43 min | astreinte nécessaire |
| 99,95 % | 21 min | redondance multi-AZ |
| 99,99 % | 4 min 22 s | très coûteux, multi-région |
| 99,999 % | 26 s | quasi personne n'y arrive vraiment |

**Argument à retenir face à un client qui exige « cinq neuf »** : chaque neuf
supplémentaire multiplie le coût par 3 à 10. La vraie question n'est pas
« quel SLO voulez-vous ? » mais « combien vaut une minute d'indisponibilité pour
votre métier ? ». Le SLO se déduit de cette réponse, jamais l'inverse.

---

## Écrire un SLI en PromQL

```promql
# SLI de DISPONIBILITÉ : proportion de requêtes non-5xx
1 - (
  sum(rate(http_server_requests_seconds_count{status=~"5.."}[30d]))
  / sum(rate(http_server_requests_seconds_count[30d]))
)

# SLI de LATENCE : proportion de requêtes sous 300 ms
sum(rate(http_server_requests_seconds_bucket{le="0.3"}[30d]))
/ sum(rate(http_server_requests_seconds_count[30d]))
```

**Le point où tout se joue** : `status=~"5.."` et **pas** `status=~"[45].."`.
Un 404 est une faute du client, un 400 une requête malformée, un 409 un conflit
métier — aucun ne doit consommer votre budget d'erreur.

C'est précisément pourquoi
[`GlobalExceptionHandler`](../api/src/main/java/com/grafanalab/orders/web/GlobalExceptionHandler.java)
prend autant de soin à traduire chaque exception en code HTTP. **Le SLO n'est
pas plus fiable que la rigueur de vos codes de statut.**

---

## Le budget d'erreur restant

```promql
# Fraction du budget encore disponible (1 = intact, 0 = épuisé)
1 - (
  (
    sum(increase(http_server_requests_seconds_count{status=~"5.."}[30d]))
    / sum(increase(http_server_requests_seconds_count[30d]))
  ) / 0.01
)
```

À afficher dans une **jauge**, en évidence, en haut du dashboard. C'est le
chiffre que regarde un responsable, pas le p99.

---

## Alerter sur le burn rate (multi-fenêtres)

### Le problème des alertes naïves

- **Alerter sur chaque erreur** → bruit permanent, tout le monde ignore.
- **Alerter sur « SLO dépassé »** → trop tard, le mois est déjà perdu.

### La solution : le taux de consommation

Le **burn rate** est la vitesse à laquelle vous consommez votre budget.

- burn rate = 1 → vous épuiserez exactement votre budget en 30 jours. Normal.
- burn rate = 14,4 → budget épuisé en **2 jours**. Urgent.
- burn rate = 6 → budget épuisé en 5 jours. À traiter dans la journée.

D'où le tableau canonique du *Google SRE Workbook* :

| Sévérité | Burn rate | Fenêtre longue | Fenêtre courte | Budget consommé |
|---|---|---|---|---|
| **Critique** (page) | 14,4 | 1 h | 5 min | 2 % en 1 h |
| **Critique** | 6 | 6 h | 30 min | 5 % en 6 h |
| **Warning** (ticket) | 3 | 24 h | 2 h | 10 % en 24 h |
| **Warning** | 1 | 72 h | 6 h | 10 % en 3 j |

### Pourquoi DEUX fenêtres ?

- La **fenêtre longue** (1 h) évite les faux positifs : un pic isolé de 30 s ne
  suffit pas à la faire basculer.
- La **fenêtre courte** (5 min) garantit que l'alerte **se referme vite** quand
  l'incident est résolu. Sans elle, une alerte basée sur 1 h resterait active
  50 minutes après la réparation — et l'astreinte perdrait confiance dans le
  système.

L'implémentation complète est dans
[`k8s/mimir/mimir-rules.yaml`](../k8s/mimir/mimir-rules.yaml), règle
`OrdersApiSLOBurnRateFast`.

**Savoir expliquer ce mécanisme au tableau vous distingue immédiatement.** C'est
un sujet que beaucoup citent et que peu savent dérouler.

---

## Où évaluer les règles : Mimir ou Grafana ?

Les deux sont possibles, et il faut savoir choisir.

### Ruler Mimir (choix de ce lab)

- ✅ Les règles vivent avec les données ; elles fonctionnent même si Grafana est
  indisponible.
- ✅ Format Prometheus standard → portable, versionnable, testable avec `promtool`.
- ✅ Passage à l'échelle sur des milliers de règles.
- ❌ Une seule datasource par règle.
- ❌ Pas d'interface de rédaction : c'est du YAML.

### Alerting unifié Grafana

- ✅ **Multi-datasources** dans une même règle : « alerte si le taux d'erreur
  Prometheus monte **et** que les logs Loki contiennent X ». Impossible ailleurs.
- ✅ Interface graphique, prévisualisation, silences, routage riche.
- ❌ Dépend de la disponibilité de Grafana.

**Recommandation à donner à un client** : les alertes d'infrastructure critiques
dans le ruler Mimir (elles doivent survivre à la panne de Grafana) ; les alertes
métier et multi-signaux dans Grafana.

---

## Ce qui fait une bonne alerte

Une alerte doit répondre à **trois** questions :

1. **Que se passe-t-il ?** → `summary` clair, avec la valeur mesurée.
2. **Est-ce grave ?** → `severity`, et l'impact utilisateur, pas la cause technique.
3. **Que dois-je faire ?** → `runbook_url` **obligatoire** sur toute alerte
   critique.

### Les anti-patterns à savoir nommer

| Anti-pattern | Pourquoi c'est grave |
|---|---|
| Alerter sur une **cause** (CPU à 80 %) | Un CPU à 80 % sans impact utilisateur est un CPU bien utilisé. Alertez sur le **symptôme** : la latence. |
| Alerte sans `for` | Un pic d'une seconde réveille l'astreinte. |
| Alerte sans runbook | Celui qui la reçoit à 3 h du matin ne sait pas quoi faire. |
| Alerte que personne ne traite | Elle éduque l'équipe à ignorer les alertes. Supprimez-la. |
| Seuil statique sur du trafic variable | 100 req/s est normal à midi, anormal à 4 h. |

**La question à poser en audit chez un client** : *« combien d'alertes reçoit
votre équipe par semaine, et combien donnent lieu à une action ? »* Si le
rapport est inférieur à 1 pour 10, la plateforme d'alerting ne sert plus à rien,
et c'est là que vous apportez de la valeur.
