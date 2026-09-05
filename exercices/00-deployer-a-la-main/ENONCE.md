# Exercice 00 — Déployer la stack à la main, sans les scripts

> **Durée estimée** : 3 à 5 h · **Niveau** : ⭐⭐ à faire AVANT tous les autres

## 🎬 Le contexte

Les scripts `01` à `03` montent le cluster et déploient toute la stack en trois commandes. C'est pratique une fois. C'est catastrophique pour apprendre : au bout de vingt minutes vous avez une plateforme qui tourne et **vous ne sauriez pas la refaire**.

Or un Solutions Engineer déploie devant un client, sur son cluster à lui, sans vos scripts, avec ses contraintes. Si vous ne savez faire que `.\scripts\03-deploy.ps1`, vous êtes démuni à la première question.

## 🎯 L'objectif

Monter la stack complète **à la main**, commande par commande, en comprenant chaque étape et en vérifiant chacune avant de passer à la suivante.

## 📦 Ce qui est fourni

- Les manifestes dans `k8s/`, commentés ligne par ligne
- Les scripts — **que vous n'avez pas le droit d'exécuter** pendant cet exercice
- La documentation officielle Grafana, Mimir, Loki, Alloy

## ✅ Les tâches

1. **Lire** `scripts/01-start-minikube.ps1` en entier, puis taper la commande `minikube start` vous-même, sans copier-coller, en justifiant chaque option (`--cpus`, `--memory`, `--driver`, `--profile`)
2. Créer les namespaces, et savoir dire pourquoi on sépare `observability` et `apps`
3. Déployer **Mimir seul**. Attendre qu'il soit `Ready`. Vérifier `/ready` et interroger son API avant d'aller plus loin
4. Déployer **Loki seul**. Même vérification
5. Se demander, avant de continuer : **pourquoi les backends avant l'agent ?** Écrire la réponse
6. Déployer **Alloy**. Ouvrir son UI sur :12345 et constater qu'il ne trouve encore aucune cible applicative
7. Déployer **Grafana**. ⚠️ `kubectl apply -f k8s\grafana` ne suffit PAS : le déploiement monte un objet qui n'est **pas déclaré** dans `k8s/`, parce qu'il est *généré* à partir de fichiers du dépôt. Si le pod reste en `ContainerCreating`, `kubectl describe pod` nomme précisément ce qui manque. À vous de trouver quoi, et de le créer
8. Construire l'image de l'API et la déployer. Regarder les cibles apparaître dans Alloy **en direct**
9. Générer du trafic à la main avec `curl`, avant même de lancer k6
10. À chaque étape, noter ce que vous avez dû chercher dans la documentation

## 🔎 La méthode : comment savoir AVANT de se planter

Trois moments pour attraper une dépendance manquante, du plus artisanal au plus
mécanique. Apprenez les trois, ils servent dans cet ordre.

**1. Après coup — le réflexe universel, sans aucun outil**
Un pod bloqué en `ContainerCreating` n'a **pas de logs**, parce que le conteneur
n'a jamais démarré. `kubectl logs` ne renverra rien et c'est déroutant.
La réponse est toujours en bas de :
```
kubectl -n <ns> describe pod <nom>
```
Section **Events**. Elle nomme précisément l'objet manquant. Ce réflexe marche
pour tout : image introuvable, volume non montable, quota dépassé, nœud saturé.

**2. Avant — lire le bloc `volumes:` du manifeste**
Un Deployment liste en clair ce dont il a besoin. Cinq lignes à lire :
```
volumes:
  - name: datasources         → configMap: grafana-datasources
  - name: dashboards-provider → configMap: grafana-dashboards-provider
  - name: dashboards          → configMap: grafana-dashboards      ← lequel n'est
  - name: storage             → emptyDir                              pas déclaré ?
```
Puis vérifier que chacun existe bien quelque part dans `k8s/`.

**3. Mécaniquement — le script fourni**
```powershell
.\scripts\06-verifier-dependances.ps1
```
Il compare ce que les manifestes **référencent** à ce qu'ils **déclarent**, et
affiche la différence. À lancer avant tout déploiement sur un environnement neuf,
et surtout quand on reprend les manifestes de quelqu'un d'autre — c'est-à-dire
tout le temps, chez un client.

> [!tip] Ce que ça vous donne en entretien
> « Comment vous prenez en main un déploiement que vous n'avez pas écrit ? »
> Répondre « je liste ce que les manifestes référencent sans le déclarer, parce
> que c'est ce qui bloque en silence » est une réponse d'ingénieur qui a déjà
> repris un environnement existant.

---

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [ ] La stack tourne, et **aucun script n'a été exécuté**
- [ ] Vous savez expliquer l'ordre de déploiement et ce qui casse si on l'inverse
- [ ] Vous avez une liste écrite des **3 endroits où vous vous êtes trompé** — c'est le vrai livrable
- [ ] Vous savez dire, pour chaque composant, quelle commande prouve qu'il fonctionne
- [ ] Refait une deuxième fois, vous tenez en moins de 45 minutes
- [ ] Vous pouvez commenter à voix haute pendant que ça se déploie, sans lire de notes

## ⚠️ Le piège de cet exercice

> [!] **Tout n'est pas déclaratif, et c'est un piège de production.**
> Un dépôt de manifestes donne l'illusion que `kubectl apply -f k8s/` suffit.
> En pratique il reste presque toujours des objets **générés** — ConfigMaps
> construites à partir de fichiers, secrets injectés par la CI, certificats émis
> par un opérateur. Le manifeste les *monte* sans les *créer*.
>
> Le symptôme est toujours le même : un pod bloqué en `ContainerCreating`, sans
> aucun log — puisque le conteneur n'a jamais démarré. `kubectl logs` ne donne
> rien, et c'est déroutant la première fois. La réponse est dans les **Events**
> de `kubectl describe pod`, tout en bas.
>
> Retenez la règle : **pas de logs = le conteneur n'a pas démarré = regardez les
> Events, pas les logs.**

Copier-coller les commandes des scripts sans les lire. Vous aurez le même résultat et appris la même chose que la première fois : rien. La contrainte de cet exercice n'est pas le résultat, c'est **le chemin**. Si vous bloquez, la règle est : documentation officielle d'abord, manifeste commenté ensuite, script en dernier recours — et si vous ouvrez le script, notez-le dans vos trois erreurs.

## 🎤 Ce que ça prouve en entretien

C'est l'exercice qui vous rend **autonome devant un client**. En démo, personne ne vous laissera lancer un script maison : on vous demandera de déployer sur leur cluster, en expliquant ce que vous faites. Et la question « qu'est-ce qui se casse si on déploie l'agent avant le backend ? » est un classique d'entretien technique.

## 📚 Notes du vault liées

[[Kubernetes pour un SE]] · [[Mimir - métriques à l'échelle]] · [[Alloy - le collecteur unique]] · [[Lab et exercices - le hub]]

---

> **État** : réalisé une fois le 2026-09-05 (12,5/20).
> Objectif du prochain passage : **moins de 45 minutes**, doc officielle d'abord.
> L'historique complet est en bas de ce fichier — **à ne pas relire avant d'avoir refait l'exercice**.
---
---

# 📓 Historique des passages

> [!warning] À ne pas lire avant d'avoir refait l'exercice
> Tout ce qui suit contient les réponses et le corrigé. Le relire avant de
> recommencer vide l'exercice de son intérêt.

---

## Passage 1 — samedi 5 septembre 2026 · **12,5 / 20**

| Axe | Note | Constat |
|---|---|---|
| **Exécution** | 16/20 | 35 min, composant par composant, avec vérification entre chaque. Les horodatages Kubernetes le prouvent : mimir 08:47, loki 09:02, alloy 09:17, grafana 09:20. C'est exactement la méthode demandée. |
| **Compréhension** | 11/20 | Les mécanismes sont acquis, les *raisons* moins : namespaces, ordre de déploiement, rôle réel de minikube. |
| **Méthode** | 8/20 | La documentation officielle n'a pas été ouverte une seule fois. Tout est venu des scripts et des manifestes. |

---

### Q1 — Justifier les options de `minikube start` · **3/5**

> **Ma réponse** — *minikube start permet de lancer minikube qui va ensuite instrumentaliser les containers des différents composants, l'api grafana alloy mimir loki etc. `--cpus` donne le nombre de cœurs qu'on alloue à minikube, `--memory` la mémoire, `--driver` c'est le système pour gérer les VM, ici hyperv. `--profile` permet de lier à un profil et donc dans les futures commandes de cibler ce profil et cette série de containers.*

**Correction.** Les flags sont justes, mais le modèle mental est faux.
`minikube start` crée **une VM avec Kubernetes dedans, et rien d'autre**. Il ne
connaît ni Grafana ni Mimir : c'est `kubectl apply` qui les y dépose ensuite.
minikube = la machine vide ; les manifestes = ce qui tourne dessus.

Deux précisions manquantes :
- `--memory=8192` avec **Hyper-V est réservé en dur** : ces 8 Go sont retirés à
  Windows tant que la VM vit. Avec le driver `docker`, c'est partagé.
- `--profile` ne sert pas qu'à cibler : chaque profil est **une VM séparée**.
  Sans lui, un `minikube delete` détruit le mauvais cluster.
- `--kubernetes-version` non mentionné : c'est la **reproductibilité**. Sans elle,
  deux installations à deux mois d'écart ne donnent pas la même version.

---

### Q2 — Pourquoi séparer `observability` et `apps` · **2/5**

> **Ma réponse** — *observability contient la stack Grafana, apps nos applications. Je dirais qu'on les sépare pour faciliter les flux ? apps → observability → nous.*

**Correction.** Le contenu est juste, la raison ne l'est pas.
**Un namespace n'a aucun effet sur le réseau.** Par défaut n'importe quel pod de
`apps` peut joindre n'importe quel pod de `observability`. Il faut des
`NetworkPolicy` pour cloisonner.

Les vraies raisons :
- **RBAC** — l'équipe produit sur `apps`, l'équipe plateforme sur `observability`
- **Quotas** — un `ResourceQuota` par namespace : une appli qui s'emballe ne prive
  pas la plateforme
- **Cycle de vie** — `kubectl delete namespace apps` sans toucher à la plateforme
- **Tri** — le label `namespace` est la clé de filtrage de tous les dashboards

> 🎤 **Question piège d'entretien** : un namespace est une **frontière
> administrative, pas une frontière de sécurité**.

---

### Q3 — Déployer et vérifier Mimir · **3,5/5**

> **Ma réponse** — *J'ai utilisé `kubectl wait --for=condition=ready pod -l app=mimir`. Je n'ai pas trouvé comment lancer le tunnel, je l'ai décortiqué du script 4. Sur `query=up` j'ai eu `{"status":"success","data":{"result":[]}}`. Je ne sais pas exactement comment ça s'est passé. Est-ce bien `image: grafana/mimir:2.17.1` qui lance cette app ?*

**Correction.** **Réussi sans le voir** : `success` + `[]` n'est **pas** un échec,
c'est le bon résultat. Mimir a répondu correctement ; l'ensemble est vide parce
qu'Alloy n'existait pas encore, donc personne ne scrapait.

> ⭐ Savoir distinguer *« la requête a échoué »* de *« la requête a réussi et ne
> renvoie rien »* est fondamental — c'est la moitié des faux diagnostics en
> observabilité.

**Les deux questions posées :**

- `pod` dans `kubectl wait ... pod -l app=mimir` est le **type de ressource**
  attendu ; `-l` est le **filtre par label**. On pourrait écrire `deployment/grafana`.
- Oui pour l'image, et la chaîne complète vaut la peine d'être sue :
  ```
  image:  grafana/mimir:2.17.1              → le binaire
  args:   -config.file=/etc/mimir/mimir.yaml → comment on le lance
  volumeMounts + ConfigMap                   → le fichier lu à ce chemin
  ```
  L'image est la même pour tout le monde ; **la ConfigMap en fait *votre* Mimir**.

---

### Q4 — Déployer et vérifier Loki · **5/5**

> **Ma réponse** — *Assez rapide cette fois, une fois la mécanique huilée ça part vite.*

Rien à redire.

---

### Q5 — Pourquoi les backends avant l'agent · **3/5**

> **Ma réponse** — *Loki et Mimir sont interchangeables dans leur ordre car ce sont des receveurs et des puits de données. On va venir les interroger. Tandis que Grafana et Alloy, eux aussi interchangeables, vont venir requêter Loki et Mimir. Si on les lance en premier ils ne vont pas trouver de réponses à leur requête d'initialisation.*

**Correction.** La direction de dépendance est juste, « mimir et loki
interchangeables » aussi. Deux corrections :

**Alloy n'interroge pas Mimir, il lui écrit** (`remote_write`). C'est Grafana qui
interroge. Deux sens opposés, même dépendance.

**Et surtout : ça ne casse pas.** Alloy lancé en premier démarre, ne joint pas
Mimir, **met en file d'attente dans son WAL** et retente. Grafana afficherait une
datasource rouge puis se rétablirait. Kubernetes est fait pour **converger**.

La vraie raison est la **diagnosticabilité** : en déployant dans l'ordre des
dépendances, chaque échec n'a qu'une cause possible. Si on lance tout d'un coup
et que ça ne marche pas, on ne peut pas distinguer « Alloy est mal configuré »
de « Mimir n'est pas encore là ».

> 🎤 **Nuance experte à sortir en entretien** : en production avec GitOps
> (ArgoCD, Flux) on **ne maîtrise pas l'ordre** — on s'appuie sur la convergence
> et les retry. L'ordre manuel est un outil d'apprentissage et de diagnostic,
> pas une règle absolue.

---

### Q6 — Déployer Alloy · **3/5**

> **Ma réponse** — *Mécanique bien huilée : `kubectl apply -f k8s\alloy`, port-forward, et l'UI sur :12345.*

**Correction.** Déployé correctement, mais l'énoncé demandait de **constater
qu'aucune cible applicative n'est trouvée** — ce n'est pas commenté. À ce moment
précis, `discovery.relabel.annotated_pods` exportait **3 cibles** (mimir, loki,
grafana) et pas `orders-api`, qui n'existait pas encore.

Non relevé non plus : `k8s/alloy/` contient un **ClusterRole et un
ClusterRoleBinding**, les seuls objets *cluster-scoped* du lab. Sans eux Alloy ne
peut ni découvrir les pods ni lire leurs logs — et c'est pour ça qu'un
`delete namespace` ne suffit pas à tout nettoyer.

---

### Q7 — Déployer Grafana · **4,5/5** ⭐ la meilleure réponse

> **Ma réponse** — *Le port-forward a échoué avec `Current status=Pending`. J'ai fait `wait`, puis je me suis un peu fait aider : `kubectl describe pod -l app=grafana`. Il fallait se rendre compte que `grafana-dashboards` n'existe pas et est référencé dans `grafana.yaml`, et que le pod est en ContainerCreating. En regardant les events le problème devient évident. `kubectl create configmap grafana-dashboards --from-file=grafana\dashboards` le crée.*

**Correction.** Enchaînement correct de bout en bout : `Pending` → `wait` →
`describe` → Events → cause identifiée → correctif. Et l'honnêteté sur l'aide
reçue compte.

Un détail : la séquence exacte est `Pending` (pas encore planifié, ou volumes non
montés) → `ContainerCreating` → `Running`. Bloqué sur un volume manquant, on
stagne entre les deux.

---

### Q8 — Construire l'image et voir les cibles apparaître · **4/5**

> **Ma réponse** — *`minikube image build -t orders-api:1.0.0 api --profile=grafana-lab`, puis apply et wait. Dans graph je vois des flux qui bougent quand je fais des appels, c'est ça ?*

**Correction.** Non. **L'onglet Graph ne s'anime pas avec le trafic** : il montre
la topologie des composants. Ce qui change quand l'API apparaît, c'est le
**nombre de cibles** sur `discovery.relabel.annotated_pods` : **3 → 5**. Le
trafic, lui, se regarde dans Grafana.

---

### Q9 — Générer du trafic à la main · **3,5/5**

> **Ma réponse** — *`curl http://localhost:8080/api/orders` → HTTP 200, contenu `[]`.*

**Correction.** Correct : aucune commande n'existe encore. Mais **seulement un
GET**. L'esprit de la tâche était de *générer* du trafic — un `POST /api/orders`
aurait créé une commande et fait bouger les métriques métier : chiffre
d'affaires, taille du panier, répartition des statuts.

---

### Q10 — Ce que j'ai cherché dans la documentation · **1/5**

> **Ma réponse** — *Globalement pas d'utilisation de la doc mais fouille dans les scripts et les configs.*

**Correction.** Honnête, et **c'est le point à corriger en priorité**. La règle
était : documentation officielle d'abord, manifeste ensuite, **script en dernier
recours**. L'ordre a été inversé.

Ça compte parce que devant un client il n'y aura pas vos scripts. Il y aura
`grafana.com/docs` et le cluster du client.

---

## 🔴 Les 3 erreurs du passage 1

*Le vrai livrable de l'exercice — la case avait été cochée sans que la liste existe.*

1. **Un manifeste peut monter un objet sans le créer.** `apply -f k8s/grafana`
   laissait le pod bloqué sans **aucun log**, parce que le conteneur n'avait
   jamais démarré. `grafana-dashboards` est *générée* depuis des fichiers, pas
   déclarée. Trouvé par `describe pod` → Events.
2. **Un port local ne se partage pas.** `port-forward` sur 8080 a échoué avec
   *« Only one usage of each socket address »* : le script 04 tenait déjà le
   tunnel. Réflexe : `Get-NetTCPConnection -LocalPort 8080 -State Listen`.
3. **`success` + `[]` pris pour un échec.** Mimir répondait parfaitement ; il
   n'y avait simplement rien à renvoyer.

---

## 🎯 Les 3 choses à changer au passage 2

1. **Documentation officielle en premier.** Interdiction d'ouvrir un script
   pendant les 10 premières minutes de blocage.
2. **Écrire les erreurs pendant, pas après.** Un fichier ouvert à côté, une ligne
   à chaque surprise.
3. **Vérifier par une requête applicative, pas par `Running`.** `count(up)` pour
   Mimir, `/loki/api/v1/labels` pour Loki, le nombre de cibles pour Alloy.

**Objectif : moins de 45 minutes.** Les deux pièges sont connus, c'est atteignable.

---

## Passage 2 — *à venir*

| | |
|---|---|
| Date | |
| Durée | |
| Note | |
| Mes 3 erreurs | |
