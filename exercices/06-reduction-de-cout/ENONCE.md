# Exercice 06 — Diviser la facture par deux, chiffres à l'appui

> **Durée estimée** : 4 à 6 h · **Niveau** : ⭐⭐ le plus vendeur

## 🎬 Le contexte

Reprise du brief *Vertuoz* de [[MEDDPICC - session pratique Grafana]] : 470 k€ par an, personne ne sait expliquer pourquoi. On vous demande de démontrer une réduction, et de la **prouver** — pas de l'affirmer.

## 🎯 L'objectif

Partir d'une plateforme volontairement mal configurée, mesurer, réduire, et produire un chiffre défendable devant un directeur financier.

## 🛠️ Préparer l'environnement

Le lab de référence est **déjà optimisé** — liste blanche cAdvisor, buckets JVM
filtrés, logs en INFO. C'est précisément ce qui empêche de faire l'exercice.

```powershell
.\exercices06-reduction-de-cout\degrader.ps1
```

Il défait ces optimisations pour vous placer devant une plateforme telle qu'on la
trouve chez un client qui n'a jamais regardé sa facture. **Lisez-le si vous
voulez** : savoir *quoi* couper ne gâche rien. La difficulté est de **mesurer,
couper dans le bon ordre, et prouver que rien n'a cassé**.

Puis, avant de toucher à quoi que ce soit :

```powershell
.\exercices06-reduction-de-cout\mesurer.ps1 -Etiquette "avant"
```

L'outil compte les séries actives par famille, le débit d'ingestion et le volume
de logs, **consigne tout dans `mesures.csv`**, et projette le résultat à ×100 et
×1000 — parce que c'est ce raisonnement qui parle à un directeur financier, pas
le chiffre brut de votre lab.

Relancez-le après **chaque** levier, avec une étiquette différente. Le tableau
avant/après se construit tout seul.

## 📦 Ce qui est fourni

- Une stack configurée « comme chez un vrai client » : cAdvisor complet, tous les buckets JVM, logs DEBUG en production, aucun filtrage
- Un compteur de séries actives et de volume de logs

## ✅ Les tâches

1. **Mesurer l'état initial** : séries actives, volume de logs par jour, top 10 des métriques les plus coûteuses
2. Identifier les métriques **jamais requêtées** par aucun dashboard ni aucune alerte
3. Appliquer les leviers dans l'ordre : suppression des métriques inutilisées, réduction de cardinalité, allongement du scrape, filtrage des logs DEBUG, échantillonnage
4. **Après chaque levier**, remesurer et noter le gain
5. Vérifier qu'**aucun dashboard ni aucune alerte n'a cessé de fonctionner**
6. Produire un tableau avant/après avec le pourcentage de réduction
7. Rédiger la restitution en **une slide** destinée à un non-technicien

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Réduction d'au moins 50 % des séries actives
- [ ] Tous les dashboards et alertes fonctionnent encore — vérifié, pas supposé
- [ ] Chaque levier a son gain chiffré séparément, pas un total global
- [ ] La slide finale ne contient aucun terme technique et tient un chiffre en euros
- [ ] Vous savez dire quel levier a le meilleur rapport gain/risque, et pourquoi

## ⚠️ Le piège de cet exercice

Couper d'abord et vérifier ensuite. Un SE qui casse un dashboard client pendant un PoC de réduction de coût perd l'affaire immédiatement — il a prouvé exactement ce que le client craignait. La séquence est : mesurer, couper une chose, vérifier, recommencer.

## 🎤 Ce que ça prouve en entretien

C'est le `M` de MEDDPICC : le SE est celui qui **produit** le chiffre qui justifie l'achat. Un candidat capable de dire « j'ai réduit de 60 % sans casser un dashboard, voici la méthode » parle le langage du métier, pas celui de la console.

## 📚 Notes du vault liées

[[Cardinalité et coût]] · [[Grafana Cloud vs self-hosted]] · [[MEDDPICC - session pratique Grafana]]

---

> **État** : ✅ environnement prêt.