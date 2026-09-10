# Exercice 01 — Instrumenter une application de zéro

> **Durée estimée** : 3 à 4 h · **Niveau** : fondations

## 🎬 Le contexte

Une équipe produit vous tend une API Java qui n'expose **aucune** métrique. Elle est en production, elle a des incidents, et personne ne sait dire si elle va bien. C'est la situation de départ de la moitié des clients.

## 🎯 L'objectif

Partir d'une application nue et arriver à un dashboard RED exploitable, sans jamais deviner : chaque métrique ajoutée doit répondre à une question qu'on s'est posée d'abord.

## 🛠️ Préparer l'environnement

```powershell
.\exercices\01-instrumenter-de-zero\preparer.ps1
```

Le script **génère** une copie dépouillée de l'API depuis la version de référence
— il ne la duplique pas dans le dépôt, pour qu'elle ne dérive jamais. Puis :

```powershell
minikube image build -t orders-api-nue:1.0.0 .\exercices\01-instrumenter-de-zero\api-nue --profile=grafana-lab
kubectl apply -f .\exercices\01-instrumenter-de-zero\orders-api-nue.yaml
```

Elle se déploie **à côté** de l'API instrumentée, sous le nom `orders-api-nue`.
Vous pourrez comparer les deux à tout moment — c'est même conseillé.

**Ce qui a été retiré :** le registre Prometheus, `ObservabilityConfig`,
l'adaptateur `MicrometerOrderMetrics` (remplacé par un `NoOpOrderMetrics` qui
absorbe les appels en silence), l'exposition `/actuator/prometheus`, et les
annotations `prometheus.io` du pod.

**Ce qui a été gardé :** les logs JSON et `/actuator/health`. L'exercice porte
sur les métriques.

> [!tip] Ce que la structure vous montre déjà
> `NoOpOrderMetrics` implémente le même port que l'adaptateur supprimé. Le
> service, le domaine et les controllers n'ont **pas eu à changer d'une ligne**.
> Toute l'instrumentation métier tiendra dans cette seule classe : c'est le
> retour sur investissement de l'architecture hexagonale, rendu visible.

## 📦 Ce qui est fourni

- Une copie de l'API de commandes, **dépouillée** de toute instrumentation
- La stack LGTM déjà déployée (Mimir, Loki, Grafana, Alloy)
- Aucun dashboard

## ✅ Les tâches

1. Écrire, **avant de coder**, les 5 questions auxquelles le dashboard devra répondre
2. Ajouter Micrometer et exposer `/actuator/prometheus`
3. Faire découvrir le pod par Alloy (annotations + relabeling)
4. Vérifier l'arrivée des séries dans Mimir
5. Activer les buckets d'histogramme et vérifier qu'un p95 est calculable
6. Construire le dashboard RED : trafic, erreurs, latence p50/p95/p99
7. Ajouter **une** métrique métier qui n'existait pas et qui intéresse un non-technicien

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] Le dashboard répond aux 5 questions écrites au départ
- [ ] `histogram_quantile` est appliqué APRÈS `sum by (le)` — vérifiez, c'est le piège
- [ ] Le label `uri` contient le template `/api/orders/{id}`, jamais un ID réel
- [ ] Les 4xx et les 5xx sont distingués dans deux séries différentes
- [ ] Une métrique métier est affichée avec une unité lisible (euros, commandes)
- [ ] Vous savez expliquer à voix haute pourquoi chaque panneau existe

## ⚠️ Le piège de cet exercice

La tentation est d'ajouter des métriques d'abord et de chercher quoi en faire ensuite. C'est l'inverse du métier : on part de la question, jamais de la donnée disponible. Un dashboard de 40 panneaux que personne ne regarde est un échec, pas un livrable.

## 🎤 Ce que ça prouve en entretien

Que vous savez instrumenter, pas seulement lire des dashboards existants. C'est la base sur laquelle tous les autres exercices s'appuient, et c'est ce qu'un client vous demandera de faire devant lui en PoC.

## 📚 Notes du vault liées

[[Métriques - counter, gauge, histogram]] · [[PromQL - les requêtes qui comptent]] · [[Alloy - le collecteur unique]]

---

> **État** : ✅ environnement prêt — `preparer.ps1` le génère à la demande.