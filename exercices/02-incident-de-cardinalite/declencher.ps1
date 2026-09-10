# =============================================================================
# EXERCICE 02 - DECLENCHER L'INCIDENT
# =============================================================================
# A LANCER SANS LIRE LE CONTENU DU DOSSIER manifests\.
# Tout l'interet est de diagnostiquer par les symptomes, pas par le code source.
#
# Ce script :
#   1. abaisse les limites de Mimir pour que l'incident survienne en minutes
#   2. reconstruit et redeploie l'API avec une modification
#   3. genere du trafic pour amorcer la pompe
#
# Puis il vous laisse devant une plateforme qui se degrade. A vous.
# =============================================================================
$ErrorActionPreference = "Stop"
$racine = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host "=== 1/4 Abaissement des limites Mimir ===" -ForegroundColor Cyan
$cm = kubectl -n observability get configmap mimir-config -o jsonpath="{.data.mimir\.yaml}"
$cm | Out-File "$env:TEMP\mimir-original.yaml" -Encoding utf8
$patch = $cm -replace 'max_global_series_per_user: \d+', 'max_global_series_per_user: 3000'
$patch = $patch -replace 'ingestion_rate: \d+', 'ingestion_rate: 2000'
$patch = $patch -replace 'ingestion_burst_size: \d+', 'ingestion_burst_size: 4000'
$tmp = "$env:TEMP\mimir-patched.yaml"
[System.IO.File]::WriteAllText($tmp, $patch, (New-Object System.Text.UTF8Encoding $false))
kubectl -n observability create configmap mimir-config --from-file=mimir.yaml=$tmp --dry-run=client -o yaml | kubectl apply -f -
kubectl -n observability delete pod mimir-0 --wait=$false | Out-Null
Write-Host "  limites abaissees, Mimir redemarre" -ForegroundColor Green

Write-Host "`n=== 2/4 Reconstruction de l'image (3 a 6 min) ===" -ForegroundColor Cyan
minikube image build -t orders-api:1.0.0 "$racine\api" --profile=grafana-lab
if ($LASTEXITCODE -ne 0) { throw "echec du build" }

Write-Host "`n=== 3/4 Redeploiement de l'API ===" -ForegroundColor Cyan
kubectl apply -f "$PSScriptRoot\manifests\api-cardinalite.yaml"
kubectl -n apps rollout restart deployment/orders-api | Out-Null
kubectl -n apps rollout status deployment/orders-api --timeout=180s

Write-Host "`n=== 4/4 Generation de trafic ===" -ForegroundColor Cyan
Write-Host "  k6 tourne 3 minutes pour amorcer la pompe." -ForegroundColor DarkGray
$env:BASE_URL = "http://localhost:8080"
$env:TEST_RUN = "incident-cardinalite"
k6 run --quiet --duration 3m --vus 5 "$racine\k6\01-smoke.js" 2>&1 | Select-Object -Last 5

Write-Host "`n=========================================" -ForegroundColor Yellow
Write-Host " L'incident est en cours." -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "Ouvrez Grafana. Constatez. Puis remontez la chaine." -ForegroundColor White
Write-Host "Rappel de la methode : Grafana -> Mimir -> Alloy -> application." -ForegroundColor DarkGray
Write-Host ""
Write-Host "Pour tout remettre d'aplomb : .\exercices\02-incident-de-cardinalite\restaurer.ps1" -ForegroundColor DarkGray
