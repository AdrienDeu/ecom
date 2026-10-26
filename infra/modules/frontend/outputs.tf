output "bucket" {
  description = "Bucket où la CD publie la SPA (sans écraser config.json)."
  value       = aws_s3_bucket.spa.id
}

output "distribution_id" {
  description = "Distribution à invalider après publication."
  value       = aws_cloudfront_distribution.this.id
}

output "distribution_arn" {
  value = aws_cloudfront_distribution.this.arn
}
