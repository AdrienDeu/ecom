# TODO — Application e-commerce

Feuille de route du projet, ordonnée par étapes. Chaque étape doit être **livrable et déployable** avant de passer à la suivante.

> Stack de départ (déjà présente dans le repo) : Spring Boot 4.1.2, Maven, package `org.ecom`.
> Voir [TECHNICAL.md](TECHNICAL.md) pour l'architecture et les dépendances.

---

## Étape 0 — Décisions & cadrage

- [x] Cloud cible : **AWS**
- [x] Frontend : **SPA séparée** consommant l'API REST (hébergée sur S3 + CloudFront)
- [x] Version Java : **Java 25 LTS** (`pom.xml` mis à jour)
- [x] IaC : **OpenTofu**
- [x] Framework SPA : **Angular**
- [x] Runtime backend : **ECS Fargate**
- [x] Fournisseur d'identité : **Amazon Cognito**
- [x] Région AWS : **eu-west-3** (Paris)
- [ ] Définir le périmètre MVP : catalogue, panier, commande, paiement PayPal, compte client, back-office minimal
- [ ] Définir les SLO (ex. p95 < 300 ms sur le catalogue, taux d'échec paiement < 2 %)
- [ ] Rédiger les ADR (Architecture Decision Records) dans `docs/adr/`

## Étape 1 — Fondations du repo GitHub

- [x] Repo GitHub `AdrienDeu/ecom`, branches `main` (prod) et `dev` (intégration)
- [x] Rulesets `main` et `dev` : PR obligatoire, checks CI + CodeQL requis et à jour, pas de force-push ni suppression ; `dev` en squash + historique linéaire, `main` en merge commit (promotion depuis `dev`)
- [x] `CODEOWNERS`, templates d'issues et de PR, `.editorconfig`, README
- [x] Convention de commits (Conventional Commits) — documentée dans le README
- [ ] Versioning sémantique automatisé (release-please, étape 8)
- [x] Dependabot configuré (Maven, GitHub Actions ; npm et OpenTofu à décommenter plus tard)
- [x] Secret scanning + push protection, alertes et correctifs de sécurité Dependabot, signalement privé de vulnérabilités
- [x] Workflows `ci.yml` (build + tests Java 25) et `codeql.yml` (Java + Actions), actions épinglées par SHA
- [x] ADR `docs/adr/0001-socle-technique.md`
- [x] Environnements GitHub `staging` et `production` (déploiement depuis `main` uniquement, approbation obligatoire pour prod)
- [x] Structure du repo :
  ```
  ecom/
  ├── src/                    # backend Spring Boot (API REST)
  ├── frontend/               # SPA
  ├── infra/                  # OpenTofu (AWS)
  │   ├── bootstrap/
  │   ├── modules/
  │   └── envs/{staging,production}/
  ├── observability/          # dashboards Grafana, règles d'alerte
  ├── docs/                   # TODO, doc technique, ADR
  └── .github/workflows/
  ```

## Étape 2 — Socle applicatif

- [x] Dépendances de base (webmvc, validation, JPA, Flyway, PostgreSQL, actuator, Prometheus, springdoc) — cf. doc technique
- [x] `compose.yaml` local : PostgreSQL 18, Valkey 9, Prometheus, Grafana (provisionné) — démarré automatiquement avec le profil `local`
- [ ] Ajouter Tempo au `compose.yaml` (étape 5, avec OpenTelemetry)
- [x] Profils Spring `local`, `staging`, `prod` ; config externalisée (variables d'env, pas de secrets dans le repo) — les tests utilisent Testcontainers sans profil dédié
- [x] Gestion d'erreurs globale (`ProblemDetail` RFC 9457), sans fuite de détails internes sur les 500
- [x] Configuration CORS (`ecom.cors.allowed-origins`, `localhost:4200` en local)
- [x] Projet Angular 22 dans `frontend/` (standalone, angular-eslint, Vitest, Node 24 en CI, scripts d'installation natifs refusés)
- [x] Génération du client API configurée (`npm run generate:api`, ng-openapi-gen) — à lancer quand les premiers endpoints existeront (étape 3)
- [x] Proxy de dev Angular (`proxy.conf.json`) : `/api` → `localhost:8080`
- [x] Documentation API OpenAPI (springdoc) : `/api/openapi`, `/api/swagger-ui` — désactivée en prod
- [x] Healthchecks liveness/readiness sur le port de management `8081`, non exposé sur le port applicatif
- [x] Tests d'intégration avec **Testcontainers** (`*IT`, failsafe) + `TestEcomApplication` pour lancer l'app sur Testcontainers
- [x] Job CI frontend (audit, lint, tests, build) ajouté à `ci.yml` ; Dependabot npm et docker-compose activés

## Étape 3 — Domaine métier

### 3a — Backend

- [x] **Catalogue** : produits, catégories, prix (`Money`), images (URL), recherche plein texte simple + filtre catégorie + pagination
- [x] **Panier** : panier invité (`X-Cart-Id`, Valkey/Redis, TTL 30 j) + fusion à la connexion, prix toujours relus du catalogue
- [x] **Commande** : machine à états (`CREATED → PAYMENT_PENDING → PAID → SHIPPED → DELIVERED`, `CANCELLED`, `REFUNDED`), prix et adresse figés au checkout
- [x] **Clients** : adresses (`/api/me/addresses`), historique de commandes — l'inscription est gérée par Cognito
- [x] **Sécurité backend** : validation des JWT Cognito (issuer, signature, `token_use=access`, `client_id`), groupes `cognito:groups` → rôles ; actuator ouvert sur le port de management uniquement
- [x] Serveur OIDC de test (`mock-oauth2-server`) dans `compose.yaml` pour développer sans Cognito
- [x] **Back-office API** : produits, catégories, stock, commandes (expédition, livraison, annulation)
- [x] Réservation de stock atomique au checkout (pas de survente, testé en concurrence), libération à l'annulation et à l'expiration (job `SKIP LOCKED`, multi-instances)
- [ ] Remboursements (étape 4, via le module paiement)
- [ ] Clé d'idempotence sur `POST /api/orders` (double-clic → une seule commande)
- [ ] Cache du catalogue (`@Cacheable`) si les tests de charge le justifient

### 3b — Frontend Angular

- [ ] Authentification Cognito (`angular-auth-oidc-client`, Authorization Code + PKCE), interceptor `/api/**`, guards
- [ ] Catalogue : liste, recherche, filtre catégorie, fiche produit
- [ ] Panier invité + fusion à la connexion
- [ ] Compte : adresses, historique et détail des commandes
- [ ] Checkout jusqu'à la création de commande (le paiement arrive à l'étape 4)
- [ ] Back-office : produits, stock, commandes
- [ ] Client API généré (`npm run generate:api`)

## Étape 4 — Paiement (PayPal, extensible)

- [ ] Définir l'abstraction `PaymentProvider` (port) + `PaymentProviderRegistry` — cf. doc technique §5
- [ ] Modèle `Payment` / `PaymentAttempt` persisté, indépendant du fournisseur
- [ ] Adapter **PayPal** (API Orders v2) : `create order` → approbation client → `capture`
- [ ] Authentification OAuth2 client credentials PayPal avec cache du token
- [ ] **Clé d'idempotence** sur chaque création/capture (`PayPal-Request-Id`)
- [ ] **Webhooks** PayPal : endpoint dédié + vérification de signature + traitement idempotent
- [ ] Réconciliation : job planifié qui vérifie les paiements restés `PENDING`
- [ ] Remboursements (total / partiel)
- [ ] Résilience : timeouts, retry contrôlé, circuit breaker sur les appels PayPal
- [ ] Tests : WireMock pour l'API PayPal + tests manuels sur **PayPal Sandbox**
- [ ] Vérifier qu'ajouter un nouveau provider (ex. Stripe) = **un nouvel adapter, zéro modification du domaine**

## Étape 5 — Observabilité & métriques

- [ ] Micrometer + endpoint Prometheus (`/actuator/prometheus`) sur un port de management séparé
- [ ] Histogrammes de latence (`http.server.requests`) avec percentiles p50/p95/p99
- [ ] Métriques métier custom :
  - [ ] `ecom.page.views` (vues par page/type de page)
  - [ ] `ecom.payment.attempts` tagué `provider`, `outcome` (`success` / `failed` / `cancelled`), `reason`
  - [ ] `ecom.payment.duration`
  - [ ] `ecom.orders.created`, `ecom.cart.abandoned`
- [ ] Traces distribuées (OpenTelemetry → Tempo/Jaeger), corrélation `traceId` dans les logs
- [ ] Logs structurés JSON → Loki (ou équivalent cloud)
- [ ] Dashboards Grafana versionnés dans `observability/` (temps de réponse, trafic, taux d'échec paiement)
- [ ] Alertes : taux d'échec paiement > seuil, p95 > SLO, erreurs 5xx, webhook PayPal en échec

## Étape 6 — CI (GitHub Actions)

- [ ] Workflow `ci.yml` sur chaque PR :
  - [ ] Build + tests unitaires + tests d'intégration (Testcontainers)
  - [ ] Couverture (JaCoCo) avec seuil minimal
  - [ ] Qualité : Checkstyle/Spotless, SpotBugs, (optionnel SonarCloud)
  - [ ] Sécurité : **CodeQL**, scan des dépendances (Trivy / OWASP), scan des secrets
  - [ ] Build de l'image OCI (`spring-boot:build-image` ou Jib) + scan Trivy de l'image
- [ ] Cache Maven, exécution parallèle des jobs
- [ ] Workflow `frontend-ci.yml` sur PR touchant `frontend/` : lint, tests, build, audit des dépendances npm
- [ ] Workflow `infra-ci.yml` sur PR touchant `infra/` : `tofu fmt -check`, `tofu validate`, `tflint`, `trivy config`, `tofu plan` commenté dans la PR

## Étape 7 — Infrastructure (OpenTofu)

- [ ] Bootstrap (une fois, à la main) : bucket S3 d'état (versionné, chiffré KMS) + clé KMS de chiffrement d'état
- [ ] Backend S3 avec verrouillage natif (`use_lockfile = true`) + **chiffrement de l'état** OpenTofu via KMS
- [ ] Comptes AWS séparés `staging` / `production` (AWS Organizations) — recommandé
- [ ] Fournisseur OIDC GitHub dans IAM + un rôle IAM par environnement (plan en lecture seule / apply), restreint au repo et à l'environnement
- [ ] Modules AWS :
  - [ ] `network` : VPC, sous-réseaux publics/privés sur 2–3 AZ, NAT, VPC endpoints (ECR, S3, Secrets Manager)
  - [ ] `database` : RDS PostgreSQL Multi-AZ (prod), backups automatiques, chiffrement KMS
  - [ ] `cache` : ElastiCache (Valkey/Redis)
  - [ ] `registry` : ECR (scan à l'envoi, tags immuables)
  - [ ] `backend` : ECS Fargate + ALB + autoscaling
  - [ ] `identity` : Cognito User Pool, groupes `CUSTOMER`/`ADMIN`, client SPA, domaine Managed Login
  - [ ] `frontend` : bucket S3 privé + CloudFront (OAC) + ACM
  - [ ] `edge` : Route 53, certificats ACM, AWS WAF
  - [ ] `secrets` : Secrets Manager (PayPal, BDD)
  - [ ] `observability` : Amazon Managed Prometheus + Amazon Managed Grafana, CloudWatch Logs, X-Ray/ADOT
- [ ] Un répertoire par environnement (`envs/staging`, `envs/production`) réutilisant les modules
- [ ] Versions de providers épinglées + `.terraform.lock.hcl` committé
- [ ] Secrets PayPal (client id/secret, webhook id) dans Secrets Manager, injectés dans la tâche ECS

## Étape 8 — CD

- [ ] Workflow `cd.yml` sur merge dans `main` :
  - [ ] Publication de l'image taguée par SHA + version, **signature cosign** + **SBOM**
  - [ ] Push de l'image dans ECR, `tofu apply` staging → nouvelle révision de tâche ECS
  - [ ] SPA : build → sync S3 → invalidation CloudFront
  - [ ] Migrations Flyway automatiques au démarrage (ou job dédié)
  - [ ] Smoke tests + tests E2E (Playwright) sur staging, paiement PayPal Sandbox inclus
  - [ ] Approbation manuelle (environnement GitHub `production`) → déploiement prod
- [ ] Déploiement sans interruption : rolling ECS avec **deployment circuit breaker** (ou blue/green via CodeDeploy)
- [ ] Rollback automatique si healthcheck ou alarmes CloudWatch post-déploiement en échec
- [ ] Release automatique (tag + changelog) via release-please ou semantic-release

## Étape 9 — Sécurité & conformité

- [ ] Aucune donnée carte stockée (PayPal gère la conformité **PCI-DSS**)
- [ ] RGPD : consentement cookies, export/suppression des données client, durée de rétention
- [ ] Headers de sécurité (CSP, HSTS via CloudFront response headers policy), rate limiting AWS WAF sur login/paiement
- [ ] Jetons stockés côté SPA en mémoire (pas de `localStorage`), refresh token rotatif
- [ ] Revue OWASP Top 10, test de pénétration avant mise en prod
- [ ] Sauvegardes RDS automatiques + test de restauration
- [ ] AWS Budgets + alertes de coût, GuardDuty, CloudTrail

## Étape 10 — Mise en production & après

- [ ] Passage PayPal Sandbox → Live (credentials live, webhooks live)
- [ ] Tests de charge (Gatling / k6) contre les SLO
- [ ] Runbooks d'incident (paiement en échec, PayPal indisponible, BDD saturée)
- [ ] Ajout d'un 2ᵉ moyen de paiement (Stripe / carte, Apple Pay…) pour valider l'extensibilité
