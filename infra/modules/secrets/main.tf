# Secrets de l'application dont la valeur est saisie hors d'OpenTofu (console ou CLI) : ni le code ni l'état
# ne les contiennent. Clés JSON attendues pour PayPal : client_id, client_secret, webhook_id.
#   aws secretsmanager put-secret-value --secret-id <name>/paypal \
#     --secret-string '{"client_id":"…","client_secret":"…","webhook_id":"…"}'

resource "aws_secretsmanager_secret" "paypal" {
  name        = "${var.name}/paypal"
  description = "Identifiants de l'app PayPal (sandbox en staging, live en production)"
  kms_key_id  = var.kms_key_arn
}
