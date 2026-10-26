variable "name" {
  description = "Préfixe des ressources, ex. ecom-staging."
  type        = string
}

variable "kms_key_arn" {
  description = "Clé du topic SNS et des journaux (sa politique autorise aps et cloudwatch)."
  type        = string
}

variable "alert_rules" {
  description = "Règles Prometheus (contenu de observability/alerts/ecom.yml), chargées dans Managed Prometheus."
  type        = string
}

variable "alert_emails" {
  description = "Destinataires des alertes (abonnement SNS à confirmer par e-mail)."
  type        = list(string)
  default     = []
}

variable "alb_arn_suffix" {
  type = string
}

variable "target_group_arn_suffix" {
  type = string
}

variable "ecs_cluster_name" {
  type = string
}

variable "ecs_service_name" {
  type = string
}

variable "min_tasks" {
  description = "Nombre minimal de tâches attendu : alarme en dessous."
  type        = number
}

variable "db_instance_id" {
  type = string
}

variable "enable_managed_grafana" {
  description = "Crée un espace Amazon Managed Grafana (exige IAM Identity Center activé dans le compte)."
  type        = bool
  default     = false
}

variable "log_retention_days" {
  type    = number
  default = 30
}
