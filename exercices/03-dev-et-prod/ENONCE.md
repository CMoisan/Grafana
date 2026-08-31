# Exercice 03 — Deux environnements : dev et prod

> **Durée estimée** : 1 à 2 jours · **Niveau** : ⭐⭐ le plus proche du réel

## 🎬 Le contexte

Vous arrivez chez un client qui a **un** Grafana, **un** Prometheus, et deux environnements dont personne ne sait les distinguer dans les dashboards. Les alertes de dev réveillent l'astreinte. Un dashboard modifié en dev casse la prod. Et quand une équipe demande « combien nous coûte notre dev ? », personne ne sait répondre.

C'est la situation la plus fréquente en clientèle, et celle qui produit le plus de frustration quotidienne.

## 🎯 L'objectif

Faire cohabiter proprement un environnement de développement et un environnement de production sur **une seule** plateforme d'observabilité : isolation des données, SLO différenciés, alertes qui ne réveillent personne pour rien, et un chemin de promotion des dashboards de dev vers prod.

## 📦 Ce qui est fourni

- La stack LGTM déployée
- L'API de commandes, déployable deux fois
- Un seul Grafana, un seul Mimir, un seul Loki

## ✅ Les tâches

1. Créer les namespaces `apps-dev` et `apps-prod`, avec des ressources différentes (prod dimensionnée, dev bridée)
2. Déployer l'API dans les deux, avec un common tag `environment` distinct
3. **Isoler les données** : activer le multi-tenant Mimir et router dev et prod vers deux tenants (`X-Scope-OrgID`) via Alloy
4. Faire de même pour Loki
5. Appliquer des **limites différentes par tenant** : dev bridé, prod généreux. Vérifier que saturer dev ne casse pas prod
6. Créer deux datasources Grafana, une par tenant, et une variable `$env` sur le dashboard
7. Écrire **un seul** dashboard qui fonctionne dans les deux environnements
8. Définir deux SLO différents : 99 % en prod, aucun SLO en dev
9. Router les alertes : prod → sévérité critique, dev → aucune notification
10. Mettre en place la **promotion** : le dashboard vit dans git, un dossier par environnement, et une modification en dev ne touche pas prod tant qu'on ne la promeut pas
11. Chiffrer le coût de chaque environnement : séries actives et volume de logs par tenant

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Un test de charge k6 qui sature **dev** n'a aucun effet sur les métriques de **prod**
- [ ] Le même fichier JSON de dashboard sert aux deux environnements
- [ ] Une alerte se déclenche en prod et **pas** en dev, pour le même incident provoqué
- [ ] Vous pouvez donner le nombre de séries actives de chaque environnement séparément
- [ ] Modifier le dashboard en dev ne modifie pas celui de prod tant qu'on n'a pas promu
- [ ] Vous savez expliquer en 2 minutes pourquoi tenant ≠ namespace ≠ label

## ⚠️ Le piège de cet exercice

Croire qu'un simple label `environment=dev` suffit. Ça sépare visuellement, mais **pas** les quotas, ni les limites, ni les droits d'accès, ni la facture. Le jour où un test de charge en dev consomme le quota d'ingestion, c'est la prod qui perd des données. La séparation visuelle est cosmétique ; l'isolation par tenant est structurelle. Savoir énoncer cette différence est exactement ce qu'on attend d'un SE en cadrage d'architecture.

## 🎤 Ce que ça prouve en entretien

C'est **la question d'architecture la plus posée en avant-vente** : « comment on sépare nos environnements et nos équipes ? ». Répondre avec les trois niveaux — label, namespace, tenant — et savoir dire lequel résout quoi, vous place immédiatement au-dessus de quelqu'un qui n'a manipulé qu'un environnement unique.

## 📚 Notes du vault liées

[[Mimir - métriques à l'échelle]] · [[SLI, SLO et budget d'erreur]] · [[Kubernetes pour un SE]] · [[Grafana - la couche de visualisation]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.