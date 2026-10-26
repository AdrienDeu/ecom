# Comptes clients et administrateurs. La SPA s'authentifie par Authorization Code + PKCE (client public,
# sans secret) ; le backend valide les jetons d'accès émis pour ce client (issuer, signature, client_id).

data "aws_region" "current" {}

resource "aws_cognito_user_pool" "this" {
  name = var.name

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  username_configuration {
    case_sensitive = false
  }

  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = false
    temporary_password_validity_days = 3
  }

  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  user_attribute_update_settings {
    attributes_require_verification_before_update = ["email"]
  }

  deletion_protection = var.deletion_protection ? "ACTIVE" : "INACTIVE"
}

# Rôles applicatifs : cognito:groups → ROLE_CUSTOMER, ROLE_ADMIN côté backend.
# Un administrateur est ajouté au groupe ADMIN depuis la console ou la CLI.
resource "aws_cognito_user_group" "this" {
  for_each = {
    CUSTOMER = "Clients de la boutique"
    ADMIN    = "Accès au back-office"
  }

  user_pool_id = aws_cognito_user_pool.this.id
  name         = each.key
  description  = each.value
}

resource "aws_cognito_user_pool_domain" "this" {
  user_pool_id          = aws_cognito_user_pool.this.id
  domain                = var.domain_prefix
  managed_login_version = 2
}

resource "aws_cognito_user_pool_client" "spa" {
  name         = "${var.name}-spa"
  user_pool_id = aws_cognito_user_pool.this.id

  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = ["${var.app_url}/auth/callback"]
  logout_urls                          = ["${var.app_url}/"]

  explicit_auth_flows = ["ALLOW_REFRESH_TOKEN_AUTH", "ALLOW_USER_SRP_AUTH"]

  access_token_validity  = var.access_token_minutes
  id_token_validity      = var.access_token_minutes
  refresh_token_validity = var.refresh_token_days

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  enable_token_revocation       = true
  prevent_user_existence_errors = "ENABLED"
}

# Pages de connexion hébergées par Cognito (Managed Login), avec le style par défaut
resource "aws_cognito_managed_login_branding" "spa" {
  user_pool_id                = aws_cognito_user_pool.this.id
  client_id                   = aws_cognito_user_pool_client.spa.id
  use_cognito_provided_values = true
}
