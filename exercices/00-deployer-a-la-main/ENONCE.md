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

minikube start si j'ai bien compris permet de lancer minikube qui va ensuite instrumentaliser les containers des différents composant, l'api grafana alloy mimir loki etc... --cpus donne le nombre de coeur qu'on alloue a minikube, --memory la mémoire, --driver c'est le systeme pour gérer les vm ici sur notre systeme c'est hyperv, profile permet de lié a un profile et donc dans les futur commande de cibler ce profile et cette série de container.

2. Créer les namespaces, et savoir dire pourquoi on sépare `observability` et `apps`

Donc si j'ai bien compris pour créer les namescpace on défini une conf yaml namespace.yaml ici on a fait observability et apps. J'imagine que observability contient la stack grafana tandis que apps notre ou nos applications. Je dirai qu'on les sépare pour facilité les flux ? apps -> observability -> Nous.

3. Déployer **Mimir seul**. Attendre qu'il soit `Ready`. Vérifier `/ready` et interroger son API avant d'aller plus loin

J'ai pu lancer mimir j'ai utiliser la commande kubectl -n observability wait --for=condition=ready pod -l app=mimir --timeout=180s que j'ai parfaitement comrpise pour voir si c'était bon. Juste pod dans la commande je ne sais ptet pas ce que ça signifie. Mais je n'ai pas trouvé comment lancer le tunnel, en allant sur http://localhost:9009/prometheus/api/v1/query?query=up j'ai rien du tout.
J'ai fait cette commande la après pour forward : 
kubectl -n "observability" port-forward "svc/mimir" "9009:8080" --address 127.0.0.1
Je l'ai décortiqué du script 4
Et j'ai eu ça sur l'url query up {"status":"success","data":{"resultType":"vector","result":[]}}
Le problème est que je ne sais pas exactement comment ça s'est passé. Est ce que c'est bien image: grafana/mimir:2.17.1 qui permet a l'initialisation du container de lancer cette app la ? 

4. Déployer **Loki seul**. Même vérification

Assez rapide cette fois, une fois la mécanique huilé ça part vite kubectl apply -f "k8s\loki" pour lancer avec la config loki et ensuite le port forward avec kubectl -n "observability" port-forward "svc/loki" "3100:3100" --address 127.0.0.1 et on a le ready dans http://localhost:3100/ready
5. Se demander, avant de continuer : **pourquoi les backends avant l'agent ?** Je ne suis pas sur de comprendre la question. Si c'est pour l'ordre mimir -> loky -> Alloy -> Grafana. Alors je dirais que loki et mimir sont interchangeable dans leur ordre car ce sont des receveurs et des puit de données. On va venir les interroger. Tandis que grafana et alloy eux aussi interchangeable dans leur ordre de lancement vont venir request loki et mimir. Si on les lances en premier ils ne vont pas trouver de réponses a leur requete d'initialisation.
6. Déployer **Alloy**. Ouvrir son UI sur :12345 et constater qu'il ne trouve encore aucune cible applicative

Mécanique bien huilé : kubectl apply -f "k8s\alloy"
kubectl -n "observability" port-forward "svc/alloy" "12345:12345" --address 127.0.0.1
Et ensuite sur http://localhost:12345 on a l'UI

7. Déployer **Grafana**. Vérifier que les datasources sont vertes — et si non, diagnostiquer sans regarder la solution

J'ai lancé les commandes 
PS E:\Java\Grafana> kubectl apply -f "k8s\grafana"
configmap/grafana-dashboards-provider created
configmap/grafana-datasources created
service/grafana created
deployment.apps/grafana created
PS E:\Java\Grafana> kubectl -n "observability" port-forward "svc/grafana" "3000:3000" --address 127.0.0.1
error: unable to forward port because pod is not running. Current status=Pending

Ducoup j'ai fait :
kubectl -n observability wait --for=condition=ready pod -l app=grafana --timeout=180s

Je me suis un peu fait aider : 
kubectl -n observability describe pod -l app=grafana
il fallait se rendre compte que grafana-dashboards n'existe pas et est référencé dans grafana.yaml et que le pod est en ContainerCreating en utilisant la commande kubectl -n observability describe pod -l app=grafana on voit les events et le problème devient evidant. La commande kubectl -n observability create configmap grafana-dashboards --from-file=grafana\dashboards créer grafana-dashboards.


8. Construire l'image de l'API et la déployer. Regarder les cibles apparaître dans Alloy **en direct**

J'ai lancé minikube image build -t orders-api:1.0.0 "api" --profile=grafana-lab
kubectl apply -f "k8s\api"
kubectl -n apps wait --for=condition=ready pod -l app=orders-api --timeout=120s

Dans graph je vois des flux qui bougent quand je fais des appels c'est ça ?

9. Générer du trafic à la main avec `curl`, avant même de lancer k6

Fait 

PS E:\Java\Grafana> curl http://localhost:8080/api/orders


StatusCode        : 200
StatusDescription :
Content           : []
RawContent        : HTTP/1.1 200
                    Transfer-Encoding: chunked
                    Content-Type: application/json
                    Date: Sat, 05 Sep 2026 09:58:31 GMT

                    []
Forms             : {}
Headers           : {[Transfer-Encoding, chunked], [Content-Type, application/json], [Date, Sat, 05 Sep 2026 09:58:31 GMT]}
Images            : {}
InputFields       : {}
Links             : {}
ParsedHtml        : System.__ComObject
RawContentLength  : 2

10. À chaque étape, noter ce que vous avez dû chercher dans la documentation
Globalement pas d'utilisation de la doc mais fouille dans les scripts et les configs
## 🔎 La méthode : comment savoir AVANT de se planter

**1. Après coup — le réflexe universel, sans aucun outil**
Un pod en `ContainerCreating` n'a **pas de logs** : le conteneur n'a jamais démarré.
`kubectl logs` ne renvoie rien, et c'est déroutant. La réponse est en bas de
`kubectl -n <ns> describe pod <nom>`, section **Events**.

**2. Avant — lire le bloc `volumes:` du manifeste**
Un Deployment liste en clair ses dépendances. Vérifier que chaque nom de ConfigMap
ou de Secret existe bien quelque part dans `k8s/`.

**3. Mécaniquement — le script fourni**
```powershell
.\scripts\06-verifier-dependances.ps1
```
Il compare ce que les manifestes **référencent** à ce qu'ils **déclarent**.
Sur ce dépôt il sort `grafana-dashboards`, `k6-scripts` et `k6-lib` — les deux
derniers vous auraient bloqué plus tard, sur le Job k6.

---

## 🏁 Critères de réussite

Vous avez terminé quand **toutes** ces cases sont cochées :

- [X] La stack tourne, et **aucun script n'a été exécuté**
- [X] Vous savez expliquer l'ordre de déploiement et ce qui casse si on l'inverse
- [X] Vous avez une liste écrite des **3 endroits où vous vous êtes trompé** — c'est le vrai livrable
- [X] Vous savez dire, pour chaque composant, quelle commande prouve qu'il fonctionne
- [ ] Refait une deuxième fois, vous tenez en moins de 45 minutes
- [ ] Vous pouvez commenter à voix haute pendant que ça se déploie, sans lire de notes

## ⚠️ Le piège de cet exercice

Copier-coller les commandes des scripts sans les lire. Vous aurez le même résultat et appris la même chose que la première fois : rien. La contrainte de cet exercice n'est pas le résultat, c'est **le chemin**. Si vous bloquez, la règle est : documentation officielle d'abord, manifeste commenté ensuite, script en dernier recours — et si vous ouvrez le script, notez-le dans vos trois erreurs.

## 🎤 Ce que ça prouve en entretien

C'est l'exercice qui vous rend **autonome devant un client**. En démo, personne ne vous laissera lancer un script maison : on vous demandera de déployer sur leur cluster, en expliquant ce que vous faites. Et la question « qu'est-ce qui se casse si on déploie l'agent avant le backend ? » est un classique d'entretien technique.

## 📚 Notes du vault liées

[[Kubernetes pour un SE]] · [[Mimir - métriques à l'échelle]] · [[Alloy - le collecteur unique]] · [[Lab et exercices - le hub]]

---

> **État** : ✅ réalisé le 2026-09-05. Réponses inline. À refaire en < 45 min.