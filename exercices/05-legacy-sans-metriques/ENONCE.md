# Exercice 05 — Instrumenter un legacy sans toucher au code

> **Durée estimée** : 3 à 4 h · **Niveau** : ⭐ argument de vente direct

## 🎬 Le contexte

Le client a une application métier critique écrite il y a douze ans. Personne n'ose y toucher, l'équipe qui l'a écrite est partie, et elle n'expose aucune métrique. Elle écrit seulement des lignes de log en texte libre. On vous dit : « celle-là, on ne peut rien faire ».

## 🎯 L'objectif

Produire un dashboard RED complet et une alerte fonctionnelle **sans modifier une seule ligne** de l'application.

## 📦 Ce qui est fourni

- Une application qui n'expose aucun endpoint de métriques
- Des logs en **texte libre**, pas en JSON — c'est volontaire, c'est le cas réel
- Alloy et la stack LGTM

## ✅ Les tâches

1. Analyser le format des logs et identifier ce qu'on peut en extraire
2. Écrire un pipeline `loki.process` avec un parser `pattern` ou `regexp`
3. Générer des métriques à partir des logs avec `stage.metrics`
4. Utiliser `unwrap` en LogQL pour calculer une latence à partir d'une valeur présente dans le log
5. Construire le dashboard RED uniquement à partir de ces métriques dérivées
6. Poser une alerte sur le taux d'erreur dérivé des logs
7. **Comparer** honnêtement : qu'est-ce qui manque par rapport à une vraie instrumentation ?

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Le dashboard RED existe et l'application n'a pas été recompilée
- [ ] Une alerte se déclenche quand on provoque des erreurs
- [ ] Vous savez lister **précisément** ce que cette approche ne donne pas (pas de traces, granularité limitée, dépendance au format des logs)
- [ ] Vous savez estimer le surcoût : ce chemin coûte plus cher en ingestion qu'une métrique native

## ⚠️ Le piège de cet exercice

Vendre ça comme équivalent à une vraie instrumentation. Ce n'en est pas une : c'est un point de départ qui débloque une situation. Un SE qui survend cette approche se fait rattraper au premier incident où le client cherche une trace qui n'existe pas. La force du discours, c'est justement d'en énoncer les limites soi-même.

## 🎤 Ce que ça prouve en entretien

C'est la réponse à l'objection la plus fréquente en avant-vente : « on ne peut pas réinstrumenter notre legacy ». Pouvoir répondre « on n'en a pas besoin pour commencer, voilà ce que je peux vous donner demain » débloque des affaires. À combiner avec [[Beyla et l'auto-instrumentation eBPF]].

## 📚 Notes du vault liées

[[LogQL et la logique de Loki]] · [[Beyla et l'auto-instrumentation eBPF]] · [[Alloy - le collecteur unique]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.