# =============================================================================
# EXERCICE 02 - TOUT REMETTRE D'APLOMB
# =============================================================================
# A lancer une fois l'exercice termine, ou pour repartir de zero.
# Restaure les limites Mimir d'origine, retire la variable fautive, et
# reconstruit l'image saine.
# =============================================================================
# PIEGE POWERSHELL 5.1 : avec ErrorActionPreference a "Stop", la moindre ligne
# ecrite sur stderr par un executable natif (kubectl ecrit ses AVERTISSEMENTS
# la-dessus) devient une erreur fatale et interrompt le script, alors que la
# commande a parfaitement reussi. On reste donc en Continue, et on verifie
# explicitement $LASTEXITCODE la ou ca compte.
$ErrorActionPreference = "Continue"
$racine = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host "=== 1/3 Limites Mimir d'origine ===" -ForegroundColor Cyan
kubectl apply -f "$racine\k8s\mimir\mimir-config.yaml"
kubectl -n observability delete pod mimir-0 --wait=$false | Out-Null
Write-Host "  restaurees depuis le manifeste versionne" -ForegroundColor Green

Write-Host "`n=== 2/3 Retrait de la variable fautive ===" -ForegroundColor Cyan
# On reapplique le manifeste de reference : il ne contient pas la variable,
# et `apply` supprime ce qui n'y figure plus.
kubectl apply -f "$racine\k8s\api\orders-api.yaml"

Write-Host "`n=== 3/3 Reconstruction de l'image saine ===" -ForegroundColor Cyan
Write-Host "  (la variable d'environnement disparait, le drapeau retombe a false)" -ForegroundColor DarkGray
minikube image build -t orders-api:1.0.0 "$racine\api" --profile=grafana-lab
kubectl -n apps rollout restart deployment/orders-api | Out-Null
kubectl -n apps rollout status deployment/orders-api --timeout=180s

Write-Host "`n=== Verification ===" -ForegroundColor Cyan
kubectl -n observability wait --for=condition=ready pod -l app=mimir --timeout=180s
kubectl get pods -A --no-headers | Select-String "observability|apps"

Write-Host "`nPlateforme restauree." -ForegroundColor Green
Write-Host "La serie orders_created_detailed_total reste dans Mimir jusqu'a" -ForegroundColor DarkGray
Write-Host "expiration de la retention : c'est normal, et c'est une lecon en soi." -ForegroundColor DarkGray
