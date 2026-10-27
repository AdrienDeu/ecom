variable "name" {
  description = "Préfixe des ressources, ex. ecom-staging."
  type        = string
}

variable "domain_name" {
  description = "Domaine public de la boutique, ex. shop.example.com."
  type        = string
}

variable "zone_id" {
  description = "Zone Route 53 qui contient domain_name."
  type        = string
}

variable "rate_limit_per_ip" {
  description = "Requêtes autorisées par IP sur 5 minutes, toutes routes confondues."
  type        = number
  default     = 2000
}

variable "payment_rate_limit_per_ip" {
  description = "Requêtes autorisées par IP sur 5 minutes vers /api/payments (anti test de cartes, anti abus)."
  type        = number
  default     = 100
}

variable "telemetry_rate_limit_per_ip" {
  description = "Requêtes autorisées par IP sur 5 minutes vers /api/telemetry (endpoint public de pages vues)."
  type        = number
  default     = 300
}

variable "quote_rate_limit_per_ip" {
  description = "Requêtes autorisées par IP sur 5 minutes vers /api/quote-requests (formulaire public de devis, anti-spam)."
  type        = number
  default     = 10
}

variable "log_retention_days" {
  type    = number
  default = 30
}
