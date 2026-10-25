# TODO — Application e-commerce

Feuille de route du projet, ordonnée par étapes. Chaque étape doit être **livrable et déployable** avant de passer à la suivante.

> Stack de départ (déjà présente dans le repo) : Spring Boot 4.1.2, Maven, package `org.ecom`.
> Voir [TECHNICAL.md](TECHNICAL.md) pour l'architecture et les dépendances.

---

## Étape 0 — Décisions & cadrage

- [ ] Choisir le **cloud cible** (AWS / GCP / Azure / Scaleway / OVH) — conditionne les modules OpenTofu
- [ ] Choisir le **frontend** : SSR Thymeleaf (simple) ou SPA séparée (React/Angular/Vue) consommant l'API REST
- [ ] Choisir la **version Java** : `pom.xml` cible Java 27 (non-LTS) → recommandé : **Java 25 LTS** pour la prod
- [ ] Définir le périmètre MVP : catalogue, panier, commande, paiement PayPal, compte client, back-office minimal
- [ ] Définir les SLO (ex. p95 < 300 ms sur le catalogue, taux d'échec paiement < 2 %)
- [ ] Rédiger les ADR (Architecture Decision Records) dans `docs/adr/`

## Étape 1 — Fondations du repo GitHub

- [ ] `git init` + push sur GitHub, branche par défaut `main`
- [ ] Protection de `main` : PR obligatoire, 1 review min., checks CI requis, historique linéaire
- [ ] `CODEOWNERS`, templates d'issues et de PR
- [ ] Convention de commits (Conventional Commits) + versioning sémantique
- [ ] Activer **Dependabot** (Maven, GitHub Actions, OpenTofu, Docker) et **secret scanning / push protection**
- [ ] Créer les environnements GitHub : `staging`, `production` (avec reviewers obligatoires pour prod)
- [ ] Structure du repo :
  ```
  ecom/
  ├── src/                    # application Spring Boot
  ├── infra/                  # OpenTofu
  │   ├── modules/
  │   └── envs/{staging,production}/
  ├── observability/          # dashboards Grafana, règles d'alerte
  ├── docs/                   # TODO, doc technique, ADR
  └── .github/workflows/
  ```

## Étape 2 — Socle applicatif

- [ ] Ajouter les dépendances de base (web, JPA, validation, Flyway, PostgreSQL, actuator) — cf. doc technique
- [ ] `compose.yaml` local : PostgreSQL, Redis, Prometheus, Grafana, Tempo/Jaeger
- [ ] Profils Spring : `local`, `test`, `staging`, `prod` ; config externalisée (variables d'env, pas de secrets dans le repo)
- [ ] Gestion d'erreurs globale (`ProblemDetail` RFC 9457)
- [ ] Documentation API OpenAPI (springdoc)
- [ ] Healthchecks liveness/readiness (actuator probes)
- [ ] Tests d'intégration avec **Testcontainers**

## Étape 3 — Domaine métier

- [ ] **Catalogue** : produits, catégories, prix, stock, images, recherche/pagination
- [ ] **Panier** : panier invité (session/Redis) + fusion à la connexion
- [ ] **Commande** : machine à états (`CREATED → PAYMENT_PENDING → PAID → SHIPPED → DELIVERED`, `CANCELLED`, `REFUNDED`)
- [ ] **Clients** : inscription, adresses, historique de commandes
- [ ] **Sécurité** : authentification (OAuth2/OIDC via Keycloak ou équivalent), rôles `CUSTOMER` / `ADMIN`
- [ ] **Back-office** : gestion produits, commandes, remboursements
- [ ] Réservation de stock à la commande, libération si paiement échoué/expiré

## Étape 4 — Paiement (PayPal, extensible)

- [ ] Définir l'abstraction `PaymentProvider` (port) + `PaymentProviderRegistry` — cf. doc technique §4
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
- [ ] Workflow `infra-ci.yml` sur PR touchant `infra/` : `tofu fmt -check`, `tofu validate`, `tflint`, `trivy config`, `tofu plan` commenté dans la PR

## Étape 7 — Infrastructure (OpenTofu)

- [ ] Backend d'état distant avec verrouillage + **chiffrement de l'état** (fonctionnalité native OpenTofu)
- [ ] Authentification GitHub → cloud par **OIDC** (aucune clé statique en secret)
- [ ] Modules : réseau, base de données managée (PostgreSQL), cache (Redis), registre d'images, runtime (Kubernetes ou conteneurs managés), DNS/TLS, secrets manager, stack d'observabilité
- [ ] Un répertoire par environnement (`envs/staging`, `envs/production`) réutilisant les modules
- [ ] Versions de providers épinglées + `.terraform.lock.hcl` committé
- [ ] Gestion des secrets PayPal (client id/secret, webhook id) dans le secrets manager, injectés à l'exécution

## Étape 8 — CD

- [ ] Workflow `cd.yml` sur merge dans `main` :
  - [ ] Publication de l'image taguée par SHA + version, **signature cosign** + **SBOM**
  - [ ] `tofu apply` staging → déploiement staging
  - [ ] Migrations Flyway automatiques au démarrage (ou job dédié)
  - [ ] Smoke tests + tests E2E (Playwright) sur staging, paiement PayPal Sandbox inclus
  - [ ] Approbation manuelle (environnement GitHub `production`) → déploiement prod
- [ ] Stratégie de déploiement sans interruption (rolling ou blue/green / canary)
- [ ] Rollback automatique si healthcheck ou alertes post-déploiement en échec
- [ ] Release automatique (tag + changelog) via release-please ou semantic-release

## Étape 9 — Sécurité & conformité

- [ ] Aucune donnée carte stockée (PayPal gère la conformité **PCI-DSS**)
- [ ] RGPD : consentement cookies, export/suppression des données client, durée de rétention
- [ ] Headers de sécurité (CSP, HSTS), CSRF, rate limiting sur login/paiement
- [ ] Revue OWASP Top 10, test de pénétration avant mise en prod
- [ ] Sauvegardes BDD automatiques + test de restauration

## Étape 10 — Mise en production & après

- [ ] Passage PayPal Sandbox → Live (credentials live, webhooks live)
- [ ] Tests de charge (Gatling / k6) contre les SLO
- [ ] Runbooks d'incident (paiement en échec, PayPal indisponible, BDD saturée)
- [ ] Ajout d'un 2ᵉ moyen de paiement (Stripe / carte, Apple Pay…) pour valider l'extensibilité
