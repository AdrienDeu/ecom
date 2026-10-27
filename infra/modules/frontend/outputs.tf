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

output "media_bucket" {
  description = "Bucket des images produits (ECOM_MEDIA_BUCKET)."
  value       = aws_s3_bucket.media.id
}

output "media_bucket_arn" {
  value = aws_s3_bucket.media.arn
}
