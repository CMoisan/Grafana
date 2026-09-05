# Exercice 11 — Grafana Cloud contre self-hosted, chiffres en main

> **Durée estimée** : 4 à 6 h · **Niveau** : ⭐⭐⭐ la conversation commerciale

## 🎬 Le contexte

Vous savez déployer la stack vous-même. Un client vous demandera pourtant : « pourquoi je paierais Grafana Cloud alors que tout ça est open source et gratuit ? »

C'est **la** question du métier. Y répondre par des arguments de brochure ne convainc personne. Y répondre avec les chiffres de votre propre expérience, si.

## 🎯 L'objectif

Refaire le même lab en poussant vers Grafana Cloud, comparer honnêtement les deux, et construire l'argumentaire chiffré — y compris les cas où le self-hosted est le bon choix.

## 📦 Ce qui est fourni

- Le lab self-hosted, mesuré (séries actives, volume de logs) grâce à l'exercice 06
- Un compte **Grafana Cloud gratuit** — l'offre free tier suffit largement

## ✅ Les tâches

1. Créer le compte et récupérer les identifiants d'ingestion
2. Modifier **uniquement la configuration Alloy** : `basic_auth` et les URL d'ingestion. Constater que rien d'autre ne change — c'est déjà un argument
3. Pousser métriques et logs vers Grafana Cloud, en parallèle du self-hosted si possible
4. Recréer le dashboard : le même JSON doit fonctionner
5. Mesurer la consommation facturable côté Cloud et la comparer à vos chiffres locaux
6. Estimer le coût annuel Cloud à votre volume, puis à 10x et 100x ce volume
7. Estimer le coût du self-hosted **en ETP d'exploitation**, pas en serveurs : mises à jour, astreinte, montée en charge, sauvegardes
8. Explorer Adaptive Metrics et les leviers de réduction propres au Cloud
9. Écrire un tableau de décision : dans quels cas recommander l'un, dans quels cas l'autre

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Les deux plateformes reçoivent les mêmes données et affichent le même dashboard
- [ ] Vous avez un coût annuel estimé pour trois volumes différents
- [ ] Votre estimation du self-hosted inclut le **temps humain**, chiffré
- [ ] Vous savez citer **au moins deux cas où le self-hosted est le bon conseil** — souveraineté, secteur régulé, volume énorme et équipe déjà en place
- [ ] Vous savez répondre en 90 secondes à « pourquoi payer pour de l'open source ? » sans réciter

## ⚠️ Le piège de cet exercice

Répondre « Cloud c'est mieux ». Un client qui a déjà une équipe plateforme compétente et une contrainte de souveraineté n'ira pas dans le Cloud, et le lui vendre quand même détruit la relation. La bonne réponse n'est jamais un produit, c'est un **arbitrage** : « voici ce que vous payez d'un côté en euros, de l'autre en temps humain et en risque ; à vous de choisir ».

## 🎤 Ce que ça prouve en entretien

C'est la conversation que vous aurez le plus souvent, et celle qui décide de la vente. Un candidat capable de dire « j'ai fait tourner les deux, voici mes chiffres, et voici les cas où je déconseille le Cloud » démontre en une phrase de l'honnêteté et de l'expérience — les deux choses qu'un client cherche chez un SE.

## 📚 Notes du vault liées

[[Grafana Cloud vs self-hosted]] · [[Le modèle open source de Grafana]] · [[Exercice 06 - Réduction de coût]] · [[Paysage concurrentiel observabilité]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.