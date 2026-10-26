variable "name" {
  description = "Nom du dépôt d'images."
  type        = string
}

variable "kms_key_arn" {
  type = string
}

variable "keep_images" {
  description = "Nombre d'images conservées (les plus récentes), pour pouvoir revenir en arrière."
  type        = number
  default     = 30
}
