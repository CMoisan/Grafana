# =============================================================================
# 04 - OUVRIR LES ACCES AUX INTERFACES (avec surveillance et reprise)
# =============================================================================
# POURQUOI CE SCRIPT SURVEILLE-T-IL SES PROPRES TUNNELS ?
#
# `kubectl port-forward` choisit UN pod au moment ou on le lance, et s'arrete
# des que ce pod disparait. Or dans un lab on redemarre des pods en permanence :
# un `rollout restart`, un correctif de ConfigMap, un OOMKill, et le tunnel
# meurt SILENCIEUSEMENT. On croit alors que le composant est casse alors que
# seul le tunnel l'est.
#
# Ce script relance donc automatiquement tout tunnel tombe, et affiche leur
# etat. C'est exactement ce qu'un SE fait en demo : ne jamais laisser un
# probleme d'outillage passer pour un probleme de produit.
#
# LES 3 FACONS D'ATTEINDRE UN SERVICE, a savoir distinguer :
#   1. NodePort + `minikube service` : minikube ouvre un tunnel et le navigateur
#   2. `kubectl port-forward` : tunnel vers UN pod. Marche partout, meme a
#      distance. C'est l'outil de debug universel - et c'est celui qui casse
#      quand le pod redemarre.
#   3. Ingress + `minikube tunnel` : le plus proche de la production.
# =============================================================================
$ErrorActionPreference = "Continue"

# Nom, port local, cible kubectl, namespace, URL a afficher
$tunnels = @(
  @{ Nom = "grafana"; Local = 3000;  Cible = "svc/grafana"; Ns = "observability"; Distant = 3000 }
  @{ Nom = "alloy";   Local = 12345; Cible = "svc/alloy";   Ns = "observability"; Distant = 12345 }
  @{ Nom = "api";     Local = 8080;  Cible = "svc/orders-api"; Ns = "apps";       Distant = 8080 }
  @{ Nom = "mimir";   Local = 9009;  Cible = "svc/mimir";   Ns = "observability"; Distant = 8080 }
  @{ Nom = "loki";    Local = 3100;  Cible = "svc/loki";    Ns = "observability"; Distant = 3100 }
)

function Start-Tunnel($t) {
  Start-Job -Name $t.Nom -ScriptBlock {
    param($ns, $cible, $local, $distant)
    # --address 127.0.0.1 : on n'expose PAS le tunnel sur le reseau local.
    kubectl -n $ns port-forward $cible "${local}:${distant}" --address 127.0.0.1
  } -ArgumentList $t.Ns, $t.Cible, $t.Local, $t.Distant
}

Write-Host "=== Ouverture des tunnels ===" -ForegroundColor Cyan
# On nettoie d'eventuels jobs d'une execution precedente.
Get-Job -ErrorAction SilentlyContinue | Where-Object { $_.Name -in $tunnels.Nom } |
  ForEach-Object { Stop-Job $_ -ErrorAction SilentlyContinue; Remove-Job $_ -Force -ErrorAction SilentlyContinue }

foreach ($t in $tunnels) { Start-Tunnel $t | Out-Null }
Start-Sleep -Seconds 4

Write-Host ""
Write-Host "Interfaces disponibles :" -ForegroundColor Green
Write-Host "  Grafana      http://localhost:3000        (admin / admin)"
Write-Host "  UI Alloy     http://localhost:12345       <-- LE graphe de collecte"
Write-Host "  API          http://localhost:8080/api/orders"
Write-Host "  Actuator     http://localhost:8080/actuator/prometheus"
Write-Host "  Mimir        http://localhost:9009/prometheus/api/v1/query?query=up"
Write-Host "  Loki         http://localhost:3100/ready"
Write-Host ""
Write-Host "CONSEIL : ouvrez l'UI Alloy en PREMIER. Si la collecte ne marche pas," -ForegroundColor Yellow
Write-Host "ca se voit la, pas dans un dashboard vide." -ForegroundColor Yellow
Write-Host ""
Write-Host "Surveillance active : tout tunnel tombe est relance." -ForegroundColor DarkGray
Write-Host "Ctrl+C pour tout fermer." -ForegroundColor DarkGray
Write-Host ""

$dernierEtat = @{}
try {
  while ($true) {
    foreach ($t in $tunnels) {
      $job = Get-Job -Name $t.Nom -ErrorAction SilentlyContinue
      # Un port-forward sain reste en Running. Tout autre etat = il est tombe.
      if (-not $job -or $job.State -ne "Running") {
        if ($job) { Remove-Job $job -Force -ErrorAction SilentlyContinue }
        Start-Tunnel $t | Out-Null
        Write-Host ("[{0}] tunnel '{1}' relance" -f (Get-Date -Format "HH:mm:ss"), $t.Nom) -ForegroundColor Yellow
        $dernierEtat[$t.Nom] = "relance"
      }
      elseif ($dernierEtat[$t.Nom] -eq "relance") {
        Write-Host ("[{0}] tunnel '{1}' retabli" -f (Get-Date -Format "HH:mm:ss"), $t.Nom) -ForegroundColor Green
        $dernierEtat[$t.Nom] = "ok"
      }
    }
    Start-Sleep -Seconds 5
  }
}
finally {
  Write-Host "`nFermeture des tunnels..." -ForegroundColor Cyan
  Get-Job -ErrorAction SilentlyContinue | Where-Object { $_.Name -in $tunnels.Nom } |
    ForEach-Object { Stop-Job $_ -ErrorAction SilentlyContinue; Remove-Job $_ -Force -ErrorAction SilentlyContinue }
}
