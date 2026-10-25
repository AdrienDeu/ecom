# Documentation technique — ecom

## 1. Vue d'ensemble

| Domaine            | Choix                                                                 |
|--------------------|-----------------------------------------------------------------------|
| Langage            | Java 25 LTS (le `pom.xml` cible actuellement 27, non-LTS)             |
| Framework          | Spring Boot 4.1.x (Spring Framework 7)                                |
| Build              | Maven (wrapper `mvnw`)                                                |
| Base de données    | PostgreSQL + migrations Flyway                                        |
| Cache / sessions   | Redis                                                                 |
| Paiement           | PayPal (API REST Orders v2), architecture extensible multi-fournisseurs |
| Observabilité      | Micrometer → Prometheus → Grafana ; OpenTelemetry → Tempo ; logs → Loki |
| CI/CD              | GitHub Actions                                                        |
| Infrastructure     | OpenTofu                                                              |
| Conteneurisation   | Image OCI (Buildpacks via `spring-boot:build-image` ou Jib)           |

### Architecture logique

```
                ┌───────────────┐
  Navigateur ──▶│  Frontend     │
                └──────┬────────┘
                       │ REST
                ┌──────▼──────────────────────────────────────────┐
                │  ecom (Spring Boot)                             │
                │  ┌─────────┐ ┌───────┐ ┌────────┐ ┌───────────┐ │
                │  │Catalogue│ │Panier │ │Commande│ │ Paiement  │ │
                │  └─────────┘ └───────┘ └────────┘ └─────┬─────┘ │
                │                          PaymentProvider │ (port)│
                │                    ┌─────────────┬───────┴─────┐ │
                │                    │PayPalAdapter│ (futur:Stripe)│
                └────────────────────┴──────┬──────┴─────────────┘─┘
                       │            │       │ HTTPS + webhooks
                  PostgreSQL      Redis   PayPal API
                       │
          /actuator/prometheus ──▶ Prometheus ──▶ Grafana (dashboards + alertes)
          OTLP traces ──────────▶ Tempo
```

Organisation en **monolithe modulaire** (un package par domaine), plus simple à opérer qu'une architecture microservices au démarrage, et découpable plus tard. Les frontières entre modules peuvent être vérifiées avec **Spring Modulith**.

```
org.ecom
├── catalog/
├── cart/
├── order/
├── customer/
├── payment/
│   ├── domain/        # Payment, PaymentAttempt, PaymentStatus
│   ├── port/          # PaymentProvider (interface)
│   ├── application/   # PaymentService, PaymentProviderRegistry
│   └── adapter/
│       └── paypal/    # PayPalPaymentProvider, PayPalClient, PayPalWebhookController
└── shared/            # erreurs, config, observabilité
```

---

## 2. Dépendances Spring Boot

> Les versions sont gérées par `spring-boot-starter-parent` 4.1.x : ne pas préciser de `<version>` pour les starters.
> Spring Boot 4 a renommé/modularisé plusieurs starters par rapport à la 3.x (ex. `spring-boot-starter-web` → `spring-boot-starter-webmvc`, tests découpés par module). En cas de doute, vérifier sur [start.spring.io](https://start.spring.io).

### 2.1 Starters Spring Boot

| Dépendance (`org.springframework.boot`)          | Rôle                                                        | Étape |
|--------------------------------------------------|-------------------------------------------------------------|-------|
| `spring-boot-starter-webmvc`                     | API REST / MVC (Tomcat embarqué)                            | 2     |
| `spring-boot-starter-validation`                 | Bean Validation (`@Valid`, `@NotNull`…)                     | 2     |
| `spring-boot-starter-data-jpa`                   | Persistance JPA / Hibernate                                 | 2     |
| `spring-boot-starter-flyway`                     | Migrations de schéma versionnées                            | 2     |
| `spring-boot-starter-actuator`                   | Health, probes, métriques, info                             | 2     |
| `spring-boot-starter-security`                   | Sécurité (authN/authZ, CSRF, headers)                       | 3     |
| `spring-boot-starter-security-oauth2-resource-server` | Validation des JWT (si SPA + IdP type Keycloak)        | 3     |
| `spring-boot-starter-security-oauth2-client`     | Login OIDC (si frontend SSR)                                | 3     |
| `spring-boot-starter-data-redis`                 | Accès Redis (panier invité, cache)                          | 3     |
| `spring-boot-starter-session-data-redis`         | Sessions HTTP distribuées                                   | 3     |
| `spring-boot-starter-cache`                      | Abstraction de cache (`@Cacheable`) sur le catalogue        | 3     |
| `spring-boot-starter-thymeleaf`                  | Uniquement si frontend SSR                                  | 3     |
| `spring-boot-starter-mail`                       | E-mails transactionnels (confirmation de commande)          | 3     |
| `spring-boot-starter-restclient`                 | `RestClient` pour appeler l'API PayPal                      | 4     |
| `spring-boot-starter-opentelemetry`              | Traces + export OTLP                                        | 5     |
| `spring-boot-docker-compose` *(optional)*        | Démarre `compose.yaml` automatiquement en local             | 2     |
| `spring-boot-devtools` *(optional, runtime)*     | Rechargement à chaud en dev                                 | 2     |
| `spring-boot-configuration-processor` *(optional)* | Métadonnées des `@ConfigurationProperties`                | 2     |

### 2.2 Bibliothèques complémentaires

| Dépendance                                                    | Rôle                                                     | Étape |
|---------------------------------------------------------------|----------------------------------------------------------|-------|
| `org.postgresql:postgresql` *(runtime)*                       | Driver JDBC PostgreSQL                                   | 2     |
| `org.flywaydb:flyway-database-postgresql`                     | Support PostgreSQL pour Flyway                           | 2     |
| `io.micrometer:micrometer-registry-prometheus`                | Exposition `/actuator/prometheus`                        | 5     |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui`           | OpenAPI + Swagger UI (version à épingler, compatible Boot 4) | 2 |
| `org.springframework.modulith:spring-modulith-starter-core`   | Vérification des frontières entre modules + événements   | 2     |
| `io.github.resilience4j:resilience4j-spring-boot3` *(ou artefact Boot 4 dès dispo)* | Circuit breaker / bulkhead sur PayPal | 4 |
| `net.logstash.logback:logstash-logback-encoder` *(optionnel)* | Logs JSON — Boot 4 supporte nativement `logging.structured.format.console=ecs` | 5 |
| `org.mapstruct:mapstruct` + processor                         | Mapping entités ↔ DTO                                    | 3     |

> **Résilience** : Spring Framework 7 fournit nativement `@Retryable` et `@ConcurrencyLimit` (`@EnableResilientMethods`). Resilience4j reste utile pour le **circuit breaker**.
>
> **SDK PayPal** : on recommande d'appeler l'API REST directement via `RestClient` (contrôle total des timeouts, de l'idempotence et des métriques). Alternative : `com.paypal.sdk:paypal-server-sdk` (SDK officiel ; l'ancien `checkout-sdk` est déprécié).

### 2.3 Dépendances de test

| Dépendance                                         | Rôle                                         |
|----------------------------------------------------|----------------------------------------------|
| `spring-boot-starter-test`                         | JUnit 5, AssertJ, Mockito (déjà présent)     |
| `spring-boot-starter-webmvc-test`                  | `@WebMvcTest`, `MockMvc`                     |
| `spring-boot-starter-data-jpa-test`                | `@DataJpaTest`                               |
| `spring-boot-starter-security-test`                | `@WithMockUser`, support sécurité            |
| `spring-boot-testcontainers`                       | `@ServiceConnection`                         |
| `org.testcontainers:testcontainers-postgresql`     | PostgreSQL réel en test                      |
| `org.testcontainers:testcontainers-junit-jupiter`  | Intégration JUnit                            |
| `org.wiremock.integrations:wiremock-spring-boot`   | Simulation de l'API PayPal                   |
| `com.tngtech.archunit:archunit-junit5`             | Règles d'architecture (le domaine ne dépend pas des adapters) |
| `org.springframework.modulith:spring-modulith-starter-test` | Tests de modules                    |

### 2.4 Plugins Maven

| Plugin                                    | Rôle                                         |
|-------------------------------------------|----------------------------------------------|
| `spring-boot-maven-plugin`                | Jar exécutable + `build-image` (OCI)         |
| `maven-surefire-plugin` / `maven-failsafe-plugin` | Tests unitaires / d'intégration (`*IT`) |
| `jacoco-maven-plugin`                     | Couverture + seuil bloquant                  |
| `spotless-maven-plugin`                   | Formatage                                    |
| `spotbugs-maven-plugin`                   | Analyse statique                             |
| `cyclonedx-maven-plugin`                  | Génération du SBOM                           |

### 2.5 Exemple `pom.xml` (extrait)

```xml
<dependencies>
    <!-- Web & données -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-flyway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Sécurité -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- Paiement -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-restclient</artifactId>
    </dependency>

    <!-- Observabilité -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-opentelemetry</artifactId>
    </dependency>

    <!-- Tests -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 3. Persistance

- Schéma géré **uniquement** par Flyway (`spring.jpa.hibernate.ddl-auto=validate`) — scripts dans `src/main/resources/db/migration/V1__init.sql`, etc.
- Montants stockés en `NUMERIC(19,4)` + code devise ISO 4217 (jamais de `double`).
- Verrouillage optimiste (`@Version`) sur `Order`, `Payment`, `Stock`.
- Tables principales : `product`, `category`, `stock`, `customer`, `address`, `cart`, `cart_item`, `customer_order`, `order_line`, `payment`, `payment_attempt`, `webhook_event`.

---

## 4. Architecture du paiement (extensible)

### 4.1 Principe

Architecture **ports & adapters** : le domaine `order`/`payment` ne connaît que l'interface `PaymentProvider`. Chaque moyen de paiement est un adapter Spring découvert automatiquement.

```java
public interface PaymentProvider {

    /** Identifiant stable : "paypal", "stripe"... */
    String id();

    /** Initie le paiement ; renvoie éventuellement une URL de redirection/approbation. */
    PaymentInitiation initiate(PaymentRequest request);

    /** Finalise le paiement (capture) après approbation du client. */
    PaymentResult capture(PaymentReference reference);

    PaymentResult refund(PaymentReference reference, Money amount);

    /** Vérifie et traduit un webhook fournisseur en événement de domaine. */
    PaymentEvent parseWebhook(WebhookPayload payload);
}
```

```java
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentProvider::id, Function.identity()));
    }

    public PaymentProvider get(String id) {
        PaymentProvider provider = providers.get(id);
        if (provider == null) {
            throw new UnsupportedPaymentMethodException(id);
        }
        return provider;
    }
}
```

Activation par configuration (feature flag par fournisseur) :

```yaml
ecom:
  payment:
    providers:
      paypal:
        enabled: true
        base-url: https://api-m.sandbox.paypal.com   # live : https://api-m.paypal.com
        client-id: ${PAYPAL_CLIENT_ID}
        client-secret: ${PAYPAL_CLIENT_SECRET}
        webhook-id: ${PAYPAL_WEBHOOK_ID}
        connect-timeout: 2s
        read-timeout: 10s
```

L'adapter PayPal est annoté `@ConditionalOnBooleanProperty("ecom.payment.providers.paypal.enabled")`. **Ajouter Stripe = créer `adapter/stripe/` + sa configuration**, sans toucher au domaine.

### 4.2 Flux PayPal (Orders v2)

```
Client        ecom                         PayPal
  │ checkout    │                            │
  ├────────────▶│ POST /v2/checkout/orders   │
  │             ├───────────────────────────▶│  (PayPal-Request-Id = clé d'idempotence)
  │             │◀── orderId + lien approve ─┤
  │◀─ redirect ─┤                            │
  │ approuve sur PayPal ────────────────────▶│
  │◀──────────── retour returnUrl ───────────┤
  ├────────────▶│ POST /v2/checkout/orders/{id}/capture
  │             ├───────────────────────────▶│
  │             │◀──── COMPLETED / erreur ───┤
  │◀─ confirmation                           │
  │             │◀── webhook PAYMENT.CAPTURE.COMPLETED (source de vérité asynchrone)
```

### 4.3 Règles

- **Statuts** : `INITIATED → PENDING_APPROVAL → CAPTURED` ; `FAILED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`.
- **Idempotence** : chaque `PaymentAttempt` a une clé unique transmise à PayPal ; les webhooks sont dédoublonnés via `webhook_event.event_id` (contrainte unique).
- **Webhooks** : signature vérifiée (`/v1/notifications/verify-webhook-signature`) avant tout traitement ; endpoint exclu du CSRF mais pas de l'authentification de signature.
- **Réconciliation** : job `@Scheduled` qui interroge PayPal pour les paiements `PENDING_APPROVAL` > 15 min.
- **Résilience** : timeouts explicites, retry uniquement sur erreurs réseau/5xx et toujours avec la même clé d'idempotence, circuit breaker.
- **Sécurité** : aucune donnée carte ne transite par le backend ; secrets PayPal dans le secrets manager du cloud.

---

## 5. Observabilité & métriques

### 5.1 Configuration

```yaml
management:
  server:
    port: 8081                         # port de management non exposé publiquement
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  endpoint:
    health:
      probes:
        enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms, 300ms, 1s
  tracing:
    sampling:
      probability: 0.1                 # 1.0 en staging
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
logging:
  structured:
    format:
      console: ecs                     # logs JSON avec traceId/spanId
```

### 5.2 Métriques métier

```java
@Component
public class PaymentMetrics {

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAttempt(String provider, PaymentOutcome outcome, String reason) {
        registry.counter("ecom.payment.attempts",
                "provider", provider,
                "outcome", outcome.name().toLowerCase(),
                "reason", reason)          // valeurs bornées : ex. DECLINED, TIMEOUT, CANCELLED_BY_USER
                .increment();
    }
}
```

> Attention à la **cardinalité** des tags : jamais d'identifiant client/commande comme tag.

### 5.3 Indicateurs demandés (PromQL)

| Indicateur                                  | Requête                                                                                                                 |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Temps de réponse p95 par endpoint           | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))`                            |
| Requêtes par seconde par endpoint           | `sum by (uri) (rate(http_server_requests_seconds_count[1m]))`                                                           |
| Pages vues par minute                       | `sum by (page) (rate(ecom_page_views_total[5m])) * 60`                                                                  |
| Taux de paiements échoués                   | `sum(rate(ecom_payment_attempts_total{outcome="failed"}[15m])) / sum(rate(ecom_payment_attempts_total[15m]))`           |
| Taux d'échec par fournisseur                | `sum by (provider) (rate(ecom_payment_attempts_total{outcome="failed"}[15m])) / sum by (provider) (rate(ecom_payment_attempts_total[15m]))` |
| Taux d'erreurs 5xx                          | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))`  |

> Les **pages vues** côté navigateur (SPA) se mesurent mieux côté frontend (outil d'analytics respectueux du RGPD, ex. Matomo/Plausible) ; le compteur backend `ecom.page.views` couvre les pages servies/consultées via l'API.

### 5.4 Alertes (exemples)

- Taux d'échec paiement > 5 % sur 15 min → critique
- p95 > 300 ms sur 10 min → warning
- Taux 5xx > 1 % sur 5 min → critique
- Aucun webhook PayPal reçu depuis 1 h alors que des paiements sont initiés → warning

Dashboards et règles versionnés dans `observability/` et provisionnés automatiquement.

---

## 6. CI/CD (GitHub Actions)

### 6.1 Workflows

| Fichier                 | Déclencheur                    | Contenu                                                                                     |
|-------------------------|--------------------------------|---------------------------------------------------------------------------------------------|
| `ci.yml`                | PR + push `main`               | build, tests unitaires + intégration (Testcontainers), JaCoCo, Spotless, SpotBugs, CodeQL, Trivy (deps), build image + scan |
| `infra-ci.yml`          | PR modifiant `infra/**`        | `tofu fmt -check`, `tofu validate`, `tflint`, `trivy config`, `tofu plan` posté en commentaire de PR |
| `cd.yml`                | push `main`                    | push image (tag SHA) + cosign + SBOM → `tofu apply` staging → deploy → smoke/E2E → approbation → prod |
| `release.yml`           | push `main`                    | release-please : tag SemVer + changelog                                                     |

### 6.2 Bonnes pratiques

- Actions tierces **épinglées par SHA** de commit (mises à jour via Dependabot).
- `permissions:` minimales par workflow (`contents: read` par défaut, `id-token: write` seulement pour l'OIDC).
- Authentification cloud par **OIDC** — aucune clé longue durée dans les secrets GitHub.
- Environnements GitHub `staging` / `production` avec reviewers obligatoires et secrets par environnement.
- `concurrency:` pour empêcher deux déploiements simultanés du même environnement.
- La même image (même digest) est promue de staging vers production — jamais reconstruite.

### 6.3 Squelette `ci.yml`

```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@<sha>
      - uses: actions/setup-java@<sha>
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - run: ./mvnw -B verify          # surefire + failsafe + jacoco + spotless + spotbugs
      - run: ./mvnw -B spring-boot:build-image -DskipTests
```

---

## 7. Infrastructure (OpenTofu)

### 7.1 Arborescence

```
infra/
├── modules/
│   ├── network/
│   ├── database/        # PostgreSQL managé, backups, chiffrement
│   ├── cache/           # Redis managé
│   ├── registry/        # registre d'images
│   ├── runtime/         # Kubernetes ou conteneurs managés
│   ├── secrets/         # secrets PayPal, BDD
│   ├── dns-tls/
│   └── observability/
└── envs/
    ├── staging/
    │   ├── main.tf
    │   ├── backend.tf
    │   └── terraform.tfvars
    └── production/
```

### 7.2 Règles

- État distant avec verrouillage, **chiffré** via le bloc `encryption` natif d'OpenTofu (clé dans un KMS).
- `required_version` OpenTofu et versions des providers épinglées ; `.terraform.lock.hcl` committé.
- Modules versionnés, sans valeur codée en dur spécifique à un environnement.
- Aucun secret dans les `.tfvars` : valeurs créées dans le secrets manager, référencées par l'application.
- `tofu plan` sur chaque PR, `tofu apply` uniquement depuis la CD (jamais depuis un poste local en prod).
- Détection de dérive : workflow planifié quotidien `tofu plan -detailed-exitcode`.

```hcl
terraform {
  required_version = "~> 1.10"

  encryption {
    key_provider "<kms_provider>" "main" {
      # configuration KMS du cloud choisi
    }
    method "aes_gcm" "main" {
      keys = key_provider.<kms_provider>.main
    }
    state {
      method = method.aes_gcm.main
    }
    plan {
      method = method.aes_gcm.main
    }
  }
}
```

---

## 8. Environnements

| Environnement | PayPal            | Données           | Déploiement                     |
|---------------|-------------------|-------------------|---------------------------------|
| `local`       | Sandbox / WireMock| Docker Compose    | `./mvnw spring-boot:run`        |
| `staging`     | Sandbox           | Jeu de test       | Automatique sur merge `main`    |
| `production`  | Live              | Réelles           | Après approbation manuelle      |

---

## 9. Points ouverts

- Cloud cible (impacte les modules OpenTofu et l'auth OIDC)
- Frontend SSR (Thymeleaf) ou SPA séparée
- Runtime : Kubernetes vs conteneurs managés (Cloud Run, ECS, Azure Container Apps…)
- Fournisseur d'identité (Keycloak auto-hébergé vs service managé)
