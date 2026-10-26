variable "name" {
  description = "Préfixe des ressources, ex. ecom-staging."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  description = "Sous-réseaux de l'ALB."
  type        = list(string)
}

variable "app_subnet_ids" {
  description = "Sous-réseaux privés des tâches."
  type        = list(string)
}

variable "app_security_group_id" {
  description = "Groupe de sécurité des tâches, créé par l'appelant (la base et le cache l'autorisent)."
  type        = string
}

variable "origin_domain_name" {
  description = "Nom DNS de l'ALB, joint uniquement par CloudFront, ex. origin.shop.example.com."
  type        = string
}

variable "zone_id" {
  type = string
}

variable "origin_verify_secret" {
  description = "Valeur de l'en-tête X-Origin-Verify ajouté par CloudFront : l'ALB refuse les requêtes sans."
  type        = string
  sensitive   = true
}

variable "image" {
  description = "Image de l'application, référence complète (dépôt:tag ou dépôt@digest)."
  type        = string
}

variable "cpu" {
  description = "Unités CPU de la tâche (1024 = 1 vCPU)."
  type        = number
  default     = 1024
}

variable "memory" {
  description = "Mémoire de la tâche en Mio."
  type        = number
  default     = 2048
}

variable "min_tasks" {
  type    = number
  default = 2
}

variable "max_tasks" {
  type    = number
  default = 6
}

variable "environment" {
  description = "Variables d'environnement de l'application."
  type        = map(string)
}

variable "secrets" {
  description = "Variables d'environnement lues dans Secrets Manager au démarrage de la tâche : nom → ARN[:clé-json::]."
  type        = map(string)
}

variable "secret_arns" {
  description = "Secrets lisibles par le rôle d'exécution (ARN sans suffixe de clé)."
  type        = list(string)
}

variable "kms_key_arn" {
  description = "Clé des secrets et des journaux."
  type        = string
}

variable "prometheus_remote_write_url" {
  description = "Endpoint remote_write d'Amazon Managed Prometheus."
  type        = string
}

variable "prometheus_workspace_arn" {
  type = string
}

variable "adot_image" {
  description = "Collecteur ADOT : scrape /actuator/prometheus et relaie les traces OTLP vers X-Ray."
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.50.0"
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "enable_execute_command" {
  description = "ECS Exec (shell dans un conteneur) pour le dépannage."
  type        = bool
  default     = false
}

variable "deletion_protection" {
  description = "Protection contre la suppression de l'ALB."
  type        = bool
  default     = true
}
