variable "environment" {
  description = "staging ou production."
  type        = string
}

variable "hosted_zone_name" {
  description = "Zone Route 53 existante (déléguée) qui contient domain_name, ex. example.com."
  type        = string
}

variable "domain_name" {
  description = "Domaine public de la boutique, ex. shop.example.com ou staging.shop.example.com."
  type        = string
}

variable "image" {
  description = "Image de l'application (dépôt ECR:tag), fournie par la CD."
  type        = string
}

variable "spring_profile" {
  description = "Profil Spring de l'application (staging ou prod)."
  type        = string
}

# --- Réseau ---

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "az_count" {
  type    = number
  default = 2
}

variable "single_nat_gateway" {
  type    = bool
  default = true
}

# --- Données ---

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "db_backup_retention_days" {
  type    = number
  default = 7
}

variable "cache_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "cache_replicas" {
  type    = number
  default = 0
}

# --- Application ---

variable "task_cpu" {
  type    = number
  default = 1024
}

variable "task_memory" {
  type    = number
  default = 2048
}

variable "min_tasks" {
  type    = number
  default = 2
}

variable "max_tasks" {
  type    = number
  default = 6
}

variable "paypal_enabled" {
  description = "Active PayPal : le secret <name>/paypal doit alors avoir une valeur (sinon les tâches ne démarrent pas)."
  type        = bool
  default     = false
}

variable "paypal_base_url" {
  type    = string
  default = "https://api-m.sandbox.paypal.com"
}

variable "cognito_domain_prefix" {
  description = "Préfixe du domaine Managed Login Cognito, unique dans la région."
  type        = string
}

# --- Exploitation ---

variable "deletion_protection" {
  description = "Protège la base, le user pool et l'ALB contre la suppression."
  type        = bool
  default     = true
}

variable "quote_notification_emails" {
  description = "Adresses prévenues de chaque demande de devis (aucune : pas d'e-mail)."
  type        = list(string)
  default     = []
}

variable "alert_emails" {
  type    = list(string)
  default = []
}

variable "enable_managed_grafana" {
  type    = bool
  default = false
}

variable "log_retention_days" {
  type    = number
  default = 30
}
