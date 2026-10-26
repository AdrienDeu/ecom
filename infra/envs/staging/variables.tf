variable "account_id" {
  description = "Compte AWS de l'environnement (TF_VAR_account_id) : le provider refuse tout autre compte."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id doit être un identifiant de compte AWS (12 chiffres)."
  }
}

variable "image" {
  description = "Image de l'application (dépôt ECR:tag), fournie par la CD (TF_VAR_image)."
  type        = string
}

variable "environment" {
  type = string
}

variable "hosted_zone_name" {
  type = string
}

variable "domain_name" {
  type = string
}

variable "spring_profile" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "az_count" {
  type = number
}

variable "single_nat_gateway" {
  type = bool
}

variable "db_instance_class" {
  type = string
}

variable "db_multi_az" {
  type = bool
}

variable "db_backup_retention_days" {
  type = number
}

variable "cache_node_type" {
  type = string
}

variable "cache_replicas" {
  type = number
}

variable "task_cpu" {
  type = number
}

variable "task_memory" {
  type = number
}

variable "min_tasks" {
  type = number
}

variable "max_tasks" {
  type = number
}

variable "paypal_enabled" {
  type = bool
}

variable "paypal_base_url" {
  type = string
}

variable "cognito_domain_prefix" {
  type = string
}

variable "deletion_protection" {
  type = bool
}

variable "alert_emails" {
  type = list(string)
}

variable "enable_managed_grafana" {
  type = bool
}

variable "log_retention_days" {
  type = number
}
