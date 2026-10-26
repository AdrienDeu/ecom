variable "name" {
  description = "Préfixe des ressources, ex. ecom-staging."
  type        = string
}

variable "cidr" {
  description = "Plage d'adresses du VPC (/16 attendu : un /24 par sous-réseau)."
  type        = string
}

variable "az_count" {
  description = "Nombre de zones de disponibilité (2 ou 3)."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count doit valoir 2 ou 3."
  }
}

variable "single_nat_gateway" {
  description = "Une seule NAT gateway (économique, hors production) au lieu d'une par zone."
  type        = bool
  default     = false
}

variable "interface_endpoints" {
  description = "Services joints par des VPC endpoints d'interface, sans passer par la NAT (ECR, Secrets Manager, logs…)."
  type        = list(string)
  default     = ["ecr.api", "ecr.dkr", "secretsmanager", "logs", "sts"]
}

variable "kms_key_arn" {
  description = "Clé KMS des journaux de flux."
  type        = string
}

variable "flow_logs_retention_days" {
  description = "Rétention des VPC flow logs."
  type        = number
  default     = 30
}
