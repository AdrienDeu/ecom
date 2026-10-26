terraform {
  # Même répertoire pour les deux comptes : seul le bucket change.
  #   tofu init -backend-config="bucket=ecom-tofu-state-<account-id>"
  # Premier apply dans un compte (le bucket n'existe pas encore) : état local via backend_override.tf, puis
  # migration dans le bucket créé (voir infra/README.md).
  # Pas de chiffrement côté client ici : la clé KMS est créée par cette configuration et l'état ne contient aucun
  # secret ; il reste chiffré par le bucket (SSE-KMS).
  backend "s3" {
    key          = "bootstrap/terraform.tfstate"
    region       = "eu-west-3"
    encrypt      = true
    use_lockfile = true
  }
}
