variable "name" {
  description = "Préfixe des secrets, ex. ecom-staging."
  type        = string
}

variable "kms_key_arn" {
  type = string
}
