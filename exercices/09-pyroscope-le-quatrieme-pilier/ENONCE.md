# Exercice 09 — Pyroscope — le quatrième pilier

> **Durée estimée** : 3 à 4 h · **Niveau** : ⭐ différenciateur

## 🎬 Le contexte

La trace vous dit que le service paiement a pris 800 ms. Elle ne vous dit pas **pourquoi**. Le profiling continu répond à la question suivante : quelle ligne de code a consommé ce temps.

Peu de concurrents l'ont intégré au même niveau que Grafana depuis le rachat de Pyroscope. C'est donc un angle de démo qui surprend.

## 🎯 L'objectif

Profiler l'API Java en continu, savoir lire un flame graph à voix haute, et relier un span lent à son profil.

## 📦 Ce qui est fourni

- L'API Java du lab, avec son chaos panel pour créer de la charge CPU
- La stack LGTM en place
- **Aucun manifeste Pyroscope** — à écrire

## ✅ Les tâches

1. Déployer Pyroscope et sa datasource Grafana
2. Instrumenter l'API Java : agent Pyroscope, ou eBPF selon ce que vous choisissez — **et savoir justifier le choix**
3. Générer de la charge avec k6 et observer les profils arriver
4. Lire un flame graph : identifier les trois fonctions les plus coûteuses en CPU
5. Provoquer une dégradation (latence du paiement) et vérifier ce que le profil montre — ou ne montre pas
6. Mesurer le surcoût : comparer la latence p95 avec et sans profiling activé
7. Si l'exercice 08 est fait : activer la navigation trace → profil

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Les profils arrivent en continu et sont consultables dans Grafana
- [ ] Vous lisez un flame graph à voix haute, sans hésiter, en expliquant l'axe horizontal
- [ ] Vous avez un **chiffre** sur le surcoût du profiling, mesuré et non supposé
- [ ] Vous savez dire ce que le profiling ne montre PAS (attente réseau, verrous en base)
- [ ] Vous savez à quel moment d'une conversation client sortir cet argument

## ⚠️ Le piège de cet exercice

Présenter le profiling comme « gratuit ». Il y a toujours un surcoût, faible mais réel, et un SRE le sait. Annoncer le chiffre vous-même vaut infiniment mieux que de le laisser vous être opposé. C'est la même discipline que dans l'exercice 05 : énoncer soi-même les limites de ce qu'on vend.

## 🎤 Ce que ça prouve en entretien

Que vous couvrez les quatre piliers, pas trois. Et que vous savez situer un produit récent dans une conversation — sans le sortir hors de propos, ce qui est l'erreur inverse.

## 📚 Notes du vault liées

[[Profiling continu]] · [[Tempo - traces]] · [[Stack Grafana - catalogue produits]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.