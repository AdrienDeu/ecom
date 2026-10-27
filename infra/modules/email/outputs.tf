output "identity_arns" {
  description = "Identités SES à autoriser dans ses:SendEmail : le domaine et, en sandbox, les destinataires."
  value       = concat([aws_sesv2_email_identity.domain.arn], [for identity in aws_sesv2_email_identity.recipient : identity.arn])
}

output "from_address" {
  description = "Expéditeur des notifications."
  value       = "no-reply@${var.domain_name}"
}
