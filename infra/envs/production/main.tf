terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.68.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9.0"
    }
  }
}

locals {
  default_tags = {
    project      = "ecom"
    env          = var.environment
    "managed-by" = "opentofu"
  }
}

provider "aws" {
  region              = "eu-west-3"
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = local.default_tags
  }
}

# CloudFront : certificat et WAF obligatoirement en us-east-1
provider "aws" {
  alias               = "us_east_1"
  region              = "us-east-1"
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = local.default_tags
  }
}

module "platform" {
  source = "../../modules/platform"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  environment               = var.environment
  hosted_zone_name          = var.hosted_zone_name
  domain_name               = var.domain_name
  image                     = var.image
  spring_profile            = var.spring_profile
  vpc_cidr                  = var.vpc_cidr
  az_count                  = var.az_count
  single_nat_gateway        = var.single_nat_gateway
  db_instance_class         = var.db_instance_class
  db_multi_az               = var.db_multi_az
  db_backup_retention_days  = var.db_backup_retention_days
  cache_node_type           = var.cache_node_type
  cache_replicas            = var.cache_replicas
  task_cpu                  = var.task_cpu
  task_memory               = var.task_memory
  min_tasks                 = var.min_tasks
  max_tasks                 = var.max_tasks
  paypal_enabled            = var.paypal_enabled
  paypal_base_url           = var.paypal_base_url
  cognito_domain_prefix     = var.cognito_domain_prefix
  deletion_protection       = var.deletion_protection
  alert_emails              = var.alert_emails
  quote_notification_emails = var.quote_notification_emails
  enable_managed_grafana    = var.enable_managed_grafana
  log_retention_days        = var.log_retention_days
}
