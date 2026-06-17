output "namespace" {
  description = "Namespace criado para a aplicação."
  value       = kubernetes_namespace.oficina.metadata[0].name
}

output "api_service_name" {
  description = "Service interno da API."
  value       = kubernetes_service.api.metadata[0].name
}

output "postgres_service_name" {
  description = "Service interno do PostgreSQL."
  value       = kubernetes_service.postgres.metadata[0].name
}
