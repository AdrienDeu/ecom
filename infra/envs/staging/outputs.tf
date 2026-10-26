output "app_url" {
  value = module.platform.app_url
}

output "ecr_repository_url" {
  value = module.platform.ecr_repository_url
}

output "spa_bucket" {
  value = module.platform.spa_bucket
}

output "cloudfront_distribution_id" {
  value = module.platform.cloudfront_distribution_id
}

output "ecs_cluster_name" {
  value = module.platform.ecs_cluster_name
}

output "ecs_service_name" {
  value = module.platform.ecs_service_name
}

output "task_definition_arn" {
  value = module.platform.task_definition_arn
}

output "cognito_user_pool_id" {
  value = module.platform.cognito_user_pool_id
}

output "paypal_secret_arn" {
  value = module.platform.paypal_secret_arn
}

output "alerts_topic_arn" {
  value = module.platform.alerts_topic_arn
}

output "grafana_url" {
  value = module.platform.grafana_url
}

# Relue par le tofu plan des PR : le plan reprend l'image déployée au lieu d'annoncer une nouvelle révision de tâche
output "image" {
  value = var.image
}
