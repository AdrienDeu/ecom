output "app_url" {
  value = local.app_url
}

output "ecr_repository_url" {
  description = "Dépôt où la CD pousse l'image."
  value       = module.registry.repository_url
}

output "spa_bucket" {
  value = module.frontend.bucket
}

output "cloudfront_distribution_id" {
  value = module.frontend.distribution_id
}

output "ecs_cluster_name" {
  value = module.backend.cluster_name
}

output "ecs_service_name" {
  value = module.backend.service_name
}

output "task_definition_arn" {
  description = "Révision de tâche dont la CD attend le déploiement complet."
  value       = module.backend.task_definition_arn
}

output "cognito_user_pool_id" {
  value = module.identity.user_pool_id
}

output "paypal_secret_arn" {
  description = "Secret à renseigner avant d'activer PayPal (clés client_id, client_secret, webhook_id)."
  value       = module.secrets.paypal_secret_arn
}

output "alerts_topic_arn" {
  value = module.observability.alerts_topic_arn
}

output "grafana_url" {
  value = module.observability.grafana_url
}
