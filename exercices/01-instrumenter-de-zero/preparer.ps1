# =============================================================================
# EXERCICE 01 - GENERATION DE L'API DEPOUILLEE
# =============================================================================
# Pourquoi un script plutot qu'une copie versionnee ?
#   Dupliquer 22 fichiers Java dans le depot, c'est garantir qu'ils divergeront
#   de la version de reference a la premiere modification. Le script regenere la
#   variante a la demande, toujours a partir de la source actuelle.
#
# Ce qu'il RETIRE (c'est ce que vous devrez remettre) :
#   - la dependance micrometer-registry-prometheus
#   - la classe ObservabilityConfig (histogrammes, common tags, garde-fou)
#   - l'adaptateur MicrometerOrderMetrics, remplace par une implementation vide
#   - l'exposition /actuator/prometheus
#   - les annotations prometheus.io du pod, sans lesquelles Alloy ignore l'appli
#
# Ce qu'il GARDE volontairement :
#   - les logs JSON : l'exercice porte sur les METRIQUES
#   - /actuator/health : les sondes k8s en dependent
#   - le port OrderMetrics : le service metier reste inchange, c'est le principe
#     de l'architecture hexagonale. Seul l'ADAPTATEUR disparait.
# =============================================================================
$ErrorActionPreference = "Stop"
$racine = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$src    = Join-Path $racine "api"
$dst    = Join-Path $PSScriptRoot "api-nue"

# PowerShell 5.1 ecrit de l'UTF-8 AVEC BOM via Set-Content -Encoding UTF8.
# javac refuse ce BOM ("illegal character: "). On ecrit donc en UTF-8 nu.
function Write-Utf8NoBom([string]$Chemin, [string]$Contenu) {
  [System.IO.File]::WriteAllText($Chemin, $Contenu, (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "=== Generation de l'API depouillee ===" -ForegroundColor Cyan

if (Test-Path $dst) {
  Write-Host "  Suppression de la version precedente..." -ForegroundColor DarkGray
  Remove-Item $dst -Recurse -Force
}
Copy-Item $src $dst -Recurse
Remove-Item (Join-Path $dst "target") -Recurse -Force -ErrorAction SilentlyContinue

# --- 1. pom.xml : retirer le registre Prometheus -----------------------------
$pom = Join-Path $dst "pom.xml"
$t = Get-Content $pom -Raw
$t = $t -replace '(?s)\s*<dependency>\s*<groupId>io\.micrometer</groupId>\s*<artifactId>micrometer-registry-prometheus</artifactId>\s*</dependency>', ''
$t = $t -replace '<artifactId>orders-api</artifactId>', '<artifactId>orders-api-nue</artifactId>'
Write-Utf8NoBom $pom $t

# --- 2. Supprimer les classes d'instrumentation ------------------------------
Remove-Item (Join-Path $dst "src\main\java\com\grafanalab\orders\config\ObservabilityConfig.java") -Force
Remove-Item (Join-Path $dst "src\main\java\com\grafanalab\orders\infrastructure\metrics\MicrometerOrderMetrics.java") -Force

# --- 3. Un adaptateur VIDE, pour que le projet compile encore ----------------
# Le service metier appelle toujours le port OrderMetrics : sans implementation,
# le contexte Spring ne demarre pas. Cette classe ne fait rien, volontairement.
$noop = Join-Path $dst "src\main\java\com\grafanalab\orders\infrastructure\metrics\NoOpOrderMetrics.java"
@'
package com.grafanalab.orders.infrastructure.metrics;

import com.grafanalab.orders.domain.OrderStatus;
import com.grafanalab.orders.service.port.OrderMetrics;

import java.math.BigDecimal;

/**
 * Adaptateur de metriques QUI NE FAIT RIEN.
 *
 * Le service metier appelle le port OrderMetrics a chaque evenement. Sans une
 * implementation, le contexte Spring refuse de demarrer. Celle-ci absorbe donc
 * les appels en silence.
 *
 * VOTRE TRAVAIL : la remplacer par une vraie implementation Micrometer.
 * Remarquez ce que l'architecture hexagonale vous offre ici : le service, le
 * domaine et les controllers n'ont PAS BESOIN d'etre modifies. Toute
 * l'instrumentation metier tient dans cette seule classe.
 */
public class NoOpOrderMetrics implements OrderMetrics {

    @Override public void orderCreated(int itemCount, BigDecimal amount) { }
    @Override public void orderPaid(BigDecimal amount) { }
    @Override public void paymentDeclined(String reason) { }
    @Override public void paymentGatewayFailure() { }
    @Override public void statusTransition(OrderStatus from, OrderStatus to) { }
}
'@ | ForEach-Object { Write-Utf8NoBom $noop $_ }

Write-Host "  [OK] classes d'instrumentation retirees" -ForegroundColor Green

# --- 4. BeanConfiguration : cabler l'adaptateur vide -------------------------
$bean = Join-Path $dst "src\main\java\com\grafanalab\orders\config\BeanConfiguration.java"
$t = Get-Content $bean -Raw
$t = $t -replace 'import com\.grafanalab\.orders\.infrastructure\.metrics\.MicrometerOrderMetrics;', 'import com.grafanalab.orders.infrastructure.metrics.NoOpOrderMetrics;'
$t = $t -replace 'return new MicrometerOrderMetrics\(registry\);', 'return new NoOpOrderMetrics();'
$t = $t -replace 'public OrderMetrics orderMetrics\(MeterRegistry registry\)', 'public OrderMetrics orderMetrics()'
# Les gauges metier dependaient de Micrometer : on retire le bean MeterBinder.
$t = $t -replace '(?s)    // On retourne un MeterBinder.*?\n    \}\n', ''
$t = $t -replace 'import io\.micrometer\.core\.instrument\.Gauge;\r?\n', ''
$t = $t -replace 'import io\.micrometer\.core\.instrument\.binder\.MeterBinder;\r?\n', ''
$t = $t -replace 'import io\.micrometer\.core\.instrument\.MeterRegistry;\r?\n', ''
$t = $t -replace 'import com\.grafanalab\.orders\.domain\.OrderStatus;\r?\n', ''
Write-Utf8NoBom $bean $t

# --- 5. application.yaml : ne plus exposer /actuator/prometheus --------------
$app = Join-Path $dst "src\main\resources\application.yaml"
$t = Get-Content $app -Raw
$t = $t -replace 'include: health,info,prometheus,metrics,loggers', 'include: health,info'
$t = $t -replace '(?s)\n  prometheus:\n    metrics:\n      export:.*?step: 15s\n', "`n"
$t = $t -replace '(?s)\n    distribution:\n      percentiles-histogram:.*?http\.server\.requests: true\n', "`n"
Write-Utf8NoBom $app $t

# --- 6. Le manifeste k8s, SANS les annotations de decouverte ----------------
$k8s = Join-Path $PSScriptRoot "orders-api-nue.yaml"
$m = Get-Content (Join-Path $racine "k8s\api\orders-api.yaml") -Raw
$m = $m -replace 'orders-api', 'orders-api-nue'
$m = $m -replace 'image: orders-api-nue:1\.0\.0', 'image: orders-api-nue:1.0.0'
# Sans ces trois annotations, Alloy ignore le pod : c'est la tache 3 de l'exercice.
$m = $m -replace '(?m)^\s*prometheus\.io/scrape: "true"\r?\n', ''
$m = $m -replace '(?m)^\s*prometheus\.io/port: "8080"\r?\n', ''
$m = $m -replace '(?m)^\s*prometheus\.io/path: "/actuator/prometheus"\r?\n', ''
$m = $m -replace 'nodePort: 30080', 'nodePort: 30081'
Write-Utf8NoBom $k8s $m

Write-Host "  [OK] configuration et manifeste generes" -ForegroundColor Green
Write-Host ""
Write-Host "=== Pret ===" -ForegroundColor Green
Write-Host "  Source depouillee : exercices\01-instrumenter-de-zero\api-nue\"
Write-Host "  Manifeste         : exercices\01-instrumenter-de-zero\orders-api-nue.yaml"
Write-Host ""
Write-Host "Construire et deployer :" -ForegroundColor Yellow
Write-Host "  minikube image build -t orders-api-nue:1.0.0 .\exercices\01-instrumenter-de-zero\api-nue --profile=grafana-lab"
Write-Host "  kubectl apply -f .\exercices\01-instrumenter-de-zero\orders-api-nue.yaml"
Write-Host ""
Write-Host "Puis constatez qu'Alloy ne la voit pas, et que /actuator/prometheus n'existe pas." -ForegroundColor DarkGray
Write-Host "A vous de tout remettre. Ecrivez d'abord vos 5 questions." -ForegroundColor DarkGray
