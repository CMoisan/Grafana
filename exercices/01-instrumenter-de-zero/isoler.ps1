# =============================================================================
# EXERCICE 01 - ISOLER L'ENVIRONNEMENT
# =============================================================================
# Retire ce qui donnerait la reponse, et garde ce dont l'exercice a besoin.
#
# CE QUI EST RETIRE
#   - l'API INSTRUMENTEE (orders-api) : sinon deux applications emettent, on ne
#     sait plus lesquelles de vos metriques viennent de votre travail
#   - le dashboard RED deja provisionne : c'est la tache 6, la voir toute faite
#     vide l'exercice de son interet
#
# CE QUI EST GARDE
#   - Mimir, Loki, Alloy, Grafana : l'exercice en depend entierement
#
# Reversible : .\restaurer.ps1
# =============================================================================
# PIEGE POWERSHELL 5.1 : avec ErrorActionPreference a "Stop", la moindre ligne
# ecrite sur stderr par un executable natif (kubectl ecrit ses AVERTISSEMENTS
# la-dessus) devient une erreur fatale et interrompt le script, alors que la
# commande a parfaitement reussi. On reste donc en Continue, et on verifie
# explicitement $LASTEXITCODE la ou ca compte.
$ErrorActionPreference = "Continue"
$racine = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Write-Host "=== 1/3 Retrait de l'API instrumentee ===" -ForegroundColor Cyan
kubectl -n apps delete deployment orders-api --ignore-not-found=true
kubectl -n apps delete service orders-api --ignore-not-found=true
Write-Host "  seule l'API depouillee restera dans le namespace apps" -ForegroundColor Green

Write-Host "`n=== 2/3 Retrait du dashboard RED provisionne ===" -ForegroundColor Cyan
# On ne peut pas SUPPRIMER la ConfigMap : le Deployment Grafana la monte avec
# Optional:false, il resterait bloque en ContainerCreating. On la remplace donc
# par un contenu inerte.
$vide = "$env:TEMP\placeholder.json"
'{ "title": "A vous de jouer", "panels": [] }' | Out-File $vide -Encoding utf8
kubectl -n observability create configmap grafana-dashboards --from-file=placeholder.json=$vide `
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n observability rollout restart deployment/grafana | Out-Null
kubectl -n observability rollout status deployment/grafana --timeout=120s
Write-Host "  le dossier 'Orders Lab' sera vide" -ForegroundColor Green

Write-Host "`n=== 3/3 Verification de la stack ===" -ForegroundColor Cyan
# PIEGE : `kubectl wait --for=condition=ready pod -l app=X` attend que TOUS les
# pods correspondants soient prets, y compris ceux en cours de TERMINAISON apres
# un rollout. On interroge donc le controleur, pas les pods.
$ok = $true
$composants = @(
  @{ n = "mimir";   type = "statefulset" }
  @{ n = "loki";    type = "statefulset" }
  @{ n = "alloy";   type = "daemonset"   }
  @{ n = "grafana"; type = "deployment"  }
)
foreach ($c in $composants) {
  kubectl -n observability rollout status "$($c.type)/$($c.n)" --timeout=120s 2>&1 | Out-Null
  if ($LASTEXITCODE -eq 0) { Write-Host ("  {0,-10} [OK]" -f $c.n) -ForegroundColor Green }
  else { Write-Host ("  {0,-10} [ECHEC]" -f $c.n) -ForegroundColor Red; $ok = $false }
}
if (-not $ok) { Write-Host "`nUn composant ne repond pas - voir docs\07-troubleshooting.md" -ForegroundColor Red; exit 1 }

Write-Host "`n=========================================" -ForegroundColor Yellow
Write-Host " Terrain degage pour l'exercice 01." -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "Mimir, Loki, Alloy et Grafana tournent. Aucune application n'emet." -ForegroundColor White
Write-Host "Aucun dashboard. A vous de faire apparaitre les deux." -ForegroundColor White
Write-Host ""
Write-Host "Avant de coder : ecrivez vos 5 questions (tache 1)." -ForegroundColor DarkGray
