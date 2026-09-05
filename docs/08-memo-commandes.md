# 08 — Mémo : commandes et pièges

Fiche de terrain, issue du premier déploiement manuel. Volontairement dense.

---

## `minikube start`

```
minikube start --driver=hyperv --cpus=4 --memory=8192 `
  --disk-size=40g --kubernetes-version=v1.31.0 --profile=grafana-lab
```

| Option | Ce qu'elle fait, et le piège |
|---|---|
| `--driver=hyperv` | Vraie VM, aucun Docker sur l'hôte. **Exige un PowerShell admin à chaque `start`**, et SVM/VT-x activé au BIOS |
| `--cpus=4` | Cœurs alloués à la VM |
| `--memory=8192` | **Avec Hyper-V, réservé en dur** : retiré à Windows tant que la VM vit. Partagé avec le driver `docker` |
| `--disk-size=40g` | Taille du disque virtuel |
| `--kubernetes-version` | Reproductibilité. Sans elle, deux installs à deux mois d'écart diffèrent |
| `--profile=nom` | **Une VM par profil.** Sans lui, un `minikube delete` peut détruire le mauvais cluster |

> `minikube start` crée **une VM avec Kubernetes, et rien d'autre**. Il ne connaît
> ni Grafana ni Mimir : c'est `kubectl apply` qui les y dépose.

---

## Les commandes qui servent tous les jours

```powershell
# Déployer un dossier entier de manifestes
kubectl apply -f k8s\mimir

# ATTENDRE vraiment (ne pas se fier à `get pods`)
kubectl -n NS wait --for=condition=ready pod -l app=X --timeout=180s

# LE réflexe de diagnostic — les Events sont en BAS
kubectl -n NS describe pod -l app=X

# Logs, et logs du conteneur précédent après un crash
kubectl -n NS logs -l app=X --tail=50
kubectl -n NS logs POD --previous

# Chronologie du cluster
kubectl get events -A --sort-by=.lastTimestamp | Select-Object -Last 20

# Tunnel : LOCAL:DISTANT
kubectl -n NS port-forward svc/X 3000:3000 --address 127.0.0.1

# Après TOUT changement de ConfigMap
kubectl -n NS rollout restart deployment/X    # ou statefulset/ daemonset/

# Construire une image sans Docker sur l'hôte
minikube image build -t orders-api:1.0.0 api --profile=grafana-lab

# Nettoyer
kubectl delete namespace observability apps
kubectl delete clusterrole alloy ; kubectl delete clusterrolebinding alloy
```

---

## La preuve de vie, composant par composant

`Running` ne veut pas dire « fonctionne ». Toujours finir par une requête réelle.

| Composant | Preuve |
|---|---|
| **Mimir** | `/ready` puis `/prometheus/api/v1/query?query=up` |
| **Loki** | `/ready` puis `/loki/api/v1/labels` |
| **Alloy** | `/-/ready` puis l'UI `:12345` — regarder le **nombre de cibles** |
| **Grafana** | `/api/health` → `"database": "ok"` |
| **API** | `/actuator/health` → `"status":"UP"` |

---

## Les 10 pièges rencontrés

1. **Pas de logs = le conteneur n'a jamais démarré.** `kubectl logs` renvoie vide,
   c'est normal. La réponse est dans les **Events** de `describe pod`.
2. **Un `apply` sur une ConfigMap ne redémarre rien.** Le fichier change, le
   process ne le relit pas → `rollout restart`.
3. **Un manifeste peut monter un objet sans le créer.** ConfigMap générée par
   `create --from-file`, absente des manifestes → pod bloqué en silence.
   Détection : `.\scripts\06-verifier-dependances.ps1`
4. **ClusterRole et ClusterRoleBinding survivent à `delete namespace`.**
   Ils sont *cluster-scoped*. À supprimer explicitement.
5. **`port-forward L:D`** — L est sur le PC, D dans le pod, indépendants.
   Mimir est en `9009:8080` **pour éviter la collision avec l'API sur 8080**.
   Dans le cluster, Mimir reste sur 8080 : c'est ce que dit la datasource.
6. **Un port local ne se partage pas.** *« Only one usage of each socket address »*
   → `Get-NetTCPConnection -LocalPort 8080 -State Listen`
7. **`{"status":"success","result":[]}` est un SUCCÈS**, pas un échec.
   La base répond, elle n'a simplement rien à renvoyer.
8. **`port-forward` meurt quand le pod redémarre**, silencieusement.
9. **Supprimer un namespace est asynchrone** : 30 à 60 s en `Terminating`.
   Recréer avant la fin échoue.
10. **Un namespace est une frontière administrative, pas réseau ni sécurité.**
    Sans `NetworkPolicy`, tout se parle.

---

## Ordre de déploiement

```
namespaces → Mimir → Loki → Alloy → Grafana → application
             \_____ backends _____/   \__ consommateurs __/
```

**Pourquoi cet ordre ?** Pas parce que ça casse — Alloy met en file d'attente dans
son WAL et retente, Grafana se rétablit. C'est une question de
**diagnosticabilité** : chaque échec n'a alors qu'une seule cause possible.

> En production avec GitOps, on ne maîtrise pas l'ordre : on s'appuie sur la
> convergence. L'ordre manuel est un outil de diagnostic, pas une règle.
