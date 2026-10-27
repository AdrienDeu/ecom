variable "domain_name" {
  description = "Domaine d'envoi (celui de la boutique), dont la zone Route 53 est zone_id."
  type        = string
}

variable "zone_id" {
  description = "Zone Route 53 du domaine, pour les enregistrements DKIM."
  type        = string
}

variable "recipient_emails" {
  description = "Destinataires des notifications, vérifiés pour le mode sandbox de SES."
  type        = list(string)
  default     = []
}
