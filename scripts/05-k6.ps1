# =============================================================================
# 05 - LANCER UN TEST k6
# =============================================================================
# Usage :
#   .\scripts\05-k6.ps1 smoke
#   .\scripts\05-k6.ps1 load
#   .\scripts\05-k6.ps1 simulation
#   .\scripts\05-k6.ps1 load -InCluster       (execute dans k8s + export Mimir)
#
# PREREQUIS pour le mode local : k6 installe (winget install k6.k6) ET le
# port-forward de l'API actif (script 04).
# =============================================================================
param(
  [Parameter(Mandatory = $true)]
  [ValidateSet("smoke", "load", "stress", "soak", "simulation")]
  [string]$Test,

  [switch]$InCluster
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$fichiers = @{
  smoke      = "01-smoke.js"
  load       = "02-load.js"
  stress     = "03-stress.js"
  soak       = "04-soak.js"
  simulation = "05-simulation.js"
}
$script = $fichiers[$Test]

if ($InCluster) {
  Write-Host "=== Execution DANS le cluster (metriques exportees vers Mimir) ===" -ForegroundColor Cyan

  # Un Job termine ne peut pas etre relance : on supprime l'ancien.
  kubectl -n apps delete job k6-load --ignore-not-found=true
  Start-Sleep -Seconds 2

  # On adapte le script cible a la volee avec un patch JSON.
  $manifest = Get-Content "$root\k8s\k6\k6-job.yaml" -Raw
  $manifest = $manifest -replace "/scripts/02-load\.js", "/scripts/$script"
  $manifest | kubectl apply -f -

  Write-Host "`nSuivi des logs (Ctrl+C pour detacher, le job continue) :" -ForegroundColor Yellow
  Start-Sleep -Seconds 5
  kubectl -n apps logs -f job/k6-load
}
else {
  Write-Host "=== Execution locale contre http://localhost:8080 ===" -ForegroundColor Cyan
  Write-Host "(le port-forward du script 04 doit etre actif)`n" -ForegroundColor DarkGray

  New-Item -ItemType Directory -Force -Path "$root\k6-results" | Out-Null

  # TEST_RUN horodate : permet de comparer plusieurs runs dans Grafana en
  # filtrant sur le tag `testid`. Prenez cette habitude des le debut.
  $runId = "$Test-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

  $env:BASE_URL = "http://localhost:8080"
  $env:TEST_RUN = $runId

  # --summary-trend-stats : ajoute p(99) au resume, absent par defaut.
  k6 run `
    --summary-trend-stats="min,avg,med,p(95),p(99),max" `
    --out "json=$root\k6-results\$runId.json" `
    "$root\k6\$script"

  Write-Host "`nResultats bruts : k6-results\$runId.json" -ForegroundColor Green
  Write-Host "Identifiant du run (tag testid dans Grafana) : $runId" -ForegroundColor Green
}
