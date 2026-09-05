# =============================================================================
# 03 - DEPLOIEMENT DE TOUTE LA STACK
# =============================================================================
# ORDRE DE DEPLOIEMENT, ET POURQUOI IL COMPTE :
#   1. namespaces      -> tout le reste en depend
#   2. Mimir et Loki   -> les BACKENDS de stockage doivent exister avant les
#                         producteurs, sinon Alloy passe sa vie a retry
#   3. Alloy           -> le collecteur, une fois les destinations pretes
#   4. Grafana         -> la consultation, en dernier
#   5. l'application   -> une fois la plateforme capable de la voir
# Rien ne casse si vous inversez (k8s est declaratif et converge tout seul),
# mais vous verrez beaucoup d'erreurs transitoires dans les logs.
# =============================================================================
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "=== 1/6 Namespaces ===" -ForegroundColor Cyan
kubectl apply -f "$root\k8s\00-base\namespace.yaml"

Write-Host "`n=== 2/6 Mimir (metriques) ===" -ForegroundColor Cyan
kubectl apply -f "$root\k8s\mimir"

Write-Host "`n=== 3/6 Loki (logs) ===" -ForegroundColor Cyan
kubectl apply -f "$root\k8s\loki"

Write-Host "`nAttente que Mimir et Loki soient prets (jusqu'a 3 min)..." -ForegroundColor DarkGray
# `kubectl wait` est LA bonne facon d'attendre, bien meilleure qu'un Start-Sleep
# arbitraire : il rend la main des que la condition est vraie.
kubectl -n observability wait --for=condition=ready pod -l app=mimir --timeout=180s
kubectl -n observability wait --for=condition=ready pod -l app=loki  --timeout=180s

Write-Host "`n=== 4/6 Alloy (collecteur) ===" -ForegroundColor Cyan
kubectl apply -f "$root\k8s\alloy"
# Force le redemarrage : un `apply` sur une ConfigMap ne redemarre PAS les pods
# qui la montent. Sans ce rollout, vos modifications de config semblent
# ignorees. C'est la source de frustration numero un quand on debute avec Alloy.
kubectl -n observability rollout restart daemonset/alloy 2>$null

Write-Host "`n=== 5/6 Grafana ===" -ForegroundColor Cyan
# Les dashboards sont generes en ConfigMap depuis les fichiers JSON du depot.
# `--dry-run=client -o yaml | kubectl apply -f -` est l'idiome pour rendre un
# `kubectl create` IDEMPOTENT (rejouable sans erreur "already exists").
kubectl -n observability create configmap grafana-dashboards `
  --from-file="$root\grafana\dashboards" `
  --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "$root\k8s\grafana"
kubectl -n observability rollout restart deployment/grafana 2>$null

Write-Host "`n=== 6/6 API de commandes ===" -ForegroundColor Cyan
kubectl apply -f "$root\k8s\api"

# Scripts k6 en ConfigMap (deux ConfigMaps : une plate ne gere pas les sous-dossiers)
kubectl -n apps create configmap k6-scripts `
  --from-file="$root\k6\01-smoke.js" `
  --from-file="$root\k6\02-load.js" `
  --from-file="$root\k6\03-stress.js" `
  --from-file="$root\k6\04-soak.js" `
  --from-file="$root\k6\05-simulation.js" `
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n apps create configmap k6-lib `
  --from-file="$root\k6\lib" `
  --dry-run=client -o yaml | kubectl apply -f -

Write-Host "`nAttente de l'API..." -ForegroundColor DarkGray
kubectl -n apps rollout status deployment/orders-api --timeout=180s

# ============================================================================
# VERIFICATION REELLE - et pas un simple affichage
# ============================================================================
# Un `kubectl get pods` juste apres un deploiement ne prouve RIEN : il attrape
# les pods en ContainerCreating et n'a aucune idee de ce qui va se passer
# ensuite. Annoncer "deploiement termine" a ce moment-la est trompeur.
#
# C'est une erreur qu'on paie cher en demo client : on affiche du vert, on
# passe a la suite, et le dashboard reste vide sans qu'on comprenne pourquoi.
# On attend donc que CHAQUE composant soit reellement pret, et on echoue
# bruyamment si l'un d'eux ne l'est pas.
# ============================================================================
Write-Host "`n=== Verification des composants ===" -ForegroundColor Cyan

$composants = @(
  @{ Nom = "mimir";      Ns = "observability"; Label = "app=mimir" }
  @{ Nom = "loki";       Ns = "observability"; Label = "app=loki" }
  @{ Nom = "alloy";      Ns = "observability"; Label = "app=alloy" }
  @{ Nom = "grafana";    Ns = "observability"; Label = "app=grafana" }
  @{ Nom = "orders-api"; Ns = "apps";          Label = "app=orders-api" }
)

$echecs = @()
foreach ($c in $composants) {
  Write-Host ("  {0,-12} " -f $c.Nom) -NoNewline
  kubectl -n $c.Ns wait --for=condition=ready pod -l $c.Label --timeout=180s 2>&1 | Out-Null
  if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK]" -ForegroundColor Green
  } else {
    Write-Host "[ECHEC]" -ForegroundColor Red
    $echecs += $c
  }
}

if ($echecs.Count -gt 0) {
  Write-Host "`n=== $($echecs.Count) composant(s) en echec ===" -ForegroundColor Red
  foreach ($c in $echecs) {
    Write-Host "`n--- $($c.Nom) : 15 dernieres lignes de log ---" -ForegroundColor Yellow
    kubectl -n $c.Ns logs -l $c.Label --tail=15 2>&1 | Out-String | Write-Host
    Write-Host "--- evenements ---" -ForegroundColor Yellow
    kubectl -n $c.Ns get events --field-selector "involvedObject.kind=Pod" --sort-by=.lastTimestamp 2>&1 |
      Select-Object -Last 5 | Out-String | Write-Host
  }
  Write-Host "`nLe deploiement N'EST PAS termine." -ForegroundColor Red
  Write-Host "Le message de log ci-dessus nomme presque toujours la cause exacte." -ForegroundColor Yellow
  Write-Host "Voir docs\07-troubleshooting.md" -ForegroundColor Yellow
  exit 1
}

Write-Host "`n=== Etat final ===" -ForegroundColor Cyan
kubectl get pods -A -o wide | Select-String "observability|apps"

Write-Host "`nTous les composants sont prets." -ForegroundColor Green
Write-Host "Prochaine etape : .\scripts\04-acces.ps1" -ForegroundColor Yellow
