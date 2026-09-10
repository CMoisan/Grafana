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

> [!warning] La solution est à un répertoire d'ici
> `MicrometerOrderMetrics.java`, `ObservabilityConfig.java` et
> `grafana/dashboards/orders-api-red.json` sont toujours sur votre disque.
> **Ne les ouvrez pas avant d'avoir buté sur le problème.** Vous auriez le
> résultat sans le chemin, et cet exercice ne vaut que par le chemin.

> [!tip] Comparer avec la référence, mais À LA FIN
> ```powershell
> kubectl apply -f k8s\api\orders-api.yaml
> kubectl -n observability create configmap grafana-dashboards --from-file=grafana\dashboards --dry-run=client -o yaml | kubectl apply -f -
> kubectl -n observability rollout restart deployment/grafana
> ```

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

## 🏢 Le contexte métier

> Vous reprenez le service **orders-api** de **Vertuoz**, l'e-commerce du brief
> [[MEDDPICC - session pratique Grafana]]. Il traite la création et le paiement
> des commandes — environ **40 000 par jour**, avec des pics le samedi soir —
> et appelle une passerelle de paiement externe.

### 🔥 Ce qui a déclenché la demande

Il y a trois semaines, **un samedi soir, pendant environ quarante minutes, les
clients n'ont plus pu payer.** Les commandes se créaient, les paiements
échouaient.

Personne ne s'en est aperçu. C'est **un client qui a appelé le support**. Le
temps de comprendre, de remonter à l'équipe et de fouiller les logs : plus d'une
heure. Et aujourd'hui encore, **personne ne sait dire si le problème venait de
l'application ou de la banque.**

### 👥 Vos trois interlocuteurs

| Qui | Ce qu'il vous a dit |
|---|---|
| **Karim**, Staff SRE | *Je veux savoir qu'on est en train de tomber **avant** que le support m'appelle. Et quand ça tombe, savoir en trente secondes si c'est nous ou un prestataire.* |
| **Léa**, resp. support client | *Quand un client m'appelle, je n'ai rien à lui répondre. Est-ce que c'est lui, est-ce que c'est tout le monde, est-ce que c'est réparé ? Je ne sais jamais.* |
| **Sophie**, VP Engineering | *Ce que je veux pouvoir dire à la direction, c'est combien cet incident nous a coûté. Personne n'a su me le chiffrer.* |

> [!important] Ce que ce contexte doit produire
> **Vos 5 questions se déduisent d'eux, pas de ce que Micrometer sait produire.**
> Si vos cinq questions sont toutes techniques, vous êtes passé à côté : ni Léa
> ni Sophie ne parlent de p95 ou de heap.
>
> C'est le renversement du métier. Un ingénieur part de la donnée disponible et
> construit un dashboard. Un Solutions Engineer part de **la personne qui
> souffre** et remonte jusqu'à la donnée qui manque.

> [!tip] Pour aller plus loin
> Demandez-moi de jouer Karim, Léa ou Sophie, et posez-leur vos questions.
> Interroger un interlocuteur non technique **est** l'exercice central du poste
> — c'est déjà ce que vous faisiez chez Nickel avec les conseillers.

---
## ✅ Les tâches

1. Écrire, **avant de coder**, les 5 questions auxquelles le dashboard devra répondre — déduites de **Karim, Léa et Sophie**, pas de ce que l'outillage sait mesurer
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