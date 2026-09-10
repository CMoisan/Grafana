# Exercice 08 — Tempo — compléter les trois piliers

> **Durée estimée** : 4 à 6 h · **Niveau** : ⭐⭐ la démo la plus spectaculaire

## 🎬 Le contexte

Le lab a les métriques et les logs. Il manque les traces — donc la moitié de l'histoire.

Le terrain est déjà préparé, et c'est volontaire : Alloy reçoit déjà l'OTLP sur 4317/4318, l'application pousse déjà ses traces, les `derivedFields` de Loki et les exemplars de Mimir pointent déjà vers une datasource d'uid `tempo`. Tout ça ne mène nulle part aujourd'hui. **Il manque une seule brique, et c'est à vous de l'écrire.**

## 🎯 L'objectif

Déployer Tempo, brancher le pipeline de traces dans Alloy, et rendre vivante la navigation croisée : d'un log vers sa trace, et d'un pic de latence vers la requête responsable.

## 📦 Ce qui est fourni

- Alloy avec son récepteur OTLP déjà configuré
- L'application qui **génère** des spans et un traceId (visible dans les logs), mais qui **n'exporte rien** : la dépendance `opentelemetry-exporter-otlp` manque au `pom.xml`. L'ajouter fait partie de l'exercice
- Les `derivedFields` Loki et les `exemplarTraceIdDestinations` Mimir déjà écrits
- **Aucun manifeste Tempo** — c'est l'exercice

## ✅ Les tâches

1. Lire la documentation Tempo et choisir un mode de déploiement pour un lab
2. Écrire le StatefulSet, le Service et la ConfigMap Tempo, en vous inspirant de ceux de Loki
3. Dans Alloy, router les traces vers Tempo : le `otelcol.processor.batch` a aujourd'hui `traces = []`
4. Ajouter la datasource Tempo dans le provisioning Grafana, **avec l'uid `tempo`** — les autres datasources la référencent déjà par ce nom
5. Vérifier qu'une trace arrive : générer du trafic, la retrouver par son traceId
6. Activer les **exemplars** : depuis un pic de p99, cliquer et atterrir sur la trace
7. Vérifier la corrélation **log → trace** : un log d'erreur, un bouton, la trace
8. Configurer les **span metrics** ou le service graph, et comparer les métriques dérivées des traces à celles de Micrometer
9. Provoquer une panne au chaos panel et diagnostiquer **par les traces**, pas par les logs

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Depuis un log dans Explore, un clic ouvre la trace correspondante
- [ ] Depuis un pic de latence sur le dashboard, un clic ouvre une trace lente
- [ ] Vous savez dire combien de traces vous gardez, et ce que ça coûte
- [ ] Vous savez expliquer pourquoi Tempo n'indexe rien et ce que ça implique
- [ ] Vous racontez en 2 minutes un diagnostic fait par les traces, chiffres à l'appui

## ⚠️ Le piège de cet exercice

Déployer Tempo et s'arrêter là parce que « ça marche ». Le stockage des traces n'a aucune valeur en soi : la valeur est dans la **navigation croisée**. Une trace qu'on ne peut atteindre qu'en connaissant son identifiant ne sert à personne. Tant que le clic depuis un log ou depuis un pic de latence ne fonctionne pas, l'exercice n'est pas fini.

## 🎤 Ce que ça prouve en entretien

C'est **la démonstration la plus marquante du portefeuille Grafana**. « Je vois l'erreur dans le log, je clique, je vois les quatorze appels de service qui y ont mené » : aucun argumentaire ne remplace ça. Et le fait que vous l'ayez câblée vous-même, plutôt que de la découvrir dans une démo toute faite, s'entend immédiatement.

## 📚 Notes du vault liées

[[Tempo - traces]] · [[Traces et OpenTelemetry]] · [[Alloy - le collecteur unique]] · [[Grafana - la couche de visualisation]]

---

> **État** : 🌱 énoncé rédigé, environnement à construire.