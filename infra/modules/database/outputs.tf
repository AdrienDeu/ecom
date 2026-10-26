output "endpoint" {
  description = "Hôte et port, ex. ecom-staging.xxxx.eu-west-3.rds.amazonaws.com:5432."
  value       = aws_db_instance.this.endpoint
}

output "address" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "jdbc_url" {
  value = "jdbc:postgresql://${aws_db_instance.this.endpoint}/${aws_db_instance.this.db_name}?sslmode=require"
}

output "master_user_secret_arn" {
  description = "Secret géré par RDS (clés JSON username et password)."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "instance_id" {
  value = aws_db_instance.this.identifier
}

output "security_group_id" {
  value = aws_security_group.this.id
}
