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

Puis **isolez le terrain** :

```powershell
.\exercices\01-instrumenter-de-zero\isoler.ps1
```

Ce second script retire de votre cluster **l'API instrumentée** et **le dashboard
RED déjà provisionné**. Sans lui, vous auriez sous les yeux une application qui
émet déjà des métriques et un dashboard tout fait : la réponse, en somme.

Il garde Mimir, Loki, Alloy et Grafana : l'exercice en dépend entièrement.

> [!warning] La réponse est à un répertoire d'ici
> Le code instrumenté reste dans `api/` sur votre disque.
> `MicrometerOrderMetrics.java`, `ObservabilityConfig.java` et
> `grafana/dashboards/orders-api-red.json` contiennent la solution complète.
> **Ne les ouvrez pas avant d'avoir buté sur le problème.** Vous auriez le résultat
> sans le chemin, et cet exercice ne vaut que par le chemin.

> [!tip] Comparer, mais À LA FIN
> Une fois votre instrumentation en place, redéployez la version de référence et
> mettez les deux côte à côte : c'est là que la comparaison enseigne quelque chose.
> ```powershell
> kubectl apply -f k8s\api\orders-api.yaml
> kubectl -n observability create configmap grafana-dashboards --from-file=grafana\dashboards --dry-run=client -o yaml | kubectl apply -f -
> kubectl -n observability rollout restart deployment/grafana
> ```