# Dimensions de production : trois zones, NAT par zone, base Multi-AZ, cache répliqué, protection contre la suppression.
# Aucun secret ici. account_id et image viennent de la CD (TF_VAR_account_id, TF_VAR_image).
environment    = "production"
spring_profile = "prod"

# À adapter au domaine réel (zone Route 53 déléguée dans le compte production)
hosted_zone_name      = "example.com"
domain_name           = "shop.example.com"
cognito_domain_prefix = "ecom-shop"

vpc_cidr           = "10.20.0.0/16"
az_count           = 3
single_nat_gateway = false

db_instance_class        = "db.t4g.medium"
db_multi_az              = true
db_backup_retention_days = 30
cache_node_type          = "cache.t4g.small"
cache_replicas           = 1

task_cpu    = 1024
task_memory = 2048
min_tasks   = 2
max_tasks   = 8

# Passage en live à l'étape 10, une fois le secret PayPal renseigné
paypal_enabled  = false
paypal_base_url = "https://api-m.paypal.com"

deletion_protection = true
alert_emails        = []
# Prévenues de chaque demande de devis (vérification SES à confirmer tant que le compte est en sandbox)
quote_notification_emails = []
enable_managed_grafana    = false
log_retention_days        = 90
