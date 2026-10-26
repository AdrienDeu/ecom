variable "environment" {
  description = "Environnement du compte AWS : staging ou production (un compte par environnement)."
  type        = string

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment doit valoir staging ou production."
  }
}

variable "account_id" {
  description = "Compte AWS de l'environnement : le provider refuse tout autre compte."
  type        = string
}

variable "region" {
  description = "Région AWS principale."
  type        = string
  default     = "eu-west-3"
}

variable "github_repository" {
  description = "Dépôt GitHub autorisé à assumer les rôles (owner/name)."
  type        = string
  default     = "AdrienDeu/ecom"
}
