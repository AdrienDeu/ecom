terraform {
  # Bucket créé par infra/bootstrap dans le compte de l'environnement ; son nom contient l'id du compte :
  #   tofu init -backend-config="bucket=ecom-tofu-state-<account-id>"
  backend "s3" {
    key          = "production/terraform.tfstate"
    region       = "eu-west-3"
    encrypt      = true
    use_lockfile = true
  }

  # Chiffrement côté client : l'état et les plans sont illisibles sans la clé KMS, même pour qui lit le bucket
  encryption {
    key_provider "aws_kms" "state" {
      kms_key_id = "alias/ecom-tofu-state"
      region     = "eu-west-3"
      key_spec   = "AES_256"
    }

    method "aes_gcm" "state" {
      keys = key_provider.aws_kms.state
    }

    state {
      method   = method.aes_gcm.state
      enforced = true
    }

    plan {
      method   = method.aes_gcm.state
      enforced = true
    }
  }
}
