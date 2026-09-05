# =============================================================================
# 06 - QU'EST-CE QUE MES MANIFESTES REFERENCENT SANS LE DECLARER ?
# =============================================================================
# LE PROBLEME QU'IL RESOUT
#   Un Deployment MONTE une ConfigMap ou un Secret sans forcement les CREER.
#   Si l'objet n'existe pas, le pod reste bloque en ContainerCreating,
#   INDEFINIMENT et SANS AUCUN LOG — puisque le conteneur n'a jamais demarre.
#   `kubectl logs` ne renvoie rien, ce qui est tres deroutant la premiere fois.
#
# QUAND S'EN SERVIR
#   Avant tout deploiement sur un environnement neuf, et surtout quand on
#   reprend les manifestes de quelqu'un d'autre — c'est-a-dire tout le temps,
#   chez un client.
#
# LIMITE ASSUMEE
#   Ce script fait de l'analyse textuelle, pas du parsing YAML. Il couvre les
#   formes utilisees dans ce depot. Pour un cas reel plus tordu, l'outil de
#   reference est `kubeconform` ou une lecture attentive des blocs `volumes:`.
# =============================================================================
param([string]$Dossier = "k8s")

$racine = Join-Path (Split-Path -Parent $PSScriptRoot) $Dossier
if (-not (Test-Path $racine)) { Write-Host "Dossier introuvable : $racine" -ForegroundColor Red; exit 1 }

$fichiers = Get-ChildItem $racine -Recurse -Include *.yaml, *.yml

# --- Ce qui est DECLARE : un document dont le kind vaut ConfigMap ou Secret ---
$declares = @{}
foreach ($f in $fichiers) {
  $lignes = Get-Content $f.FullName
  for ($i = 0; $i -lt $lignes.Count; $i++) {
    if ($lignes[$i] -match '^\s*kind:\s*(ConfigMap|Secret)\s*$') {
      $kind = $Matches[1]
      # Le nom se trouve dans le bloc metadata qui suit, a quelques lignes.
      for ($j = $i; $j -lt [Math]::Min($i + 8, $lignes.Count); $j++) {
        if ($lignes[$j] -match '^\s{2}name:\s*(\S+)') { $declares["$kind/$($Matches[1])"] = $true; break }
      }
    }
  }
}

# --- Ce qui est REFERENCE : monte par un volume, ou injecte en variable ---
$references = @{}
foreach ($f in $fichiers) {
  $texte = Get-Content $f.FullName -Raw
  foreach ($m in [regex]::Matches($texte, 'configMap:\s*\{?\s*name:\s*([\w.-]+)'))    { $references["ConfigMap/$($m.Groups[1].Value)"] = $f.Name }
  foreach ($m in [regex]::Matches($texte, 'configMapRef:\s*\{?\s*name:\s*([\w.-]+)')) { $references["ConfigMap/$($m.Groups[1].Value)"] = $f.Name }
  foreach ($m in [regex]::Matches($texte, 'configMapKeyRef:\s*\{?\s*name:\s*([\w.-]+)')) { $references["ConfigMap/$($m.Groups[1].Value)"] = $f.Name }
  foreach ($m in [regex]::Matches($texte, 'secret:\s*\{?\s*secretName:\s*([\w.-]+)'))  { $references["Secret/$($m.Groups[1].Value)"] = $f.Name }
  foreach ($m in [regex]::Matches($texte, 'secretRef:\s*\{?\s*name:\s*([\w.-]+)'))     { $references["Secret/$($m.Groups[1].Value)"] = $f.Name }
  foreach ($m in [regex]::Matches($texte, 'secretKeyRef:\s*\{?\s*name:\s*([\w.-]+)'))  { $references["Secret/$($m.Groups[1].Value)"] = $f.Name }
}

Write-Host "=== Declares dans $Dossier ===" -ForegroundColor Cyan
$declares.Keys | Sort-Object | ForEach-Object { Write-Host "  $_" }

$manquants = $references.Keys | Where-Object { -not $declares.ContainsKey($_) } | Sort-Object

Write-Host ""
if ($manquants.Count -eq 0) {
  Write-Host "Tout ce qui est reference est declare." -ForegroundColor Green
} else {
  Write-Host "=== REFERENCES MAIS NON DECLARES ===" -ForegroundColor Yellow
  Write-Host "Ces objets doivent etre crees AUTREMENT, sinon le pod restera" -ForegroundColor Yellow
  Write-Host "bloque en ContainerCreating, sans aucun log." -ForegroundColor Yellow
  Write-Host ""
  foreach ($m in $manquants) { Write-Host ("  {0,-32} reference par {1}" -f $m, $references[$m]) -ForegroundColor Red }
  Write-Host ""
  Write-Host "Dans ce depot, les trois sont generes par 03-deploy.ps1 avec" -ForegroundColor DarkGray
  Write-Host "'kubectl create configmap --from-file', parce que leur contenu est" -ForegroundColor DarkGray
  Write-Host "fait de fichiers entiers (605 lignes de JSON pour le dashboard)." -ForegroundColor DarkGray
}
