# 0001 — Socle technique

- **Statut** : accepté
- **Date** : 2026-10-25

## Contexte

Application e-commerce avec paiement PayPal extensible à d'autres moyens de paiement, métriques (latence, trafic, taux d'échec des paiements), CI/CD robuste et infrastructure en code. Le code est hébergé sur GitHub.

## Décision

| Sujet            | Choix                                   |
|------------------|-----------------------------------------|
| Backend          | Spring Boot 4.1, Java 25 LTS, Maven     |
| Architecture     | Monolithe modulaire, paiement en ports & adapters |
| Frontend         | SPA Angular, servie par S3 + CloudFront |
| Cloud            | AWS, région `eu-west-3` (Paris)         |
| Runtime backend  | ECS Fargate derrière un ALB             |
| Identité         | Amazon Cognito (OIDC, Authorization Code + PKCE) |
| Base de données  | PostgreSQL (RDS) + Flyway               |
| IaC              | OpenTofu, état S3 chiffré               |
| CI/CD            | GitHub Actions, OIDC vers AWS           |
| Observabilité    | Micrometer, Managed Prometheus/Grafana, X-Ray, CloudWatch Logs |

## Conséquences

- ECS Fargate plutôt qu'EKS : pas de cluster Kubernetes à opérer, au prix d'un écosystème d'outils plus restreint.
- Cognito : service managé et peu coûteux, mais personnalisation de l'interface de connexion limitée et migration vers un autre IdP plus coûteuse.
- `eu-west-3` : données hébergées en France (RGPD) ; certains services AWS récents peuvent y arriver plus tard qu'en `us-east-1` (les certificats ACM pour CloudFront doivent de toute façon être créés en `us-east-1`).
- Détails : [TECHNICAL.md](../TECHNICAL.md).
