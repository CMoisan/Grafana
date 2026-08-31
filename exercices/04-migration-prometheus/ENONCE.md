# Exercice 04 — Migrer un Prometheus existant vers Alloy et Mimir

> **Durée estimée** : Demi-journée à 1 jour · **Niveau** : ⭐ scénario client type

## 🎬 Le contexte

Le client a un Prometheus qui tourne depuis trois ans. 400 dashboards, 120 règles d'alerte, et une équipe qui n'a aucune envie de tout refaire. Il veut la rétention longue et la haute disponibilité, mais il ne signera jamais pour un projet qui casse l'existant.

## 🎯 L'objectif

Démontrer une migration **sans rupture** : les dashboards continuent de fonctionner, les alertes aussi, et on peut revenir en arrière à tout moment.

## 📦 Ce qui est fourni

- Un Prometheus déployé, scrapant l'API, avec ses propres règles et dashboards
- Mimir et Alloy disponibles à côté

## ✅ Les tâches

1. Inventorier l'existant : combien de cibles, de séries, de règles
2. Configurer le `remote_write` du Prometheus existant vers Mimir — **sans rien débrancher**
3. Vérifier que les mêmes requêtes PromQL donnent le même résultat sur les deux sources
4. Migrer les règles d'alerte vers le ruler Mimir, et vérifier l'équivalence
5. Basculer les datasources Grafana de Prometheus vers Mimir
6. Remplacer le scrape Prometheus par Alloy, cible par cible
7. Définir et documenter le **plan de retour arrière** à chaque étape

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Aucun dashboard n'a été modifié
- [ ] Une même requête donne un résultat identique avant et après (à la fenêtre de rétention près)
- [ ] Les alertes se déclenchent au même moment sur les deux systèmes pendant la phase de double écriture
- [ ] Le plan de retour arrière est écrit et a été testé au moins une fois
- [ ] Vous savez chiffrer la durée de la phase de double écriture et son coût

## ⚠️ Le piège de cet exercice

Vouloir tout basculer d'un coup. Un client ne signe pas une migration big bang. La valeur d'un SE est de découper en étapes réversibles — et de savoir dire à quel moment précis on peut débrancher l'ancien système sans risque.

## 🎤 Ce que ça prouve en entretien

Que vous savez conduire un changement chez un client qui a un existant, ce qui est le cas de 100 % des affaires réelles. C'est aussi la démonstration concrète de l'argument « pas de verrouillage ».

## 📚 Notes du vault liées

[[Mimir - métriques à l'échelle]] · [[Alloy - le collecteur unique]] · [[PromQL - les requêtes qui comptent]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.