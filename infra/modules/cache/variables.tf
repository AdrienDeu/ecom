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
  description = "Clé KMS du chiffrement au repos et du secret du jeton d'authentification."
  type        = string
}

variable "engine_version" {
  type    = string
  default = "8.1"
}

variable "node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "replicas" {
  description = "Réplicas en plus du primaire ; au moins 1 pour le basculement automatique multi-zones."
  type        = number
  default     = 0
}
