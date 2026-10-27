# Dimensions de staging : économique, une seule zone de NAT, pas de haute disponibilité des données.
# Aucun secret ici. account_id et image viennent de la CI (TF_VAR_account_id, TF_VAR_image).
environment    = "staging"
spring_profile = "staging"

# À adapter au domaine réel (zone Route 53 déléguée dans le compte staging)
hosted_zone_name      = "example.com"
domain_name           = "staging.shop.example.com"
cognito_domain_prefix = "ecom-staging-shop"

vpc_cidr           = "10.10.0.0/16"
az_count           = 2
single_nat_gateway = true

db_instance_class        = "db.t4g.micro"
db_multi_az              = false
db_backup_retention_days = 3
cache_node_type          = "cache.t4g.micro"
cache_replicas           = 0

task_cpu    = 512
task_memory = 1024
min_tasks   = 1
max_tasks   = 2

paypal_enabled  = false
paypal_base_url = "https://api-m.sandbox.paypal.com"

deletion_protection = false
alert_emails        = []
# Prévenues de chaque demande de devis (vérification SES à confirmer tant que le compte est en sandbox)
quote_notification_emails = []
enable_managed_grafana    = false
log_retention_days        = 14
