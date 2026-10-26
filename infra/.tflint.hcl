# Règles tflint partagées par tous les répertoires de infra/ (tflint --recursive)
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}
