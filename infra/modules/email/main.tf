# Envoi des notifications de l'application (demandes de devis) par Amazon SES, depuis le domaine de la boutique.
# DKIM signe les e-mails ; tant que le compte SES est en mode sandbox, seuls les destinataires vérifiés reçoivent
# (chaque adresse de var.recipient_emails reçoit un e-mail de vérification à confirmer).

resource "aws_sesv2_email_identity" "domain" {
  email_identity = var.domain_name

  dkim_signing_attributes {
    next_signing_key_length = "RSA_2048_BIT"
  }
}

resource "aws_route53_record" "dkim" {
  count = 3

  zone_id = var.zone_id
  name    = "${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[count.index]}._domainkey.${var.domain_name}"
  type    = "CNAME"
  ttl     = 600
  records = ["${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

resource "aws_sesv2_email_identity" "recipient" {
  for_each = toset(var.recipient_emails)

  email_identity = each.value
}
