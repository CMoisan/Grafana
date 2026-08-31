# =============================================================================
# 01 - DEMARRAGE DU CLUSTER MINIKUBE (driver Hyper-V, sans Docker Desktop)
# =============================================================================
# POURQUOI HYPER-V PLUTOT QUE DOCKER DESKTOP ?
#   Choix assume sur ce poste : pas d'installation de Docker Desktop.
#   minikube cree alors une VM Hyper-V complete qui embarque son propre moteur
#   de conteneurs. L'hote n'a besoin d'AUCUN Docker.
#
# CE QU'IL FAUT SAVOIR (et savoir dire en entretien) :
#   - driver `docker`  : minikube tourne DANS un conteneur. Demarrage rapide,
#     faible empreinte, mais partage le noyau de l'hote.
#   - driver `hyperv`  : vraie VM, isolation complete, plus proche d'un noeud
#     de production. Demarrage plus lent, RAM reservee en dur.
#   - driver `none`    : sur l'hote directement, Linux uniquement, deconseille.
#
# CONTRAINTES DU DRIVER HYPER-V, a connaitre avant de commencer :
#   1. Windows Pro/Enterprise requis (Famille n'a pas Hyper-V). OK ici.
#   2. La fonctionnalite Hyper-V doit etre ACTIVEE -> admin + REDEMARRAGE.
#   3. Toute commande `minikube` doit tourner dans un POWERSHELL ADMINISTRATEUR.
#      Hyper-V refuse de piloter des VM autrement.
#   4. La virtualisation (VT-x / AMD-V) doit etre active dans le BIOS/UEFI.
# =============================================================================
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# PRE-VOL : on verifie tout AVANT de lancer quoi que ce soit.
# Un script qui echoue a la moitie laisse un cluster a moitie cree, bien plus
# penible a diagnostiquer qu'un refus net au demarrage.
# ---------------------------------------------------------------------------
Write-Host "=== Verifications prealables ===" -ForegroundColor Cyan

$estAdmin = ([Security.Principal.WindowsPrincipal] `
  [Security.Principal.WindowsIdentity]::GetCurrent()
).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $estAdmin) {
  Write-Host ""
  Write-Host "  [X] Ce script doit tourner en ADMINISTRATEUR." -ForegroundColor Red
  Write-Host "      Le driver hyperv pilote des machines virtuelles : Windows"
  Write-Host "      l'interdit depuis une session non elevee."
  Write-Host ""
  Write-Host "      Ouvrez PowerShell via clic droit > Executer en tant"
  Write-Host "      qu'administrateur, puis relancez ce script." -ForegroundColor Yellow
  exit 1
}
Write-Host "  [OK] Session administrateur" -ForegroundColor Green

$hv = Get-CimInstance Win32_OptionalFeature -Filter "Name='Microsoft-Hyper-V-All'" -ErrorAction SilentlyContinue
if (-not $hv -or $hv.InstallState -ne 1) {
  Write-Host ""
  Write-Host "  [X] La fonctionnalite Hyper-V n'est pas activee." -ForegroundColor Red
  Write-Host ""
  Write-Host "      Activez-la avec la commande ci-dessous, PUIS REDEMARREZ :" -ForegroundColor Yellow
  Write-Host ""
  Write-Host "        Enable-WindowsOptionalFeature -Online ``" -ForegroundColor White
  Write-Host "          -FeatureName Microsoft-Hyper-V -All" -ForegroundColor White
  Write-Host ""
  Write-Host "      Le redemarrage est OBLIGATOIRE : l'hyperviseur se charge au"
  Write-Host "      demarrage de Windows, sous le systeme d'exploitation."
  exit 1
}
Write-Host "  [OK] Hyper-V active" -ForegroundColor Green

if (-not (Get-CimInstance Win32_ComputerSystem).HypervisorPresent) {
  Write-Host ""
  Write-Host "  [X] Aucun hyperviseur en cours d'execution." -ForegroundColor Red
  Write-Host "      Soit vous n'avez pas redemarre apres l'activation d'Hyper-V,"
  Write-Host "      soit la virtualisation (VT-x / AMD-V) est desactivee dans le"
  Write-Host "      BIOS/UEFI. Verifiez ce reglage : sans lui, aucun cluster local"
  Write-Host "      ne fonctionnera, quel que soit le driver." -ForegroundColor Yellow
  exit 1
}
Write-Host "  [OK] Hyperviseur actif" -ForegroundColor Green

# ---------------------------------------------------------------------------
# COMMUTATEUR VIRTUEL
# Une VM Hyper-V a besoin d'un "switch" pour acceder au reseau. minikube sait
# en creer un, mais le switch par defaut (`Default Switch`) est plus fiable :
# il fournit un NAT deja configure. Sans reseau, la VM ne peut pas telecharger
# les images des conteneurs et le demarrage echoue apres plusieurs minutes.
# ---------------------------------------------------------------------------
$switch = Get-VMSwitch -Name "Default Switch" -ErrorAction SilentlyContinue
if ($switch) {
  Write-Host "  [OK] Commutateur 'Default Switch' disponible" -ForegroundColor Green
  $switchArg = @("--hyperv-virtual-switch=Default Switch")
} else {
  Write-Host "  [!] 'Default Switch' absent, minikube en creera un" -ForegroundColor Yellow
  $switchArg = @()
}

# ---------------------------------------------------------------------------
# DEMARRAGE
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "=== Demarrage de minikube (driver hyperv) ===" -ForegroundColor Cyan
Write-Host "Premiere execution : 5 a 10 minutes (creation de la VM + images)." -ForegroundColor DarkGray

# DIMENSIONNEMENT : la stack complete consomme environ 4,5 Go. Contrairement au
# driver docker, Hyper-V RESERVE cette memoire en dur : elle est retiree a
# Windows pendant toute la duree de vie de la VM. Ne montez pas au-dela de la
# moitie de votre RAM physique.
minikube start `
  --driver=hyperv `
  --cpus=4 `
  --memory=8192 `
  --disk-size=40g `
  --kubernetes-version=v1.31.0 `
  --profile=grafana-lab `
  @switchArg

minikube profile grafana-lab

Write-Host ""
Write-Host "=== Activation des addons ===" -ForegroundColor Cyan

# metrics-server alimente `kubectl top`. Utile pour croiser ce que dit
# Kubernetes et ce que disent VOS metriques : les deux doivent coincider.
minikube addons enable metrics-server --profile=grafana-lab
minikube addons enable storage-provisioner --profile=grafana-lab

Write-Host ""
Write-Host "=== Etat du cluster ===" -ForegroundColor Cyan
kubectl get nodes -o wide

Write-Host ""
Write-Host "Cluster pret." -ForegroundColor Green
Write-Host "Prochaine etape : .\scripts\02-build-api.ps1" -ForegroundColor Yellow
