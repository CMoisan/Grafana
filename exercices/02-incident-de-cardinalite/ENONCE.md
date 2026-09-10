# Exercice 02 — Diagnostiquer un incident de cardinalité

> **Durée estimée** : 2 à 3 h · **Niveau** : ⭐ à faire absolument

## 🎬 Le contexte

Il est 9 h. Les dashboards sont vides depuis cette nuit. Mimir renvoie des 429, Alloy accumule dans sa file d'attente, et la seule chose qui a changé hier soir, c'est un déploiement « mineur » d'une équipe produit.

## 🎯 L'objectif

Retrouver la cause en partant des symptômes, la corriger, et poser un garde-fou pour que ça ne se reproduise pas. Puis savoir raconter l'incident en trois minutes.

## 🛠️ Déclencher l'incident

```powershell
.\exercices-incident-de-cardinalite\declencher.ps1
```

> [!danger] Ne lisez PAS le dossier `manifests\` avant d'avoir diagnostiqué
> Il contient la cause. Tout l'intérêt est de la retrouver **par les symptômes**,
> comme un matin où une équipe produit a déployé la veille au soir.
>
> Si vous êtes bloqué plus de 45 minutes, allez le lire. Mais essayez d'abord —
> et notez que vous l'avez ouvert, ça fait partie du bilan.

Le script abaisse les limites de Mimir pour que l'incident survienne en minutes,
redéploie l'API avec une modification, puis génère du trafic. Comptez **5 à
8 minutes** avant que la dégradation soit franche.

Pour tout remettre d'aplomb ensuite :

```powershell
.\exercices-incident-de-cardinaliteestaurer.ps1
```

## 📦 Ce qui est fourni

- Une version de l'API qui met l'**identifiant de commande** en label Prometheus
- Des limites Mimir volontairement basses pour que ça casse vite
- Aucune indication sur la cause : c'est l'exercice

## ✅ Les tâches

1. Constater la panne côté Grafana, sans encore savoir pourquoi
2. Remonter la chaîne : Grafana → Mimir → Alloy → application
3. Lire les logs Mimir et identifier le message `err-mimir-*` exact
4. Trouver **quelle métrique** et **quel label** explosent (API `/api/v1/status/tsdb`)
5. Chiffrer : combien de séries avant, combien après, quel facteur
6. Corriger à la source (le code), puis poser un garde-fou côté Alloy (relabeling `drop`)
7. Ajouter un troisième garde-fou côté application (`maximumAllowableTags`)
8. Rédiger un post-mortem d'une page : chronologie, cause, correction, prévention

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Vous avez trouvé la cause **sans lire le code en premier** : par les symptômes
- [ ] Le nombre de séries actives est mesuré avant et après, avec un chiffre
- [ ] Les trois garde-fous sont en place et vous savez dire pourquoi trois et pas un
- [ ] Les dashboards refonctionnent
- [ ] Le post-mortem tient en une page et se lit par un non-technicien

## ⚠️ Le piège de cet exercice

Corriger uniquement le code. Le jour où un autre développeur refait la même erreur, la plateforme retombe. Un SE qui ne propose qu'un correctif applicatif n'a pas compris que son travail est de protéger la plateforme contre ses utilisateurs.

## 🎤 Ce que ça prouve en entretien

C'est **l'exercice le plus rentable de tous** pour l'entretien. La cardinalité est le premier sujet que soulève un client, et raconter un incident vécu vaut infiniment mieux que réciter une définition.

## 📚 Notes du vault liées

[[Cardinalité et coût]] · [[Mimir - métriques à l'échelle]] · [[Post-mortems à connaître]]

---

> **État** : ✅ environnement prêt — `declencher.ps1` provoque l'incident.