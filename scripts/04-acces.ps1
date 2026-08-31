# =============================================================================
# 04 - OUVRIR LES ACCES AUX INTERFACES
# =============================================================================
# TROIS FACONS D'ATTEINDRE UN SERVICE DANS MINIKUBE, A CONNAITRE :
#
# 1. NodePort + `minikube service` : minikube ouvre un tunnel et le navigateur.
#    Le plus simple. C'est ce qu'on utilise ici.
#
# 2. `kubectl port-forward` : cree un tunnel depuis votre poste vers UN pod
#    (ou un service). Fonctionne partout, y compris sur un cluster distant.
#    C'est l'outil de debug universel. Attention : il meurt si le pod redemarre.
#
# 3. Ingress + `minikube tunnel` : le plus proche de la production, mais aussi
#    le plus de configuration. Inutile pour ce lab.
# =============================================================================
$ErrorActionPreference = "Continue"

Write-Host "=== Ouverture des tunnels (port-forward) ===" -ForegroundColor Cyan
Write-Host "Laissez cette fenetre OUVERTE. Ctrl+C pour tout fermer.`n" -ForegroundColor Yellow

# On lance chaque port-forward dans un job PowerShell d'arriere-plan.
$jobs = @()

$jobs += Start-Job -Name "grafana" -ScriptBlock {
  kubectl -n observability port-forward svc/grafana 3000:3000
}
$jobs += Start-Job -Name "api" -ScriptBlock {
  kubectl -n apps port-forward svc/orders-api 8080:8080
}
$jobs += Start-Job -Name "mimir" -ScriptBlock {
  kubectl -n observability port-forward svc/mimir 9009:8080
}
$jobs += Start-Job -Name "loki" -ScriptBlock {
  kubectl -n observability port-forward svc/loki 3100:3100
}
$jobs += Start-Job -Name "alloy" -ScriptBlock {
  kubectl -n observability port-forward svc/alloy 12345:12345
}

Start-Sleep -Seconds 4

Write-Host "Interfaces disponibles :" -ForegroundColor Green
Write-Host "  Grafana      http://localhost:3000        (admin / admin)"
Write-Host "  API          http://localhost:8080/api/orders"
Write-Host "  Actuator     http://localhost:8080/actuator/prometheus"
Write-Host "  UI Alloy     http://localhost:12345        <-- LE graphe des composants"
Write-Host "  Mimir        http://localhost:9009/prometheus/api/v1/query?query=up"
Write-Host "  Loki         http://localhost:3100/ready"
Write-Host ""
Write-Host "CONSEIL : ouvrez l'UI Alloy en premier. Elle montre EN DIRECT les" -ForegroundColor Yellow
Write-Host "cibles decouvertes et les erreurs de chaque composant. C'est le" -ForegroundColor Yellow
Write-Host "premier endroit ou regarder quand 'il n'y a pas de donnees'." -ForegroundColor Yellow
Write-Host ""
Write-Host "Ctrl+C pour arreter." -ForegroundColor DarkGray

try {
  while ($true) { Start-Sleep -Seconds 5 }
} finally {
  Write-Host "`nFermeture des tunnels..." -ForegroundColor Cyan
  $jobs | Stop-Job -ErrorAction SilentlyContinue
  $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
}
