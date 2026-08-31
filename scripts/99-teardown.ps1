# =============================================================================
# 99 - NETTOYAGE
# =============================================================================
param([switch]$Full)

if ($Full) {
  Write-Host "Suppression COMPLETE du cluster minikube..." -ForegroundColor Red
  minikube delete --profile=grafana-lab
  Write-Host "Cluster supprime." -ForegroundColor Green
}
else {
  Write-Host "Suppression des namespaces (le cluster reste debout)..." -ForegroundColor Yellow
  # Supprimer un namespace supprime en cascade TOUT ce qu'il contient,
  # sauf les objets cluster-scoped : ClusterRole et ClusterRoleBinding
  # doivent etre supprimes explicitement. Oubli tres frequent.
  kubectl delete namespace observability apps --ignore-not-found=true
  kubectl delete clusterrole alloy --ignore-not-found=true
  kubectl delete clusterrolebinding alloy --ignore-not-found=true
  Write-Host "Namespaces supprimes. `minikube` tourne toujours." -ForegroundColor Green
  Write-Host "Pour tout supprimer : .\scripts\99-teardown.ps1 -Full" -ForegroundColor DarkGray
}
