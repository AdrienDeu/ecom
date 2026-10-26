# Valkey (ElastiCache) : paniers des visiteurs. Chiffré au repos et en transit, protégé par un jeton
# d'authentification généré ici et rangé dans Secrets Manager (l'état OpenTofu est lui-même chiffré).

resource "aws_elasticache_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "this" {
  name        = "${var.name}-valkey"
  description = "Valkey depuis les tâches de l'application"
  vpc_id      = var.vpc_id
}

# count plutôt que for_each : les identifiants des groupes ne sont connus qu'après leur création
resource "aws_vpc_security_group_ingress_rule" "clients" {
  count = length(var.client_security_group_ids)

  security_group_id            = aws_security_group.this.id
  description                  = "Valkey depuis l'application"
  referenced_security_group_id = var.client_security_group_ids[count.index]
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}

resource "random_password" "auth_token" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "auth_token" {
  name        = "${var.name}/valkey-auth-token"
  description = "Jeton d'authentification Valkey de l'application"
  kms_key_id  = var.kms_key_arn
}

resource "aws_secretsmanager_secret_version" "auth_token" {
  secret_id     = aws_secretsmanager_secret.auth_token.id
  secret_string = random_password.auth_token.result
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = var.name
  description          = "ecom : paniers"

  engine               = "valkey"
  engine_version       = var.engine_version
  node_type            = var.node_type
  num_cache_clusters   = 1 + var.replicas
  parameter_group_name = "default.valkey${split(".", var.engine_version)[0]}"
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.this.id]

  automatic_failover_enabled = var.replicas > 0
  multi_az_enabled           = var.replicas > 0

  at_rest_encryption_enabled = true
  kms_key_id                 = var.kms_key_arn
  transit_encryption_enabled = true
  auth_token                 = random_password.auth_token.result

  snapshot_retention_limit   = 1
  auto_minor_version_upgrade = true
  maintenance_window         = "sun:04:30-sun:05:30"
  snapshot_window            = "01:00-02:00"
  apply_immediately          = false
}
