# =============================================================================
# EXERCICE 06 - RETOUR A LA CONFIGURATION OPTIMISEE
# =============================================================================
# PIEGE POWERSHELL 5.1 : avec ErrorActionPreference a "Stop", la moindre ligne
# ecrite sur stderr par un executable natif (kubectl ecrit ses AVERTISSEMENTS
# la-dessus) devient une erreur fatale et interrompt le script, alors que la
# commande a parfaitement reussi. On reste donc en Continue, et on verifie
# explicitement $LASTEXITCODE la ou ca compte.
$ErrorActionPreference = "Continue"
$racine = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host "=== 1/2 Config Alloy d'origine ===" -ForegroundColor Cyan
kubectl apply -f "$racine\k8s\alloy\alloy-config.yaml"
kubectl -n observability rollout restart daemonset/alloy | Out-Null

Write-Host "=== 2/2 Logs applicatifs en INFO ===" -ForegroundColor Cyan
kubectl -n apps set env deployment/orders-api LOGGING_LEVEL_ROOT- LOGGING_LEVEL_COM_GRAFANALAB_ORDERS- | Out-Null
kubectl -n apps rollout status deployment/orders-api --timeout=180s
kubectl -n observability rollout status daemonset/alloy --timeout=120s

Write-Host "`nPlateforme restauree." -ForegroundColor Green
Write-Host "Les series creees pendant l'exercice restent dans Mimir jusqu'a" -ForegroundColor DarkGray
Write-Host "expiration de la retention. C'est normal, et c'est une lecon :" -ForegroundColor DarkGray
Write-Host "une erreur de cardinalite coute encore longtemps apres sa correction." -ForegroundColor DarkGray
