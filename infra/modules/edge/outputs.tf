output "certificate_arn" {
  description = "Certificat validé, utilisable par CloudFront."
  value       = aws_acm_certificate_validation.cdn.certificate_arn
}

output "web_acl_arn" {
  value = aws_wafv2_web_acl.this.arn
}
