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
- [x] Ajouter Tempo (et Loki) au `compose.yaml` (étape 5, avec OpenTelemetry)
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
- [x] Remboursements (étape 4, via le module paiement)
- [ ] Clé d'idempotence sur `POST /api/orders` (double-clic → une seule commande)
- [ ] Cache du catalogue (`@Cacheable`) si les tests de charge le justifient

### 3b — Frontend Angular

- [x] Authentification OIDC (`angular-auth-oidc-client`, Authorization Code + PKCE, refresh token), jeton limité à `/api/**`, guards, retour à la page demandée
- [x] Configuration runtime `/config.json` (même build promu de staging à prod)
- [x] Catalogue : liste, recherche, filtre catégorie, pagination, fiche produit
- [x] Panier invité + fusion à la connexion
- [x] Compte : adresses, historique et détail des commandes, annulation
- [x] Checkout jusqu'à la création de commande (le paiement arrive à l'étape 4)
- [x] Back-office : produits (création, édition, désactivation), catégories, stock, commandes (expédition, livraison, annulation)
- [x] Client API généré (`npm run generate:api`) + test de contrat OpenAPI côté backend
- [x] Parcours complet validé dans Chromium headless (catalogue → panier invité → login → fusion → adresse → commande → back-office → mobile)
- [ ] Tests E2E Playwright versionnés et exécutés en CD contre staging (étape 8)
- [x] Vérifier en CI que le client généré est à jour par rapport à l'OpenAPI
- [ ] Images produits : upload vers S3 (aujourd'hui une URL saisie à la main)

### 3c — Offre conseil + matériel

- [x] Type de produit `PRODUCT` / `SERVICE` : les forfaits de conseil se commandent et se paient comme du matériel, sans stock
- [x] Demandes de devis (formulaire public anti-spam, suivi dans le back-office, limite de débit WAF)
- [x] Refonte du front : accueil (conseil + matériel), pages Conseil et Matériel, fiche prestation, page devis ; police auto-hébergée
- [x] Catalogue de démonstration en local (`db/demo`)
- [ ] Choisir le nom définitif (`frontend/src/app/core/brand.ts`) et l'adresse de contact
- [ ] Prix HT/TTC : décider de l'affichage (clientèle professionnelle) et des mentions de facturation
- [ ] Notification e-mail à la réception d'une demande de devis (Amazon SES)
- [ ] Commandes de prestations : statuts adaptés (planifiée, réalisée) au lieu d'« expédiée / livrée »
- [ ] Photos produits (upload S3, cf. 3b)

## Étape 4 — Paiement (PayPal, extensible)

- [x] Définir l'abstraction `PaymentProvider` (port) + `PaymentProviderRegistry` — cf. doc technique §5
- [x] Modèle `Payment` / `PaymentAttempt` persisté, indépendant du fournisseur
- [x] Adapter **PayPal** (API Orders v2) : `create order` → approbation client (PayPal JS SDK Buttons) → `capture`
- [x] Authentification OAuth2 client credentials PayPal avec cache du token
- [x] **Clé d'idempotence** sur chaque création/capture/remboursement (`PayPal-Request-Id`)
- [x] **Webhooks** PayPal : endpoint dédié + vérification de signature + traitement idempotent
- [x] Réconciliation : job planifié qui vérifie les paiements restés `PENDING`
- [x] Remboursements (total / partiel) + remboursement automatique d'un paiement capturé sur une commande expirée
- [x] Résilience : timeouts, retry contrôlé, circuit breaker sur les appels PayPal
- [x] Tests : WireMock pour l'API PayPal
- [ ] Tests manuels sur **PayPal Sandbox** (app sandbox + webhook à créer, cf. README)
- [ ] Vérifier qu'ajouter un nouveau provider (ex. Stripe) = **un nouvel adapter, zéro modification du domaine** (étape 10)

## Étape 5 — Observabilité & métriques

- [x] Micrometer + endpoint Prometheus (`/actuator/prometheus`) sur un port de management séparé
- [x] Histogrammes de latence (`http.server.requests`) avec percentiles p50/p95/p99 + buckets aux seuils SLO (100 ms, 300 ms, 1 s)
- [x] Métriques métier custom :
  - [x] `ecom.page.views` (vues par type de page, envoyées par la SPA)
  - [x] `ecom.payment.attempts` tagué `provider`, `outcome` (`success` / `failed` / `cancelled`), `reason`
  - [x] `ecom.payment.duration` (par opération et issue) + `ecom.payment.webhooks`, `ecom.payment.refunds`
  - [x] `ecom.orders.placed`, `ecom.orders.expired` (abandon au paiement, à la place de `ecom.cart.abandoned`)
- [x] Traces distribuées (OpenTelemetry → Tempo en local, ADOT → X-Ray en AWS), corrélation `traceId` dans les logs, exemplars Prometheus → traces
- [x] Logs structurés JSON (ECS) en staging/prod → CloudWatch ; OTLP → Loki en local, corrélés aux traces
- [x] Dashboards Grafana versionnés dans `observability/` (temps de réponse, trafic, taux d'échec paiement)
- [x] Alertes : taux d'échec paiement > seuil, p95 > SLO, erreurs 5xx, webhook PayPal absent ou rejeté, fournisseur en erreur — testées avec `promtool`
- [ ] Core Web Vitals (LCP, INP, CLS) côté navigateur — CloudWatch RUM (étape 7)
- [x] Exécuter `promtool check rules` / `test rules` en CI (étape 6)

## Étape 6 — CI (GitHub Actions)

- [x] Workflow `ci.yml` sur chaque PR :
  - [x] Build + tests unitaires + tests d'intégration (Testcontainers)
  - [x] Couverture (JaCoCo, unitaires + intégration) avec seuils : 80 % des lignes, 70 % des branches
  - [x] Qualité : Spotless (imports, espaces), SpotBugs + FindSecBugs ; SonarCloud écarté pour l'instant
  - [x] Sécurité : **CodeQL** (Java, TypeScript, Actions), Trivy (dépendances, secrets, misconfigurations) → onglet Security
  - [x] Build de l'image OCI (`spring-boot:build-image`) + scan Trivy de l'image
  - [x] Client API Angular vérifié à jour par rapport à l'OpenAPI
  - [x] Règles d'alerte Prometheus vérifiées et testées (`promtool`)
- [x] Cache Maven et npm, exécution parallèle des jobs
- [x] Frontend (lint, tests, build, audit npm) — dans `ci.yml` plutôt qu'un workflow filtré par chemins, pour rester un check obligatoire
- [x] Workflow `infra-ci.yml` sur PR touchant `infra/` : `tofu fmt -check`, `tofu validate`, `tflint`, `trivy config`
- [x] `tofu plan` commenté dans la PR (job *Plan* de `infra-ci.yml`, rôle IAM lecture seule via OIDC ; ignoré tant que le compte n'est pas amorcé)
- [x] Nouveaux jobs ajoutés aux checks obligatoires des rulesets `main` et `dev` (sauf `infra-ci.yml`, filtré par chemins)

## Étape 7 — Infrastructure (OpenTofu)

> Code complet et vérifié (`fmt`, `validate`, `tflint`, `trivy config`) ; rien n'est encore appliqué. Procédure : [infra/README.md](../infra/README.md).

- [x] Bootstrap (une fois par compte, à la main) : bucket S3 d'état (versionné, SSE-KMS, TLS obligatoire) + clé KMS de chiffrement d'état, état migré dans le bucket
- [x] Backend S3 avec verrouillage natif (`use_lockfile = true`) + **chiffrement de l'état et des plans** OpenTofu via KMS (`enforced`)
- [ ] Comptes AWS séparés `staging` / `production` (AWS Organizations) — le code l'impose (`allowed_account_ids`), comptes à créer
- [x] Fournisseur OIDC GitHub dans IAM + rôles par environnement : `plan` (lecture seule, PR et `main`) / `apply` (environnement GitHub uniquement)
- [x] Modules AWS (assemblés par `platform`, seul module appelé par les environnements) :
  - [x] `network` : VPC, sous-réseaux public/app/data sur 2–3 AZ, NAT (une ou par AZ), VPC endpoints (ECR, S3, Secrets Manager, logs…), flow logs
  - [x] `database` : RDS PostgreSQL Multi-AZ (prod), backups automatiques, chiffrement KMS, mot de passe géré par RDS
  - [x] `cache` : ElastiCache Valkey (TLS, jeton d'authentification)
  - [x] `registry` : ECR (scan à l'envoi, tags immuables)
  - [x] `backend` : ECS Fargate (app + collecteur ADOT) + ALB (limité à CloudFront) + autoscaling CPU/requêtes + circuit breaker
  - [x] `identity` : Cognito User Pool, groupes `CUSTOMER`/`ADMIN`, client SPA, domaine Managed Login
  - [x] `frontend` : bucket S3 privé + CloudFront (OAC), `/api/*` vers l'ALB, en-têtes de sécurité (CSP PayPal, HSTS), `config.json`
  - [x] `edge` : certificat ACM et AWS WAF (règles gérées + rate limiting paiement et télémétrie ; le login est chez Cognito) en us-east-1, enregistrements Route 53
  - [x] `secrets` : Secrets Manager (PayPal ; BDD via le secret géré par RDS)
  - [x] `observability` : Amazon Managed Prometheus (règles d'alerte d'`observability/`), Managed Grafana (optionnel), CloudWatch Logs et alarmes, X-Ray via ADOT, SNS
- [x] Un répertoire par environnement (`envs/staging`, `envs/production`) réutilisant les modules, seules les dimensions diffèrent
- [x] Versions de providers épinglées + `.terraform.lock.hcl` committé
- [x] Secrets PayPal (client id/secret, webhook id) dans Secrets Manager, injectés dans la tâche ECS (`paypal_enabled`)
- [ ] Appliquer le bootstrap puis les environnements (zone Route 53 déléguée, domaine réel dans `terraform.tfvars`)

## Étape 8 — CD

- [x] Workflow `cd.yml` sur merge dans `main` (déploiement par environnement dans le workflow réutilisable `deploy.yml`) :
  - [x] Image construite une fois, taguée `sha-<commit>`, **signée avec cosign** (sans clé, OIDC) + **SBOM** CycloneDX attesté ; même digest dans chaque environnement (vérifié au push)
  - [ ] Tag de version SemVer sur l'image (avec release-please)
  - [x] Push de l'image dans ECR, `tofu apply` staging → nouvelle révision de tâche ECS, attente du déploiement complet
  - [x] SPA : build unique → sync S3 (bundles hachés en cache long, `index.html` en `no-cache`, `config.json` préservé) → invalidation CloudFront
  - [x] Migrations Flyway automatiques au démarrage
  - [x] Smoke tests sur l'environnement déployé (SPA, `config.json`, API catalogue)
  - [ ] Tests E2E (Playwright) sur staging, paiement PayPal Sandbox inclus
  - [x] Approbation manuelle (environnement GitHub `production`) → déploiement prod
- [x] Déploiement sans interruption : rolling ECS avec **deployment circuit breaker**
- [x] Rollback automatique : circuit breaker (tâches non saines) + alarmes de déploiement ECS (5xx, p95 > 1 s) ; la CD échoue si la nouvelle révision n'est pas entièrement déployée
- [x] Détection de dérive : `drift.yml` quotidien (`tofu plan -detailed-exitcode` par environnement)
- [ ] Release automatique (tag + changelog) via release-please ou semantic-release
- [ ] Valider le premier run réel de la CD (après amorçage des comptes, cf. étape 7)

## Étape 9 — Sécurité & conformité

- [ ] Aucune donnée carte stockée (PayPal gère la conformité **PCI-DSS**)
- [ ] RGPD : consentement cookies, export/suppression des données client, durée de rétention (dont les demandes de devis)
- [ ] Headers de sécurité (CSP — autoriser `www.paypal.com`, `*.paypal.com`, `*.paypalobjects.com` pour le SDK PayPal —, HSTS via CloudFront response headers policy), rate limiting AWS WAF sur login/paiement
- [ ] Jetons stockés côté SPA en mémoire (pas de `localStorage`), refresh token rotatif
- [ ] Revue OWASP Top 10, test de pénétration avant mise en prod
- [ ] Sauvegardes RDS automatiques + test de restauration
- [ ] AWS Budgets + alertes de coût, GuardDuty, CloudTrail

## Étape 10 — Mise en production & après

- [ ] Passage PayPal Sandbox → Live (credentials live, webhooks live)
- [ ] Tests de charge (Gatling / k6) contre les SLO
- [ ] Runbooks d'incident (paiement en échec, PayPal indisponible, BDD saturée)
- [ ] Ajout d'un 2ᵉ moyen de paiement (Stripe / carte, Apple Pay…) pour valider l'extensibilité
