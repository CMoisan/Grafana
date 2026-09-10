# =============================================================================
# EXERCICE 06 - MESURER AVANT DE COUPER
# =============================================================================
# L'outil central de l'exercice. A lancer AVANT toute modification, puis apres
# chaque levier, pour construire le tableau avant/apres.
#
# La regle du metier : on ne coupe jamais sans avoir mesure, et on ne se vante
# jamais d'une reduction qu'on n'a pas chiffree.
# =============================================================================
param([string]$Etiquette = "mesure")

$ErrorActionPreference = "Continue"
$q = "http://mimir.observability.svc.cluster.local:8080/prometheus/api/v1/query"

function Interroger([string]$Requete) {
  $enc = [System.Uri]::EscapeDataString($Requete)
  $nom = "m" + (Get-Random -Maximum 99999)
  $brut = kubectl -n observability run $nom --rm -i --restart=Never `
            --image=curlimages/curl:8.10.1 --command -- curl -s "$q`?query=$enc" 2>&1 | Out-String
  if ($brut -match '"value":\[[0-9.]+,"([0-9.e+-]+)"\]') { return [double]$Matches[1] }
  return $null
}

Write-Host "=== Mesure : $Etiquette ===" -ForegroundColor Cyan
Write-Host "  (chaque requete lance un pod jetable, comptez ~10 s par ligne)" -ForegroundColor DarkGray
Write-Host ""

$mesures = [ordered]@{
  "Series actives (total)"          = 'count({__name__=~".+"})'
  "  dont cAdvisor"                 = 'count({__name__=~"container_.+"})'
  "  dont JVM"                      = 'count({__name__=~"jvm_.+"})'
  "  dont HTTP applicatif"          = 'count({__name__=~"http_server_.+"})'
  "  dont metier (orders_)"         = 'count({__name__=~"orders_.+"})'
  "Echantillons ingeres /s"         = 'sum(rate(prometheus_remote_storage_samples_total[5m]))'
  "Lignes de log /s"                = 'sum(rate(loki_distributor_lines_received_total[5m]))'
  "Octets de log /s"                = 'sum(rate(loki_distributor_bytes_received_total[5m]))'
}

$resultats = [ordered]@{}
foreach ($k in $mesures.Keys) {
  $v = Interroger $mesures[$k]
  $resultats[$k] = $v
  if ($null -eq $v) { Write-Host ("  {0,-30} n/a" -f $k) -ForegroundColor DarkGray }
  else { Write-Host ("  {0,-30} {1,12:N0}" -f $k, $v) -ForegroundColor White }
}

# Projection de cout, a l'echelle d'un client
$series = $resultats["Series actives (total)"]
if ($series) {
  Write-Host ""
  Write-Host "  Projection a l'echelle d'un client :" -ForegroundColor Yellow
  Write-Host ("    x100  -> {0,12:N0} series actives" -f ($series * 100))
  Write-Host ("    x1000 -> {0,12:N0} series actives" -f ($series * 1000))
  Write-Host "    (c'est ce raisonnement qui parle a un directeur financier," -ForegroundColor DarkGray
  Write-Host "     pas le nombre brut de votre lab)" -ForegroundColor DarkGray
}

# Journalisation, pour construire le tableau avant/apres
$journal = Join-Path $PSScriptRoot "mesures.csv"
if (-not (Test-Path $journal)) { "horodatage;etiquette;metrique;valeur" | Out-File $journal -Encoding utf8 }
$ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
foreach ($k in $resultats.Keys) {
  "$ts;$Etiquette;$($k.Trim());$($resultats[$k])" | Out-File $journal -Append -Encoding utf8
}
Write-Host ""
Write-Host "  Consigne dans mesures.csv" -ForegroundColor DarkGray
