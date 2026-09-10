# 00 — Prérequis et outils

## État de votre poste

Les outils ci-dessous ont été **installés le 2026-08-31** sur cette machine.
Cette section sert de référence si vous devez refaire l'installation ailleurs.

| Outil | Version | Emplacement | Installé via |
|---|---|---|---|
| **JDK Temurin** | 21.0.12 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` | `winget EclipseAdoptium.Temurin.21.JDK` |
| **Maven** | 3.9.9 | `C:\Users\cleme\tools\apache-maven-3.9.9` | archive Apache (absent de winget) |
| **kubectl** | — | | `winget Kubernetes.kubectl` |
| **minikube** | — | | `winget Kubernetes.minikube` |
| **k6** | — | | `winget GrafanaLabs.k6` |
| **GitHub CLI** | — | | `winget GitHub.cli` |
| **Docker Desktop** | *non installé, volontairement* | | remplacé par Hyper-V, voir ci-dessous |

`JAVA_HOME` pointe désormais sur le **JDK 21** (l'installateur Temurin l'a
positionné au niveau machine). Le `PATH` utilisateur a été complété avec le
`bin` du JDK 21 et celui de Maven.

> **Important** : ces variables ne sont visibles que dans les terminaux
> **ouverts après** l'installation. Fermez et rouvrez PowerShell.

## Réinstaller ailleurs

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Kubernetes.kubectl
winget install Kubernetes.minikube
winget install GrafanaLabs.k6
winget install GitHub.cli
```

Maven n'est **pas** dans le catalogue winget par défaut. Deux options :

1. **Ne pas l'installer** — le projet embarque un *Maven wrapper* (voir plus bas).
2. Le poser à la main, sans droits administrateur :

```powershell
$tools = "$env:USERPROFILE\tools"
New-Item -ItemType Directory -Force -Path $tools | Out-Null
Invoke-WebRequest -UseBasicParsing `
  -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" `
  -OutFile "$env:TEMP\maven.zip"
Expand-Archive "$env:TEMP\maven.zip" -DestinationPath $tools -Force
# puis ajouter "$tools\apache-maven-3.9.9\bin" au PATH utilisateur
```

## Le Maven wrapper : compiler partout, sans rien installer

Le projet embarque `api/mvnw` et `api/mvnw.cmd`. Ces scripts téléchargent
automatiquement la bonne version de Maven au premier lancement.

```powershell
cd api
.\mvnw.cmd clean test        # Windows
```
```bash
cd api
./mvnw clean test            # Linux / macOS / Git Bash
```

**Pourquoi c'est important pour vous** : vous voulez pouvoir étudier ce projet
depuis n'importe quelle machine. Avec le wrapper, un simple `git clone` + un JDK
21 suffisent. Le fichier `.mvn/wrapper/maven-wrapper.properties` fige la version
de Maven (3.9.9) : tout le monde compile avec exactement le même outil, ce qui
élimine la classe de bugs « ça marche chez moi ».

C'est aussi la bonne pratique en entreprise, et un détail que les recruteurs
techniques remarquent.

## Vérification

```powershell
java -version           # doit afficher 21
kubectl version --client
minikube version
k6 version
# pas de `docker` : ce lab tourne sans Docker Desktop, voir plus bas
```

## Pas de Docker Desktop : le driver Hyper-V

Ce lab est configuré pour tourner **sans Docker Desktop**. minikube crée à la
place une véritable machine virtuelle Hyper-V, qui embarque son propre moteur de
conteneurs. L'hôte n'a besoin d'aucun Docker.

### Ce que ça implique

| | Driver `docker` | Driver `hyperv` (ce lab) |
|---|---|---|
| Prérequis | Docker Desktop | Windows Pro + Hyper-V activé |
| Élévation | une fois, à l'installation | **à chaque `minikube start`** |
| Redémarrage | non | **oui, après activation d'Hyper-V** |
| Mémoire | partagée avec l'hôte | **réservée en dur** à la VM |
| Démarrage | ~1 min | 5 à 10 min la première fois |
| Isolation | partage le noyau de l'hôte | VM complète, proche d'un vrai nœud |

> **Honnêteté sur le compromis** : Hyper-V n'évite pas la demande de droits
> administrateur, il la déplace. Il faut l'admin **une fois** pour activer la
> fonctionnalité, **un redémarrage**, puis un terminal élevé **à chaque
> démarrage** du cluster. Docker Desktop demande l'admin une seule fois puis
> n'en a plus besoin. Le gain d'Hyper-V est ailleurs : pas de logiciel tiers,
> pas de licence, et une isolation plus réaliste.

### Activation (une seule fois)

Dans un PowerShell **administrateur** :

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V -All
```

Puis **redémarrez**. L'hyperviseur se charge au démarrage de Windows, sous le
système d'exploitation : sans redémarrage il ne peut pas s'activer.

Vérifiez ensuite :

```powershell
(Get-CimInstance Win32_ComputerSystem).HypervisorPresent   # doit valoir True
```

Si cela reste à `False` après redémarrage, la **virtualisation (VT-x / AMD-V)
est désactivée dans le BIOS/UEFI**. C'est le seul blocage vraiment bloquant :
aucun cluster local ne fonctionnera tant qu'elle n'est pas activée, quel que
soit le driver choisi.

### Construire l'image sans Docker

Sans client Docker sur l'hôte, la méthode habituelle
(`minikube docker-env` puis `docker build`) est inutilisable. Le script
`02-build-api.ps1` utilise donc :

```powershell
minikube image build -t orders-api:1.0.0 .\api
```

minikube envoie le contexte de build dans la VM et construit avec le moteur qui
s'y trouve. L'image atterrit directement dans le cache du cluster : aucun
registre, aucun push. C'est en réalité la méthode la plus portable — elle
fonctionne avec tous les drivers — et beaucoup de monde l'ignore.

### Dimensionnement

La VM est créée avec **4 CPU et 8 Go**. Avec Hyper-V cette mémoire est
**retirée à Windows** pendant toute la durée de vie de la VM. Si votre machine a
16 Go ou moins, descendez à `--memory=6144` dans `scripts/01-start-minikube.ps1`
et passez l'API à `replicas: 1` dans `k8s/api/orders-api.yaml`.

### Revenir à Docker Desktop

Si vous changez d'avis, c'est un changement d'une ligne dans le script 01
(`--driver=docker`), plus le script 02 qui continue de fonctionner tel quel :
`minikube image build` est indépendant du driver.

## Consommation attendue

| Composant | Mémoire | Remarque |
|---|---|---|
| Mimir | ~700 Mo | dont le TSDB en mémoire |
| Loki | ~400 Mo | |
| Alloy | ~250 Mo | monte avec le nombre de cibles |
| Grafana | ~200 Mo | |
| orders-api ×2 | ~2 × 400 Mo | JVM, heap à 75 % de la limite |
| Kubernetes | ~1 Go | control plane, kubelet, CoreDNS |
| **Total** | **~4,5 Go** | d'où les 8 Go alloués |

## Note sur Helm

Ce lab utilise des **manifestes YAML bruts**, volontairement, alors que la
production utiliserait les charts Helm officiels (`grafana/mimir-distributed`,
`grafana/loki`, `grafana/alloy`).

**Pourquoi** : un chart Helm est une boîte noire de plusieurs milliers de lignes
générées. Vous ne verriez ni le relabeling, ni la structure du ring, ni les
sondes. Ici, chaque ligne est lisible et commentée.

**Ce qu'il faut savoir dire en entretien** : « en lab j'écris les manifestes à
la main pour comprendre, en production j'utilise les charts officiels avec un
`values.yaml` versionné, parce que la maintenance et les mises à jour d'une
stack Mimir en microservices ne se font pas à la main. » Cette réponse montre
que vous connaissez les deux et que vous savez quand utiliser quoi.
