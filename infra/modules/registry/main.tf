# Images de l'application : tags immuables (une version = un digest), scan à l'envoi.
# Images taguées sha-<commit> par la CD ; signatures et attestations cosign attachées par digest.

resource "aws_ecr_repository" "this" {
  name                 = var.name
  image_tag_mutability = "IMMUTABLE_WITH_EXCLUSION"

  # Signatures cosign au format tag (sha256-<digest>.sig/.att), réécrites à chaque nouvelle signature
  image_tag_mutability_exclusion_filter {
    filter      = "sha256-*"
    filter_type = "WILDCARD"
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = var.kms_key_arn
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  policy = jsonencode({
    rules = [
      # Pas de règle sur les images sans tag : signatures et attestations cosign (referrers OCI) n'en ont pas
      {
        rulePriority = 1
        description  = "Seules les ${var.keep_images} images de l'application les plus récentes sont gardées"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["sha-*"]
          countType      = "imageCountMoreThan"
          countNumber    = var.keep_images
        }
        action = { type = "expire" }
      },
    ]
  })
}
