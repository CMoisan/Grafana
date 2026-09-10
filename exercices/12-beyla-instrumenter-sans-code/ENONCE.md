# Exercice 12 — Beyla — instrumenter sans toucher au code

> **Durée estimée** : 4 à 6 h · **Niveau** : ⭐⭐⭐ la meilleure démo client

## 🎬 Le contexte

C'est l'objection la plus fréquente en avant-vente, et elle est légitime :

> *« On a deux cents services, dont la moitié écrits par des gens partis depuis. On ne va pas tous les réinstrumenter pour vous faire plaisir. »*

Beyla est la réponse. Il s'attache au noyau Linux via **eBPF**, observe les appels réseau, et en déduit des métriques RED et des traces **sans qu'une seule ligne de code applicatif ne change**.

Mais ce n'est pas magique, et c'est justement ce que cet exercice doit vous faire toucher du doigt.

## 🎯 L'objectif

Produire DEUX dashboards RED du même service — l'un depuis l'instrumentation Micrometer existante, l'autre depuis Beyla seul — puis savoir présenter l'écart entre les deux à un client.

## 📦 Ce qui est fourni

- L'API de commandes, déjà instrumentée avec Micrometer et son dashboard RED
- La stack LGTM en place
- **Aucun manifeste Beyla** — à écrire, c'est l'exercice

## ✅ Les tâches

1. Lire la documentation Beyla et choisir son mode de déploiement : DaemonSet, ou sidecar ?
2. Comprendre AVANT de déployer quels privilèges il réclame, et pourquoi (`CAP_BPF`, `hostPID`…). Savoir l'expliquer à un RSSI
3. Déployer Beyla en le pointant sur l'API, **sans rien changer à l'application**
4. Router ses métriques vers Mimir via Alloy
5. Construire un **second dashboard RED**, alimenté uniquement par Beyla
6. Mettre les deux dashboards **côte à côte** et comparer : les taux, les latences, les labels disponibles
7. Lancer `.\scripts\05-k6.ps1 simulation` et vérifier que les deux racontent la même histoire
8. **Lister ce que Beyla ne voit pas.** C'est le livrable principal
9. Provoquer une panne au chaos panel et regarder lequel des deux permet de la diagnostiquer

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Deux dashboards RED du même service, l'un sans aucune modification du code
- [ ] Les taux de requêtes des deux sources concordent à moins de 10 % près — sinon, savoir dire pourquoi
- [ ] Vous savez lister au moins **quatre choses** que Beyla ne peut pas fournir
- [ ] Vous savez expliquer eBPF à un non-spécialiste en deux phrases
- [ ] Vous savez répondre à *« c'est safe de mettre ça dans mon noyau ? »*
- [ ] Vous tenez la démonstration comparative en **5 minutes**, chiffres à l'appui

## ⚠️ Le piège de cet exercice

Vendre Beyla comme un remplacement de l'instrumentation. C'en est un **point de départ**, pas un aboutissement. Un SE qui survend ça se fait rattraper au premier client qui cherche son chiffre d'affaires dans un dashboard eBPF et ne le trouve pas. La force du discours est justement d'énoncer la limite avant que le client ne la découvre.

## 🎤 Ce que ça prouve en entretien

C'est **la meilleure démo comparative du portefeuille**, parce qu'elle ne présente pas un produit mais un **arbitrage**. Vous montrez que vous savez débloquer vite (deux cents services couverts demain) ET aller au fond (l'instrumentation métier, là où c'est rentable). Un client entend qu'on lui propose un chemin, pas qu'on lui vend une licence.

## 📚 Notes du vault liées

[[Beyla et l'auto-instrumentation eBPF]] · [[Exercice 05 - Legacy sans métriques]] · [[Métriques - counter, gauge, histogram]] · [[Traces et OpenTelemetry]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.