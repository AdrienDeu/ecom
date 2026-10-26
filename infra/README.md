# Infrastructure (OpenTofu, AWS)

Un compte AWS par environnement (`staging`, `production`), région `eu-west-3` ; CloudFront impose `us-east-1` pour son certificat et son WAF. Architecture et règles : [docs/TECHNICAL.md §8](../docs/TECHNICAL.md#8-infrastructure-opentofu).

```
infra/
├── bootstrap/         # par compte, appliqué à la main : bucket d'état, clé KMS, OIDC GitHub, rôles plan/apply
├── modules/
│   ├── platform/      # un environnement complet ; le seul module appelé par envs/*
│   ├── network/       # VPC, sous-réseaux public/app/data, NAT, VPC endpoints, flow logs
│   ├── database/      # RDS PostgreSQL (mot de passe géré par RDS dans Secrets Manager)
│   ├── cache/         # ElastiCache Valkey (TLS + jeton d'authentification)
│   ├── registry/      # ECR (tags immuables, scan à l'envoi)
│   ├── backend/       # ALB + ECS Fargate (app + collecteur ADOT), autoscaling, circuit breaker
│   ├── identity/      # Cognito : user pool, groupes CUSTOMER/ADMIN, client SPA, Managed Login
│   ├── edge/          # us-east-1 : certificat ACM et WAF de CloudFront
│   ├── frontend/      # bucket S3 privé (OAC) + CloudFront (SPA et /api/*), en-têtes de sécurité, config.json
│   ├── secrets/       # Secrets Manager : PayPal (valeur saisie hors OpenTofu)
│   └── observability/ # Managed Prometheus (règles d'alerte d'observability/), alarmes CloudWatch, SNS, Grafana
└── envs/{staging,production}/   # mêmes fichiers, seules les dimensions (terraform.tfvars) diffèrent
```

## Prérequis

- OpenTofu ≥ 1.10 (la CI utilise la version de `TOFU_VERSION` dans `.github/workflows/infra-ci.yml`)
- AWS CLI v2, un profil administrateur par compte (ex. `ecom-staging`, `ecom-production`)
- Une zone Route 53 publique dans chaque compte, déléguée depuis le registrar, qui contient le domaine de l'environnement (`hosted_zone_name` / `domain_name` dans `terraform.tfvars`)

## 1. Amorçage d'un compte (une fois, à la main)

Le bucket d'état n'existe pas encore : premier apply avec un état local, puis migration dans le bucket créé.

```bash
cd infra/bootstrap
export AWS_PROFILE=ecom-staging
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# État local le temps du premier apply (fichier ignoré par git)
echo 'terraform { backend "local" {} }' > backend_override.tf
tofu init
tofu apply -var environment=staging -var account_id=$ACCOUNT_ID

# Migration de l'état dans le bucket qui vient d'être créé
rm backend_override.tf
tofu init -migrate-state -backend-config="bucket=ecom-tofu-state-$ACCOUNT_ID"
rm -f terraform.tfstate terraform.tfstate.backup
```

Pour un compte déjà amorcé (ou l'autre compte, après `tofu init -reconfigure`), même `-backend-config`, sans override.

Reporter ensuite les sorties dans GitHub (*Settings → Secrets and variables → Actions → Variables*) :

| Variable                                      | Portée                       | Valeur                                    |
|-----------------------------------------------|------------------------------|-------------------------------------------|
| `AWS_ACCOUNT_ID_STAGING` / `_PRODUCTION`      | dépôt                        | id du compte                              |
| `AWS_PLAN_ROLE_ARN_STAGING` / `_PRODUCTION`   | dépôt                        | sortie `tofu_plan_role_arn`               |
| `AWS_APPLY_ROLE_ARN`                          | environnement GitHub `staging` / `production` | sortie `tofu_apply_role_arn` |

Tant que les variables d'un environnement sont absentes, le job *Plan* de `infra-ci.yml` est ignoré pour cet environnement.

Le rôle *plan* (lecture seule) n'est assumable que depuis une PR ou `main` ; le rôle *apply* seulement depuis l'environnement GitHub du même nom, donc après approbation pour la production.

## 2. Premier déploiement d'un environnement

Ce premier déploiement se fait à la main, avec le profil administrateur ; les suivants passent par la CD (§4).

```bash
cd infra/envs/staging
export AWS_PROFILE=ecom-staging
export TF_VAR_account_id=$(aws sts get-caller-identity --query Account --output text)
tofu init -backend-config="bucket=ecom-tofu-state-$TF_VAR_account_id"
```

1. **Registre d'abord**, pour pouvoir y pousser l'image avant de créer le service ECS :

   ```bash
   TF_VAR_image=none tofu apply -target=module.platform.module.registry
   REPO=$(tofu output -raw ecr_repository_url)
   aws ecr get-login-password | docker login --username AWS --password-stdin "${REPO%%/*}"
   ./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName="$REPO:$(git rev-parse --short HEAD)"   # depuis la racine
   docker push "$REPO:$(git rev-parse --short HEAD)"
   ```

2. **Tout l'environnement** (certificats, CloudFront et RDS : compter 15 à 30 minutes) :

   ```bash
   export TF_VAR_image="$REPO:<tag>"
   tofu apply
   ```

   Flyway applique les migrations au démarrage des tâches.

3. **SPA** (ou laisser la CD s'en charger au prochain merge) : le build est le même pour tous les environnements, `config.json` est écrit par OpenTofu et ne doit pas être écrasé.

   ```bash
   (cd frontend && npm ci && npm run build)
   aws s3 sync frontend/dist/ecom-frontend/browser "s3://$(tofu -chdir=infra/envs/staging output -raw spa_bucket)" \
     --delete --exclude config.json
   aws cloudfront create-invalidation --paths '/*' \
     --distribution-id "$(tofu -chdir=infra/envs/staging output -raw cloudfront_distribution_id)"
   ```

4. **Premier administrateur** : s'inscrire sur la boutique, puis

   ```bash
   aws cognito-idp admin-add-user-to-group --user-pool-id "$(tofu output -raw cognito_user_pool_id)" \
     --username <email> --group-name ADMIN
   ```

## 3. Activer PayPal

Le secret est créé vide : ni le code ni l'état ne contiennent les identifiants.

```bash
aws secretsmanager put-secret-value --secret-id "$(tofu output -raw paypal_secret_arn)" \
  --secret-string '{"client_id":"…","client_secret":"…","webhook_id":"…"}'
```

Puis `paypal_enabled = true` dans `terraform.tfvars` (PR → plan → CD). Sans valeur dans le secret, les nouvelles tâches ne démarrent pas et le circuit breaker ECS revient à la révision précédente. URL du webhook à déclarer chez PayPal : `https://<domain_name>/api/webhooks/paypal`.

## 4. Déploiements (CD)

Chaque merge dans `main` déclenche `cd.yml` :

1. build unique de l'image (archive `docker save`, digest calculé) et de la SPA ;
2. `deploy.yml` en **staging** : push dans ECR sous `sha-<commit>` (le digest poussé doit être celui du build), signature cosign sans clé + attestation du SBOM, `tofu apply` avec l'image référencée par digest, attente du déploiement ECS, publication de la SPA, smoke tests ;
3. même chose en **production** après approbation dans l'environnement GitHub.

ECS revient seul à la révision précédente si les nouvelles tâches ne deviennent pas saines (circuit breaker) ou si les alarmes de déploiement (5xx, p95 > 1 s) se déclenchent ; le job échoue alors. Revenir à une version antérieure : `git revert` dans `main` (nouveau déploiement), ou relancer le run de la CD de cette version tant que ses artefacts existent (7 jours).

Vérifier la signature d'une image :

```bash
cosign verify <repo>@<digest> \
  --certificate-identity-regexp '^https://github.com/AdrienDeu/ecom/\.github/workflows/deploy\.yml@' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

`drift.yml` compare chaque jour l'état réel au code (rôle en lecture seule) et échoue en cas d'écart : changement manuel à annuler ou à reporter dans le code, ou merge pas encore déployé en production.

## 5. Vérifications locales

Les mêmes que le job *Infra* de la CI :

```bash
tofu fmt -check -recursive infra
for dir in $(find infra -name '*.tf' -not -path '*/.terraform/*' -exec dirname {} \; | sort -u); do
  tofu -chdir="$dir" init -backend=false -input=false >/dev/null && tofu -chdir="$dir" validate
done
tflint --init && tflint --recursive --chdir=infra
trivy config --severity HIGH,CRITICAL infra
```

Après un changement de version de provider : `tofu init -upgrade` dans chaque répertoire racine (`bootstrap`, `envs/*`), en committant les `.terraform.lock.hcl`.

## Choix et limites

- **Coûts de staging** : une seule NAT, base mono-AZ, cache sans réplica, 1 tâche min. NAT, ALB, RDS, ElastiCache et VPC endpoints tournent en permanence ; détruire staging hors période d'usage reste possible (`deletion_protection = false`).
- **Origine de l'API** : l'ALB est public, mais son groupe de sécurité n'accepte que les IP d'origine de CloudFront (prefix list gérée) et le listener exige l'en-tête secret `X-Origin-Verify` ajouté par la distribution (sinon 403). Les CloudFront VPC origins (ALB privé) sont une évolution possible.
- **Grafana** : Amazon Managed Grafana exige IAM Identity Center (`enable_managed_grafana = false` par défaut) ; sinon, interroger Managed Prometheus depuis un Grafana existant avec la source de données SigV4.
- **ECS Exec** désactivé par défaut (`enable_execute_command` du module `backend`).
