# Exercice 10 — Passage à l'échelle — Alloy en cluster, Mimir en microservices

> **Durée estimée** : 1 à 2 jours · **Niveau** : ⭐⭐ conversation d'architecte

## 🎬 Le contexte

Tout le lab tourne en mode monolithique : un Alloy par nœud, un Mimir en un seul process. C'est le bon choix pour un lab, et ce serait le bon conseil pour beaucoup de clients.

Mais un client à 200 microservices et plusieurs millions de séries actives vous posera la question, et il ne suffira pas de répondre « ça scale ». Il faudra dire **comment**, **quand**, et surtout **quand il ne faut pas**.

## 🎯 L'objectif

Passer la plateforme en mode distribué, mesurer ce que ça change, et être capable de conseiller un client sur le franchissement — dans les deux sens.

## 📦 Ce qui est fourni

- La stack en mode monolithique, fonctionnelle
- k6 pour générer une charge suffisante à rendre la différence visible

## ✅ Les tâches

1. **Alloy en mode cluster** : plusieurs replicas, activer `--cluster.enabled`, et faire en sorte que les cibles soient **réparties** et non dupliquées
2. Vérifier la répartition : chaque cible doit être scrapée une fois et une seule. C'est le piège du DaemonSet déjà signalé dans la config
3. **Mimir en microservices** : éclater `target: all` en distributor, ingester, querier, query-frontend, store-gateway, compactor
4. Comprendre le ring : qui s'inscrit, qui interroge qui, ce qui casse si un ingester tombe
5. Faire tomber **un** ingester volontairement pendant une charge k6, et observer
6. Passer `replication_factor` à 3 et refaire l'expérience. Comparer
7. Mesurer : consommation mémoire totale, latence de requête, nombre de pods, avant et après
8. Écrire une note d'une page : à partir de quel volume ce basculement se justifie, et ce qu'il coûte en exploitation

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Deux Alloy tournent sans produire de séries en double — vérifié, pas supposé
- [ ] Mimir tourne en composants séparés et les requêtes fonctionnent toujours
- [ ] Vous avez provoqué la perte d'un ingester et savez raconter ce qui s'est passé
- [ ] Vous avez des chiffres avant/après sur la mémoire et le nombre de pods
- [ ] Vous savez énoncer **à partir de quand** conseiller ce mode — et à partir de quand le déconseiller
- [ ] Vous savez estimer le coût en ETP d'exploitation, pas seulement en serveurs

## ⚠️ Le piège de cet exercice

Conclure que « distribué c'est mieux ». C'est l'erreur d'avant-vente la plus coûteuse : un client à qui on vend une architecture microservices dont il n'a pas besoin passera six mois à galérer, et ce sera votre faute. Le livrable de cet exercice n'est pas une plateforme distribuée, c'est **un seuil chiffré** au-delà duquel elle se justifie.

## 🎤 Ce que ça prouve en entretien

Que vous savez dire non. Un SE qui propose systématiquement l'architecture la plus grosse se fait repérer immédiatement par un architecte client. Savoir dire « à votre volume, le monolithique suffit, et voici à partir de quand on en reparlera » construit plus de confiance que n'importe quelle démonstration.

## 📚 Notes du vault liées

[[Mimir - métriques à l'échelle]] · [[Alloy - le collecteur unique]] · [[Loki - logs à faible coût]] · [[Kubernetes pour un SE]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.