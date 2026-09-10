# =============================================================================
# EXERCICE 06 - REMETTRE LA PLATEFORME "COMME CHEZ UN VRAI CLIENT"
# =============================================================================
# Le lab de reference est DEJA optimise : liste blanche cAdvisor, buckets JVM
# filtres, logs en INFO. C'est justement ce qui empeche de faire l'exercice.
#
# Ce script defait ces optimisations pour vous placer devant une plateforme
# telle qu'on la trouve chez un client qui n'a jamais regarde sa facture.
#
# Il n'y a rien de sournois ici : le contenu est lisible, et le savoir ne gache
# pas l'exercice. La difficulte du 06 n'est pas de TROUVER quoi couper, c'est de
# MESURER, couper dans le bon ordre, et prouver que rien n'a casse.
# =============================================================================
# PIEGE POWERSHELL 5.1 : avec ErrorActionPreference a "Stop", la moindre ligne
# ecrite sur stderr par un executable natif (kubectl ecrit ses AVERTISSEMENTS
# la-dessus) devient une erreur fatale et interrompt le script, alors que la
# commande a parfaitement reussi. On reste donc en Continue, et on verifie
# explicitement $LASTEXITCODE la ou ca compte.
$ErrorActionPreference = "Continue"
$racine = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host "=== 1/3 Retrait des filtres cote Alloy ===" -ForegroundColor Cyan
$cfg = kubectl -n observability get configmap alloy-config -o jsonpath="{.data.config\.alloy}"
$cfg | Out-File "$env:TEMP\alloy-optimise.alloy" -Encoding utf8

# a) la liste blanche cAdvisor devient un "garde tout"
$cfg = $cfg -replace 'regex = "container_\(cpu_usage_seconds_total[^"]*"', 'regex = "container_.+"'
# b) on cesse de jeter les buckets JVM les plus verbeux
$cfg = $cfg -replace 'regex         = "jvm_gc_pause_seconds_bucket\|jvm_buffer_\.\*"', 'regex         = "ne_correspond_a_rien_du_tout"'

$tmp = "$env:TEMP\alloy-degrade.alloy"
[System.IO.File]::WriteAllText($tmp, $cfg, (New-Object System.Text.UTF8Encoding $false))
kubectl -n observability create configmap alloy-config --from-file=config.alloy=$tmp `
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n observability rollout restart daemonset/alloy | Out-Null
Write-Host "  cAdvisor complet, buckets JVM conserves" -ForegroundColor Green

Write-Host "`n=== 2/3 Passage des logs applicatifs en DEBUG ===" -ForegroundColor Cyan
kubectl -n apps set env deployment/orders-api LOGGING_LEVEL_ROOT=DEBUG LOGGING_LEVEL_COM_GRAFANALAB_ORDERS=DEBUG | Out-Null
kubectl -n apps rollout status deployment/orders-api --timeout=180s
Write-Host "  volume de logs multiplie" -ForegroundColor Green

Write-Host "`n=== 3/3 Attente de la stabilisation ===" -ForegroundColor Cyan
kubectl -n observability rollout status daemonset/alloy --timeout=120s
Write-Host "  laissez tourner 5 minutes avant la premiere mesure" -ForegroundColor DarkGray

Write-Host "`n=========================================" -ForegroundColor Yellow
Write-Host " Plateforme degradee, comme chez un client." -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "Premiere chose a faire, AVANT de toucher a quoi que ce soit :" -ForegroundColor White
Write-Host "  .\exercices\06-reduction-de-cout\mesurer.ps1 -Etiquette 'avant'" -ForegroundColor White
Write-Host ""
Write-Host "Puis un levier a la fois, en remesurant apres chacun." -ForegroundColor DarkGray
Write-Host "Retour a la normale : .\exercices\06-reduction-de-cout\restaurer.ps1" -ForegroundColor DarkGray
