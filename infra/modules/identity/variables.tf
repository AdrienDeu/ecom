variable "name" {
  description = "Préfixe des ressources, ex. ecom-staging."
  type        = string
}

variable "app_url" {
  description = "URL publique de la SPA, ex. https://shop.example.com (callback et déconnexion)."
  type        = string
}

variable "domain_prefix" {
  description = "Préfixe du domaine Managed Login, unique dans la région : <prefix>.auth.<region>.amazoncognito.com."
  type        = string
}

variable "deletion_protection" {
  description = "Empêche la suppression du user pool (et donc des comptes clients)."
  type        = bool
  default     = true
}

variable "access_token_minutes" {
  type    = number
  default = 60
}

variable "refresh_token_days" {
  type    = number
  default = 30
}
