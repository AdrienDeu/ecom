# Un environnement complet de la boutique. Les environnements (envs/*) n'appellent que ce module, avec leurs
# propres dimensions : le même code est promu de staging à production.

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_route53_zone" "this" {
  name         = var.hosted_zone_name
  private_zone = false
}

locals {
  name          = "ecom-${var.environment}"
  app_url       = "https://${var.domain_name}"
  origin_domain = "origin.${var.domain_name}"
  region        = data.aws_region.current.region
  account_id    = data.aws_caller_identity.current.account_id
  cognito_host  = "${var.cognito_domain_prefix}.auth.${local.region}.amazoncognito.com"

  paypal_sources = "https://www.paypal.com https://*.paypal.com https://*.paypalobjects.com"
  content_security_policy = join("; ", [
    "default-src 'self'",
    "script-src 'self' ${local.paypal_sources}",
    # Styles des composants Angular injectés dans des balises <style>
    "style-src 'self' 'unsafe-inline'",
    # Images produits : URL externes saisies dans le back-office
    "img-src 'self' data: https:",
    "font-src 'self' data:",
    "connect-src 'self' https://cognito-idp.${local.region}.amazonaws.com https://${local.cognito_host} ${local.paypal_sources}",
    "frame-src ${local.paypal_sources}",
    "form-action 'self' https://${local.cognito_host}",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "object-src 'none'",
    "upgrade-insecure-requests",
  ])
}

# --- Clé KMS de l'environnement (base, cache, secrets, journaux, images, alertes) ---

data "aws_iam_policy_document" "kms" {
  statement {
    sid       = "AccountAdministration"
    actions   = ["kms:*"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${local.account_id}:root"]
    }
  }

  statement {
    sid       = "CloudWatchLogs"
    actions   = ["kms:Encrypt", "kms:Decrypt", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:DescribeKey"]
    resources = ["*"]

    principals {
      type        = "Service"
      identifiers = ["logs.${local.region}.amazonaws.com"]
    }

    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:aws:logs:${local.region}:${local.account_id}:*"]
    }
  }

  # Publication dans le topic SNS chiffré des alertes
  statement {
    sid       = "AlertPublishers"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey*"]
    resources = ["*"]

    principals {
      type        = "Service"
      identifiers = ["aps.amazonaws.com", "cloudwatch.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }
}

resource "aws_kms_key" "this" {
  description             = "${local.name} : données de l'application"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy                  = data.aws_iam_policy_document.kms.json
}

resource "aws_kms_alias" "this" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.this.key_id
}

# --- Réseau ---

module "network" {
  source = "../network"

  name               = local.name
  cidr               = var.vpc_cidr
  az_count           = var.az_count
  single_nat_gateway = var.single_nat_gateway
  kms_key_arn        = aws_kms_key.this.arn
}

# Créé ici et non dans le module backend : la base et le cache l'autorisent, le backend dépend d'eux
resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "Tâches de l'application"
  vpc_id      = module.network.vpc_id
}

#trivy:ignore:AVD-AWS-0104 Sortie Internet nécessaire (API PayPal, JWKS Cognito), via la NAT
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  description       = "Sortie (PayPal, Cognito, services AWS)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# --- Données et secrets ---

module "database" {
  source = "../database"

  name                      = local.name
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.data_subnet_ids
  client_security_group_ids = [aws_security_group.app.id]
  kms_key_arn               = aws_kms_key.this.arn
  instance_class            = var.db_instance_class
  multi_az                  = var.db_multi_az
  backup_retention_days     = var.db_backup_retention_days
  deletion_protection       = var.deletion_protection
}

module "cache" {
  source = "../cache"

  name                      = local.name
  vpc_id                    = module.network.vpc_id
  subnet_ids                = module.network.data_subnet_ids
  client_security_group_ids = [aws_security_group.app.id]
  kms_key_arn               = aws_kms_key.this.arn
  node_type                 = var.cache_node_type
  replicas                  = var.cache_replicas
}

module "secrets" {
  source = "../secrets"

  name        = local.name
  kms_key_arn = aws_kms_key.this.arn
}

module "registry" {
  source = "../registry"

  name        = "ecom"
  kms_key_arn = aws_kms_key.this.arn
}

module "identity" {
  source = "../identity"

  name                = local.name
  app_url             = local.app_url
  domain_prefix       = var.cognito_domain_prefix
  deletion_protection = var.deletion_protection
}

module "observability" {
  source = "../observability"

  name                    = local.name
  kms_key_arn             = aws_kms_key.this.arn
  alert_rules             = file("${path.module}/../../../observability/alerts/ecom.yml")
  alert_emails            = var.alert_emails
  alb_arn_suffix          = module.backend.alb_arn_suffix
  target_group_arn_suffix = module.backend.target_group_arn_suffix
  ecs_cluster_name        = module.backend.cluster_name
  ecs_service_name        = module.backend.service_name
  min_tasks               = var.min_tasks
  db_instance_id          = module.database.instance_id
  enable_managed_grafana  = var.enable_managed_grafana
  log_retention_days      = var.log_retention_days
}

# --- Application ---

# Connu de CloudFront et de l'ALB seulement : l'ALB refuse toute requête qui ne vient pas de la distribution
resource "random_password" "origin_verify" {
  length  = 48
  special = false
}

locals {
  paypal_secret = module.secrets.paypal_secret_arn

  app_environment = {
    SPRING_PROFILES_ACTIVE              = var.spring_profile
    ECOM_ENVIRONMENT                    = var.environment
    SPRING_DATASOURCE_URL               = module.database.jdbc_url
    SPRING_DATA_REDIS_HOST              = module.cache.primary_endpoint
    SPRING_DATA_REDIS_PORT              = tostring(module.cache.port)
    SPRING_DATA_REDIS_SSL_ENABLED       = "true"
    ECOM_SECURITY_ISSUER_URI            = module.identity.issuer_uri
    ECOM_SECURITY_CLIENT_ID             = module.identity.spa_client_id
    PAYPAL_ENABLED                      = tostring(var.paypal_enabled)
    PAYPAL_BASE_URL                     = var.paypal_base_url
    SERVER_FORWARD_HEADERS_STRATEGY     = "native"
    ECOM_NOTIFICATIONS_TRANSPORT        = "ses"
    ECOM_NOTIFICATIONS_FROM             = module.email.from_address
    ECOM_NOTIFICATIONS_QUOTE_RECIPIENTS = join(",", var.quote_notification_emails)
  }

  app_secrets = merge(
    {
      SPRING_DATASOURCE_USERNAME = "${module.database.master_user_secret_arn}:username::"
      SPRING_DATASOURCE_PASSWORD = "${module.database.master_user_secret_arn}:password::"
      SPRING_DATA_REDIS_PASSWORD = module.cache.auth_token_secret_arn
    },
    var.paypal_enabled ? {
      PAYPAL_CLIENT_ID     = "${local.paypal_secret}:client_id::"
      PAYPAL_CLIENT_SECRET = "${local.paypal_secret}:client_secret::"
      PAYPAL_WEBHOOK_ID    = "${local.paypal_secret}:webhook_id::"
    } : {},
  )
}

module "backend" {
  source = "../backend"

  name                        = local.name
  vpc_id                      = module.network.vpc_id
  public_subnet_ids           = module.network.public_subnet_ids
  app_subnet_ids              = module.network.app_subnet_ids
  app_security_group_id       = aws_security_group.app.id
  origin_domain_name          = local.origin_domain
  zone_id                     = data.aws_route53_zone.this.zone_id
  origin_verify_secret        = random_password.origin_verify.result
  image                       = var.image
  cpu                         = var.task_cpu
  memory                      = var.task_memory
  min_tasks                   = var.min_tasks
  max_tasks                   = var.max_tasks
  environment                 = local.app_environment
  secrets                     = local.app_secrets
  secret_arns                 = [module.database.master_user_secret_arn, module.cache.auth_token_secret_arn, local.paypal_secret]
  ses_identity_arns           = module.email.identity_arns
  kms_key_arn                 = aws_kms_key.this.arn
  prometheus_remote_write_url = module.observability.prometheus_remote_write_url
  prometheus_workspace_arn    = module.observability.prometheus_workspace_arn
  log_retention_days          = var.log_retention_days
  deletion_protection         = var.deletion_protection
}

# --- Bordure (us-east-1) et SPA ---

# --- E-mails de notification (SES) ---

module "email" {
  source = "../email"

  domain_name      = var.domain_name
  zone_id          = data.aws_route53_zone.this.zone_id
  recipient_emails = var.quote_notification_emails
}

module "edge" {
  source = "../edge"

  providers = {
    aws = aws.us_east_1
  }

  name               = local.name
  domain_name        = var.domain_name
  zone_id            = data.aws_route53_zone.this.zone_id
  log_retention_days = var.log_retention_days
}

module "frontend" {
  source = "../frontend"

  name                    = local.name
  domain_name             = var.domain_name
  zone_id                 = data.aws_route53_zone.this.zone_id
  certificate_arn         = module.edge.certificate_arn
  web_acl_arn             = module.edge.web_acl_arn
  api_origin_domain_name  = local.origin_domain
  origin_verify_secret    = random_password.origin_verify.result
  content_security_policy = local.content_security_policy

  runtime_config = {
    oidc = {
      authority = module.identity.issuer_uri
      clientId  = module.identity.spa_client_id
      scope     = "openid profile email"
      logoutUrl = module.identity.logout_url
    }
  }
}
