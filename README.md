# Grafana Lab — devenir Solutions Engineer

Un lab complet et commenté pour maîtriser la stack Grafana de bout en bout :
**minikube + Alloy + Mimir + Loki + Grafana + k6**, avec une API Java
instrumentée en architecture hexagonale.

L'objectif n'est pas de « faire tourner » la stack, mais de comprendre
**pourquoi** chaque brique existe et **quoi répondre** quand un client pose la
question. Tous les fichiers sont abondamment commentés : le code *est* le cours.

---

## Ce que vous saurez faire à la fin

| Domaine | Compétence acquise |
|---|---|
| **Métriques** | Écrire du PromQL correct, comprendre counter/gauge/histogram, diagnostiquer une explosion de cardinalité |
| **Logs** | Écrire du LogQL, structurer des logs JSON, générer des métriques depuis des logs |
| **Collecte** | Configurer Alloy : discovery, relabeling, pipelines de traitement, remote_write |
| **Stockage** | Expliquer l'architecture Mimir et Loki, leurs modes de déploiement, leurs limites |
| **Alerting** | Construire un SLO, calculer un budget d'erreur, écrire une alerte multi-burn-rate |
| **Tests de charge** | Concevoir smoke / load / stress / soak avec k6, poser des seuils qui cassent la CI |
| **Java** | Instrumenter proprement une app, garder le métier découplé du framework |
| **Discours SE** | Argumenter les compromis coût / cardinalité / rétention face à un client |

---

## Architecture

```
                        ┌──────────────────────────────────────┐
                        │            GRAFANA :3000             │
                        │   dashboards · Explore · alerting    │
                        └───────┬──────────────────┬───────────┘
                       PromQL   │                  │  LogQL
                        ┌───────▼──────┐   ┌───────▼───────┐
                        │    MIMIR     │   │     LOKI      │
                        │  métriques   │   │     logs      │
                        └───────▲──────┘   └───────▲───────┘
                  remote_write  │                  │ push
                        ┌───────┴──────────────────┴───────────┐
                        │        ALLOY (DaemonSet)             │
                        │  discovery · scrape · relabel ·      │
                        │  parsing · OTLP · WAL                │
                        └───────▲──────────────────▲───────────┘
                   scrape /metrics                 │ lecture des logs
                        ┌───────┴──────────────────┴───────────┐
                        │        ORDERS-API (2 pods Java)      │
                        │  controller → service → repository   │
                        └───────▲──────────────────────────────┘
                                │ HTTP
                        ┌───────┴──────────┐
                        │        k6        │  ← smoke / load / stress /
                        │  (injecteur)     │     soak / simulation
                        └──────────────────┘
```

**Le point le plus important de ce schéma** : Alloy est le seul agent. Il
remplace à lui seul Prometheus (mode agent), Promtail et l'OTel Collector.
C'est l'argument central de Grafana face à une stack hétérogène.

---

## Démarrage rapide

> **PowerShell ADMINISTRATEUR obligatoire** pour les scripts 01 et 02 : ce lab
> tourne sur le driver **Hyper-V**, sans Docker Desktop, et Hyper-V refuse de
> piloter des machines virtuelles depuis une session non élevée. Activation
> d'Hyper-V et compromis détaillés dans [docs/00-prerequis.md](docs/00-prerequis.md).

```powershell
# 0. Prérequis : JDK 21, kubectl, minikube et k6 sont installés.
#    Hyper-V doit être activé (admin + redémarrage) : voir docs/00-prerequis.md

# 1. Créer le cluster (~5-10 min la première fois)   [ADMIN]
.\scripts\01-start-minikube.ps1

# 2. Construire l'image DANS la VM, sans Docker sur l'hôte (~4 min)   [ADMIN]
.\scripts\02-build-api.ps1

# 3. Déployer toute la stack (~3 min)
.\scripts\03-deploy.ps1

# 4. Ouvrir les accès — LAISSER CETTE FENÊTRE OUVERTE
.\scripts\04-acces.ps1

# 5. Dans une AUTRE fenêtre : générer du trafic
.\scripts\05-k6.ps1 simulation
```

Puis ouvrez **http://localhost:3000** (admin / admin) →
dossier *Orders Lab* → dashboard **Orders API - RED + Business**.

---

## Structure du dépôt

```
api/                    API Java (Spring Boot 3, Java 21)
  mvnw / mvnw.cmd       Maven wrapper : ./mvnw test marche sans Maven installé
  src/main/java/com/grafanalab/orders/
    domain/             modèle métier, Java pur, zéro dépendance
    service/            cas d'usage + PORTS (interfaces) — Java pur
    infrastructure/     ADAPTATEURS : persistance, paiement, métriques, chaos
    web/                controllers, DTO, traduction exception → code HTTP
    config/             le SEUL endroit qui connaît Spring
  src/test/             tests unitaires sans Spring ni Mockito

k8s/                    manifestes Kubernetes, un dossier par composant
grafana/dashboards/     dashboards JSON (provisionnés automatiquement)
exercices/              7 environnements d'entraînement, du plus simple au métier
k6/                     5 tests de charge, du smoke à la simulation réaliste
scripts/                pilotage PowerShell
docs/                   le cours : parcours, PromQL, LogQL, SLO, entretien
```

---

## Les exercices

Le lab ci-dessus est l'**environnement de référence** : complet et sain.
Le dossier [exercices/](exercices/) contient sept environnements qui le dégradent,
le fragmentent ou le mettent en situation pour créer un problème à résoudre —
chacun avec son énoncé et ses critères de réussite vérifiables.

| # | Exercice | Durée |
|---|---|---|
| 01 | [Instrumenter de zéro](exercices/01-instrumenter-de-zero/ENONCE.md) | 3–4 h |
| 02 | [Incident de cardinalité](exercices/02-incident-de-cardinalite/ENONCE.md) | 2–3 h |
| 03 | [Dev et prod](exercices/03-dev-et-prod/ENONCE.md) | 1–2 j |
| 04 | [Migration Prometheus](exercices/04-migration-prometheus/ENONCE.md) | ½–1 j |
| 05 | [Legacy sans métriques](exercices/05-legacy-sans-metriques/ENONCE.md) | 3–4 h |
| 06 | [Réduction de coût](exercices/06-reduction-de-cout/ENONCE.md) | 4–6 h |
| 07 | [Discovery et démo](exercices/07-discovery-et-demo/ENONCE.md) | 1 j ×3 |

→ [exercices/README.md](exercices/README.md) pour l'ordre conseillé.

---

## Parcours conseillé

Ne déployez pas tout d'un coup pour ensuite regarder des graphes sans les
comprendre. Suivez **[docs/01-parcours-apprentissage.md](docs/01-parcours-apprentissage.md)** :
7 étapes progressives, de « je scrape une métrique » à « je démontre un SLO
qui brûle son budget d'erreur en direct ».

| Document | Contenu |
|---|---|
| [00-prerequis.md](docs/00-prerequis.md) | Installation des outils sur Windows |
| [01-parcours-apprentissage.md](docs/01-parcours-apprentissage.md) | Le programme en 7 étapes |
| [02-promql.md](docs/02-promql.md) | PromQL : les 20 requêtes à connaître, et les pièges |
| [03-logql.md](docs/03-logql.md) | LogQL : filtrage, parsing, métriques depuis les logs |
| [04-alerting-slo.md](docs/04-alerting-slo.md) | SLI / SLO / budget d'erreur / multi-burn-rate |
| [05-k6.md](docs/05-k6.md) | Les 5 types de tests et comment les interpréter |
| [06-entretien-se.md](docs/06-entretien-se.md) | Questions d'entretien et réponses argumentées |
| [07-troubleshooting.md](docs/07-troubleshooting.md) | « Je n'ai pas de données » : la méthode de diagnostic |

---

## Extensions naturelles (dans l'ordre de valeur)

1. **Tempo** (traces) — la config est déjà prête : Alloy reçoit l'OTLP, les
   `derivedFields` de Loki et les exemplars de Mimir pointent déjà vers `tempo`.
   Il ne manque que le StatefulSet. C'est l'étape qui complète les trois piliers.
2. **Pyroscope** (profiling continu) — le quatrième pilier, racheté par Grafana.
   Répond à « ma requête est lente », pas seulement « quelle requête est lente ».
3. **Grafana Alloy en mode cluster** + Mimir en microservices — pour parler
   passage à l'échelle.
4. **Grafana Cloud** — refaire le même lab en poussant vers Grafana Cloud pour
   comparer self-hosted et SaaS. C'est exactement la conversation que vous
   aurez avec un client.
