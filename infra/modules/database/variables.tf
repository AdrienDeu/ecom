variable "name" {
  description = "Préfixe des ressources, ex. ecom-staging."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Sous-réseaux de données (sans accès Internet)."
  type        = list(string)
}

variable "client_security_group_ids" {
  description = "Groupes de sécurité autorisés à se connecter (tâches ECS)."
  type        = list(string)
}

variable "kms_key_arn" {
  description = "Clé KMS du stockage, des sauvegardes et du secret du mot de passe."
  type        = string
}

variable "engine_version" {
  description = "Version majeure de PostgreSQL (mises à jour mineures automatiques)."
  type        = string
  default     = "18"
}

variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "max_allocated_storage_gb" {
  description = "Plafond de l'autoscaling du stockage."
  type        = number
  default     = 100
}

variable "multi_az" {
  description = "Instance de secours synchrone dans une autre zone (production)."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  type    = number
  default = 7
}

variable "deletion_protection" {
  description = "Interdit la suppression de l'instance ; un instantané final est pris dans tous les cas."
  type        = bool
  default     = true
}
