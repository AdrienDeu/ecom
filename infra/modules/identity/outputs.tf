output "user_pool_id" {
  value = aws_cognito_user_pool.this.id
}

output "issuer_uri" {
  description = "Issuer des jetons, à fournir au backend (ECOM_SECURITY_ISSUER_URI) et à la SPA (authority)."
  value       = "https://cognito-idp.${data.aws_region.current.region}.amazonaws.com/${aws_cognito_user_pool.this.id}"
}

output "spa_client_id" {
  value = aws_cognito_user_pool_client.spa.id
}

output "logout_url" {
  description = "Cognito ne publie pas d'end_session_endpoint : URL de déconnexion explicite pour la SPA."
  value = format("https://%s.auth.%s.amazoncognito.com/logout?client_id=%s&logout_uri=%s",
    aws_cognito_user_pool_domain.this.domain, data.aws_region.current.region,
  aws_cognito_user_pool_client.spa.id, urlencode("${var.app_url}/"))
}
