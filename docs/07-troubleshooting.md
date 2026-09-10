# 07 — « Je n'ai pas de données » : la méthode de diagnostic

C'est **la** question de support la plus fréquente sur la stack Grafana. Savoir
la dérouler méthodiquement, du producteur vers le consommateur, est une
compétence directement évaluée en entretien.

**Principe** : ne jamais partir du dashboard. Toujours remonter le flux dans
l'ordre, et s'arrêter au premier maillon défaillant.

---

## Chaîne des métriques : 5 points de contrôle

```
[1] L'app expose-t-elle ?  →  [2] Alloy voit-il la cible ?  →
[3] Alloy scrape-t-il ?    →  [4] Mimir reçoit-il ?         →  [5] Grafana lit-il ?
```

### 1. L'application expose-t-elle des métriques ?

```powershell
kubectl -n apps port-forward svc/orders-api 8080:8080
curl http://localhost:8080/actuator/prometheus | Select-String "http_server_requests"
```

- **Rien ne sort** → problème applicatif : dépendance
  `micrometer-registry-prometheus` absente, ou endpoint non exposé dans
  `management.endpoints.web.exposure.include`.
- **Ça sort** → passez à l'étape 2.

### 2. Alloy voit-il la cible ?

Ouvrez **http://localhost:12345** → composant `discovery.relabel.annotated_pods`.
L'UI montre les cibles **avant** et **après** relabeling.

- **0 cible** → les annotations `prometheus.io/scrape: "true"` manquent sur le
  pod, ou une règle `keep` les élimine.
- **Cible présente mais mauvais `__address__`** → l'annotation
  `prometheus.io/port` est absente ou fausse.

```powershell
kubectl -n apps get pod -l app=orders-api -o jsonpath="{.items[0].metadata.annotations}"
```

### 3. Alloy scrape-t-il avec succès ?

Composant `prometheus.scrape.pods` dans l'UI : il affiche l'état de chaque cible.

- **`connection refused`** → mauvais port, ou pod pas encore prêt.
- **`context deadline exceeded`** → `scrape_timeout` trop court, ou l'endpoint
  `/metrics` est lent (souvent une gauge qui fait une requête coûteuse).
- **404** → mauvais `__metrics_path__`.

```powershell
kubectl -n observability logs -l app=alloy --tail=100 | Select-String "error"
```

### 4. Mimir reçoit-il les données ?

```powershell
kubectl -n observability port-forward svc/mimir 9009:8080

# Toutes les cibles vues par Mimir
curl "http://localhost:9009/prometheus/api/v1/query?query=up"

# Cherche la métrique précise
curl "http://localhost:9009/prometheus/api/v1/query?query=http_server_requests_seconds_count"
```

Vérifiez l'état de la file d'envoi côté Alloy :

```promql
prometheus_remote_storage_samples_pending       # monte = Mimir n'absorbe pas
prometheus_remote_storage_samples_failed_total  # > 0 = envois rejetés
prometheus_remote_storage_shards                # au max = saturé
```

Et cherchez les rejets côté Mimir :

```powershell
kubectl -n observability logs -l app=mimir --tail=200 | Select-String "429|limit|err-mimir"
```

Les erreurs `err-mimir-*` sont **explicites** et nomment la limite exacte
dépassée (`max_global_series_per_user`, `ingestion_rate`…). C'est presque
toujours là que se trouve la réponse.

### 5. Grafana interroge-t-il la bonne URL ?

Configuration → Data sources → Mimir → **Save & test**.

- **`Post ... 404`** → l'URL oublie le suffixe `/prometheus`. C'est **l'erreur
  numéro 1** avec Mimir.
- **`connection refused`** → mauvais nom DNS. Depuis un pod, le nom complet est
  `mimir.observability.svc.cluster.local:8080`.
- **La datasource répond mais le panneau est vide** → problème de requête, pas de
  plateforme. Vérifiez la fenêtre de temps et les variables (`$namespace` vide
  donne un sélecteur qui ne matche rien).

---

## Chaîne des logs

### 1. Le pod écrit-il bien sur stdout ?

```powershell
kubectl -n apps logs -l app=orders-api --tail=20
```

Les lignes doivent être du **JSON sur une seule ligne**. Si vous voyez du texte
lisible, le profil Spring `local` est actif à tort : le format n'est alors pas
celui qu'attend le pipeline `loki.process`.

### 2. Alloy lit-il les logs ?

UI Alloy → `loki.source.kubernetes.pods`. Vérifiez le nombre de cibles.

Erreur fréquente : RBAC insuffisant sur `pods/log`.

```powershell
kubectl auth can-i get pods/log --as=system:serviceaccount:observability:alloy -n apps
# doit répondre : yes
```

### 3. Loki reçoit-il ?

```powershell
kubectl -n observability port-forward svc/loki 3100:3100
curl http://localhost:3100/ready
curl "http://localhost:3100/loki/api/v1/labels"
curl "http://localhost:3100/loki/api/v1/label/app/values"
```

Erreurs typiques dans les logs de Loki :

| Message | Cause |
|---|---|
| `entry too far behind` | horodatage trop ancien — le `stage.timestamp` a mal parsé le format |
| `entry out of order` | plusieurs sources écrivent dans le même stream |
| `stream limit exceeded` | trop de streams : **cardinalité de labels** |
| `rate limit exceeded` | `ingestion_rate_mb` dépassé |

### 4. La requête LogQL est-elle correcte ?

Testez **sans pipeline** d'abord :

```logql
{namespace="apps"}
```

Si cela renvoie des lignes, le problème est dans votre pipeline (parser, filtre),
pas dans la collecte. Ajoutez les étapes une par une.

---

## Symptômes courants et causes

| Symptôme | Cause la plus probable |
|---|---|
| Dashboard vide, datasource OK | fenêtre de temps ; ou variable de dashboard vide |
| Graphe en escalier / dents de scie | fenêtre de `rate()` trop courte pour l'intervalle de scrape |
| p99 égal au p50 | `percentiles-histogram` non activé : aucun bucket publié |
| Séries qui disparaissent puis reviennent | le pod redémarre — le label `pod` change à chaque fois |
| Mimir consomme énormément de RAM | trop de séries actives : cardinalité |
| Loki très lent | sélecteur de stream trop large, ou filtre placé après le parser |
| Pod en `CrashLoopBackOff` | `kubectl describe pod` puis `logs --previous` |
| Pod `OOMKilled` | `limits.memory` trop bas, ou `MaxRAMPercentage` trop haut |
| Latence inexpliquée sur k8s | **CPU throttling** : `container_cpu_cfs_throttled_seconds_total` |

---

## Commandes de diagnostic à connaître

```powershell
# Vue d'ensemble
kubectl get pods -A
kubectl top pods -A                       # nécessite metrics-server

# Un pod qui ne démarre pas : les EVENTS sont en bas de describe
kubectl -n apps describe pod <nom>

# Les logs du conteneur PRÉCÉDENT (après un crash) — souvent oublié,
# c'est pourtant là qu'est la cause du crash
kubectl -n apps logs <nom> --previous

# Événements du cluster, du plus récent au plus ancien
kubectl get events -A --sort-by=.lastTimestamp | Select-Object -Last 30

# Vérifier une ConfigMap effectivement montée
kubectl -n observability get configmap alloy-config -o yaml

# Forcer la prise en compte d'une ConfigMap modifiée : elle ne redémarre RIEN
# toute seule, c'est la cause de frustration numéro un
kubectl -n observability rollout restart daemonset/alloy

# Tester la résolution DNS depuis l'intérieur du cluster
kubectl -n apps run debug --rm -it --image=busybox:1.36 --restart=Never -- nslookup mimir.observability.svc.cluster.local
```

---

## La bonne posture en support

Face à un client bloqué, l'ordre est toujours le même :

1. **Reproduire** — quelle requête exacte, quelle fenêtre de temps, quel
   utilisateur ?
2. **Isoler la couche** — application, agent, backend, ou Grafana ? Une seule
   commande par couche suffit à trancher.
3. **Lire les logs du composant concerné** — les messages de Mimir et Loki sont
   explicites et nomment la limite dépassée.
4. **Vérifier les limites avant de blâmer le code** — dans la grande majorité des
   cas d'ingestion, la réponse est dans le bloc `limits`.

Cette méthode, énoncée telle quelle en entretien, vaut mieux que n'importe quelle
réponse encyclopédique : elle montre que vous savez travailler sous pression avec
un client au téléphone.

---

## Démarrage du cluster : les pannes propres à Hyper-V

Cette section couvre le premier lancement, qui est le moment où l'on perd le plus
de temps.

### `minikube start` refuse de démarrer

| Message | Cause | Correction |
|---|---|---|
| *« This driver requires elevated permissions »* | session non élevée | PowerShell **en administrateur** — obligatoire à chaque `minikube start` avec Hyper-V |
| *« Hyper-V is not available »* | fonctionnalité non activée | `Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V -All` puis **redémarrer** |
| `HypervisorPresent = False` après redémarrage | VT-x / AMD-V désactivé dans le BIOS | à activer dans l'UEFI — aucun cluster local ne marchera sans |
| *« no External vswitch nor Default Switch found »* | aucun commutateur virtuel | voir ci-dessous |

### ⚠️ Le piège du « Default Switch », à connaître avant de perdre une soirée

Le commutateur par défaut d'Hyper-V utilise un NAT dont **le sous-réseau change à
chaque redémarrage de Windows**. Conséquence : le cluster démarre parfaitement le
premier jour, et le lendemain `minikube start` échoue ou reste bloqué, parce que
minikube a mémorisé une IP qui n'existe plus.

> [!] **Cas réellement rencontré le 2026-09-10**
> ```
> IP mémorisée par minikube    192.168.162.239   (~/.minikube/profiles/<profil>/config.json)
> Default Switch après reboot  172.19.64.1/20
> kubeconfig                   https://192.168.162.239:8443
> ```
> Deux sous-réseaux sans rapport. La VM est injoignable et ne sort plus sur
> Internet. Messages associés :
> ```
> ! Échec de la connexion à https://registry.k8s.io/ depuis l'intérieur du minikube VM
> ! Impossible de redémarrer le(s) nœud(s) du plan de contrôle, le cluster sera réinitialisé
> ```
>
> **Le diagnostic en deux commandes**, avant de supprimer quoi que ce soit :
> ```powershell
> (Get-Content "$env:USERPROFILE\.minikube\profiles\grafana-lab\config.json" | ConvertFrom-Json).Nodes[0].IP
> Get-NetIPAddress -AddressFamily IPv4 | Where-Object InterfaceAlias -match "Default Switch"
> ```
> Si les deux ne sont pas dans le même sous-réseau, c'est ça.

**Symptômes** : `minikube start` qui tourne indéfiniment, ou
`Unable to connect to the server: dial tcp ... i/o timeout` sur toute commande
`kubectl`.

**Correction rapide** — recréer le profil, les manifestes étant tous versionnés
il n'y a rien à perdre :

```powershell
minikube delete --profile=grafana-lab
.\scripts\01-start-minikube.ps1
.\scripts\02-build-api.ps1
.\scripts\03-deploy.ps1
```

**Correction durable** — créer un commutateur **externe**, dont l'IP est stable.
À faire en administrateur, en remplaçant le nom de l'adaptateur par le vôtre
(`Get-NetAdapter` pour le trouver) :

```powershell
New-VMSwitch -Name "minikube-ext" -NetAdapterName "Ethernet" -AllowManagementOS $true
```

Puis, dans `scripts/01-start-minikube.ps1`, remplacer `Default Switch` par
`minikube-ext`.

> Attention : créer un commutateur externe coupe brièvement la connexion réseau de
> l'hôte, et c'est plus capricieux sur Wi-Fi que sur Ethernet.

### Un composant reste en `CrashLoopBackOff` au premier déploiement

C'est le risque résiduel le plus probable : Mimir, Loki et Alloy lisent une
**configuration embarquée dans une ConfigMap**. Kubernetes ne la valide pas — pour
lui, ce n'est qu'une chaîne de caractères. Une clé inconnue ou renommée entre deux
versions fait échouer le démarrage.

```powershell
kubectl -n observability logs -l app=mimir --tail=50
kubectl -n observability logs -l app=loki  --tail=50
```

Le message nomme **précisément** la clé fautive (`field X not found in type Y`).
Corriger la clé dans le fichier `*-config.yaml`, puis :

```powershell
kubectl apply -f k8s/mimir
kubectl -n observability rollout restart statefulset/mimir
```

> La configuration Alloy, elle, a été validée hors ligne avec
> `alloy validate config.alloy` : elle est saine.
