terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.68.0"
    }
  }
}

provider "aws" {
  region = var.region

  allowed_account_ids = [var.account_id]

  default_tags {
    tags = {
      project      = "ecom"
      env          = var.environment
      "managed-by" = "opentofu"
      stack        = "bootstrap"
    }
  }
}
