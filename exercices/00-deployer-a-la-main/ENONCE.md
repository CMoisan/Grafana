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

> **État** : 🌱 énoncé rédigé, environnement à construire.