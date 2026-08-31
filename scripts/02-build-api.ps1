# =============================================================================
# 02 - CONSTRUCTION DE L'IMAGE DOCKER DE L'API, SANS DOCKER SUR L'HOTE
# =============================================================================
# LE PROBLEME
#   Sans Docker Desktop, il n'y a pas de commande `docker` sur ce poste. La
#   methode classique (`minikube docker-env` puis `docker build`) est donc
#   inutilisable : elle suppose un client Docker local.
#
# LA SOLUTION : `minikube image build`
#   minikube envoie le contexte de build DANS la VM et lance la construction
#   avec le moteur de conteneurs qui s'y trouve deja. L'image atterrit
#   directement dans le cache d'images du cluster : rien a pousser, aucun
#   registre, aucun client Docker.
#
#   Fonctionne avec TOUS les drivers (hyperv, docker, kvm...). C'est en realite
#   la methode la plus portable, et beaucoup de gens l'ignorent.
#
# LES 4 FACONS DE FAIRE ARRIVER UNE IMAGE DANS MINIKUBE (bon a savoir) :
#   1. minikube image build       -> construit dans la VM        (ce script)
#   2. minikube docker-env        -> construit avec le Docker de la VM
#                                    depuis un client local
#   3. minikube image load x.tar  -> importe une image deja construite ailleurs
#   4. registre distant + pull    -> la vraie methode de production
# =============================================================================
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "=== Verification du cluster ===" -ForegroundColor Cyan
$statut = minikube status --profile=grafana-lab 2>&1 | Out-String
if ($statut -notmatch "host: Running") {
  Write-Host "Le cluster n'est pas demarre. Lancez d'abord :" -ForegroundColor Red
  Write-Host "  .\scripts\01-start-minikube.ps1" -ForegroundColor Yellow
  exit 1
}
Write-Host "  [OK] Cluster demarre" -ForegroundColor Green

Write-Host ""
Write-Host "=== Build de orders-api:1.0.0 dans la VM ===" -ForegroundColor Cyan
Write-Host "Le premier build telecharge Maven et les dependances : 3 a 6 minutes." -ForegroundColor DarkGray
Write-Host "Les suivants sont bien plus rapides : le Dockerfile est concu pour que" -ForegroundColor DarkGray
Write-Host "la couche des dependances reste en cache tant que le pom ne change pas." -ForegroundColor DarkGray
Write-Host ""

# -t : le tag attendu par k8s/api/orders-api.yaml
# Le dernier argument est le CONTEXTE de build (le dossier api/).
minikube image build -t orders-api:1.0.0 "$root\api" --profile=grafana-lab
if ($LASTEXITCODE -ne 0) { throw "Echec du build de l'image" }

Write-Host ""
Write-Host "=== Images presentes dans le cluster ===" -ForegroundColor Cyan
minikube image ls --profile=grafana-lab | Select-String "orders-api"

Write-Host ""
Write-Host "Image construite." -ForegroundColor Green
Write-Host "Prochaine etape : .\scripts\03-deploy.ps1" -ForegroundColor Yellow
Write-Host ""
Write-Host "NOTE : apres chaque modification du code Java, relancez ce script" -ForegroundColor DarkGray
Write-Host "puis forcez le redeploiement :" -ForegroundColor DarkGray
Write-Host "  kubectl -n apps rollout restart deployment/orders-api" -ForegroundColor DarkGray
Write-Host "(le tag ne changeant pas, Kubernetes ne redeploie pas tout seul :" -ForegroundColor DarkGray
Write-Host " c'est un piege classique du developpement local sur k8s)" -ForegroundColor DarkGray
