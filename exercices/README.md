# Exercices — parcours de formation Solutions Engineer

Douze environnements, du déploiement manuel jusqu'à la conversation commerciale.
Chacun a un **énoncé**, des **critères de réussite vérifiables**, et une réponse à
la question *« qu'est-ce que ça prouve en entretien ? »*.

Le lab principal (à la racine du dépôt) est l'**environnement de référence** :
complet et fonctionnel. Les exercices le dégradent, le fragmentent, l'étendent ou
le mettent en situation pour créer un problème à résoudre.

---

## ⚠️ Commencez par le 00

Les scripts `01` à `03` déploient tout en trois commandes. C'est pratique une
fois, et **catastrophique pour apprendre** : au bout de vingt minutes vous avez
une plateforme qui tourne et vous ne sauriez pas la refaire.

Un Solutions Engineer déploie devant un client, sur le cluster du client, sans
ses scripts. L'[exercice 00](00-deployer-a-la-main/ENONCE.md) est là pour ça —
il **remplace** les scripts, il ne les complète pas.

---

## Le parcours

### Fondations
| # | Exercice | Durée | Ce que ça travaille |
|---|---|---|---|
| [00](00-deployer-a-la-main/ENONCE.md) | ⭐⭐ **Déployer à la main** | 3–5 h | Autonomie réelle, ordre de déploiement, vérification |
| [01](01-instrumenter-de-zero/ENONCE.md) | Instrumenter de zéro | 3–4 h | Micrometer, découverte Alloy, dashboard RED |
| [02](02-incident-de-cardinalite/ENONCE.md) | ⭐ Incident de cardinalité | 2–3 h | Diagnostic sous pression, garde-fous, post-mortem |

### Architecture client
| # | Exercice | Durée | Ce que ça travaille |
|---|---|---|---|
| [03](03-dev-et-prod/ENONCE.md) | ⭐⭐ Dev et prod | 1–2 j | Multi-tenant, SLO différenciés, promotion |
| [04](04-migration-prometheus/ENONCE.md) | Migration Prometheus | ½–1 j | Conduite du changement, réversibilité |
| [05](05-legacy-sans-metriques/ENONCE.md) | Legacy sans métriques | 3–4 h | Métriques dérivées des logs, limites assumées |

### Les quatre piliers
| # | Exercice | Durée | Ce que ça travaille |
|---|---|---|---|
| [08](08-tempo-les-trois-piliers/ENONCE.md) | ⭐⭐ Tempo, les traces | 4–6 h | Corrélation logs↔traces, exemplars |
| [09](09-pyroscope-le-quatrieme-pilier/ENONCE.md) | ⭐ Pyroscope | 3–4 h | Flame graph, surcoût mesuré |

### Le métier
| # | Exercice | Durée | Ce que ça travaille |
|---|---|---|---|
| [06](06-reduction-de-cout/ENONCE.md) | ⭐⭐ Réduction de coût | 4–6 h | Mesurer, réduire, chiffrer pour un décideur |
| [10](10-passage-a-l-echelle/ENONCE.md) | ⭐⭐ Passage à l'échelle | 1–2 j | Savoir dire non, seuil chiffré |
| [11](11-grafana-cloud-vs-self-hosted/ENONCE.md) | ⭐⭐⭐ Cloud vs self-hosted | 4–6 h | L'arbitrage, la conversation qui décide |
| [07](07-discovery-et-demo/ENONCE.md) | ⭐⭐⭐ Discovery et démo | 1 j ×3 | Qualifier, cadrer, démontrer |

**Total : 8 à 12 jours de travail effectif.**

---

## Dans quel ordre

**Le parcours complet, dans l'ordre pédagogique :**
`00 → 01 → 02 → 03 → 04 → 05 → 08 → 09 → 06 → 10 → 11 → 07`

**Si vous n'avez qu'une semaine** : `00 → 02 → 03 → 08 → 06 → 07`.
Le 00 vous rend autonome, le 08 donne la démo la plus marquante, le 06 et le 07
donnent le langage du métier.

**Si vous n'avez qu'un week-end** : `00`, puis `02`.
Déployer soi-même puis réparer une panne qu'on a diagnostiquée : ce sont les deux
seules histoires dont vous aurez vraiment besoin pour un premier entretien.

---

## La règle qui vaut pour les douze

> **Écrivez la question avant de regarder la donnée.**

C'est la différence entre un ingénieur qui construit un dashboard de 40 panneaux
que personne ne regarde, et un SE qui construit les 6 panneaux répondant à la
question que le client s'est posée.

Et son corollaire, pour l'exercice 00 : **documentation officielle d'abord,
manifeste commenté ensuite, script en dernier recours.**

---

## Un mot sur les critères de réussite

Ils sont volontairement **vérifiables**, pas déclaratifs. « Comprendre la
cardinalité » n'est pas un critère ; « donner le nombre de séries avant et après,
avec le facteur de réduction » en est un.

C'est la même exigence qu'un PoC client : sans critère écrit à l'avance, un PoC
ne se termine jamais, ou il échoue à la fin sur un critère inventé par quelqu'un
qui n'était pas dans la boucle.

---

## État d'avancement

- [x] Les 12 énoncés rédigés
- [ ] 00 — *aucun environnement à construire : le lab de référence suffit*
- [ ] 01 — environnement à construire (API dépouillée)
- [ ] 02 — environnement à construire (API à cardinalité explosive)
- [ ] 03 — environnement à construire (deux namespaces, deux tenants)
- [ ] 04 — environnement à construire (Prometheus existant)
- [ ] 05 — environnement à construire (app à logs texte)
- [ ] 06 — environnement à construire (stack volontairement mal réglée)
- [ ] 07 — *le brief suffit, l'environnement est le lab de référence*
- [ ] 08 — manifestes Tempo à écrire **par vous**, c'est l'exercice
- [ ] 09 — manifestes Pyroscope à écrire **par vous**, c'est l'exercice
- [ ] 10 — manifestes distribués à écrire **par vous**, c'est l'exercice
- [ ] 11 — *aucun environnement : un compte Grafana Cloud gratuit*

> Les exercices 00, 07, 08, 09, 10 et 11 sont **faisables immédiatement** :
> ils demandent d'écrire ou de raisonner, pas de disposer d'un environnement
> préparé. Les autres attendent que leur variante du lab soit construite.
