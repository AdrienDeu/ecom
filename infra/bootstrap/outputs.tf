output "state_bucket" {
  description = "Bucket de l'état OpenTofu, à reporter dans infra/envs/<env>/backend.tf."
  value       = aws_s3_bucket.state.id
}

output "state_kms_key_arn" {
  description = "Clé KMS de chiffrement de l'état."
  value       = aws_kms_key.state.arn
}

output "tofu_plan_role_arn" {
  description = "À enregistrer dans la variable GitHub AWS_PLAN_ROLE_ARN_<ENV>."
  value       = aws_iam_role.tofu_plan.arn
}

output "tofu_apply_role_arn" {
  description = "À enregistrer dans la variable GitHub AWS_APPLY_ROLE_ARN de l'environnement GitHub."
  value       = aws_iam_role.tofu_apply.arn
}
