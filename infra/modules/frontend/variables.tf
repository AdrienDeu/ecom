variable "name" {
  description = "Préfixe des ressources, ex. ecom-staging."
  type        = string
}

variable "domain_name" {
  description = "Domaine public de la boutique, ex. shop.example.com."
  type        = string
}

variable "zone_id" {
  type = string
}

variable "certificate_arn" {
  description = "Certificat ACM en us-east-1 couvrant domain_name."
  type        = string
}

variable "web_acl_arn" {
  description = "WAF (portée CLOUDFRONT)."
  type        = string
}

variable "api_origin_domain_name" {
  description = "Nom DNS de l'ALB vers lequel /api/* est routé."
  type        = string
}

variable "origin_verify_secret" {
  description = "En-tête X-Origin-Verify envoyé à l'ALB."
  type        = string
  sensitive   = true
}

variable "runtime_config" {
  description = "Contenu de /config.json lu par la SPA au démarrage (OIDC)."
  type        = any
}

variable "content_security_policy" {
  description = "En-tête Content-Security-Policy de toutes les réponses."
  type        = string
}

variable "price_class" {
  description = "Points de présence CloudFront utilisés (PriceClass_100 : Europe et Amérique du Nord)."
  type        = string
  default     = "PriceClass_100"
}
