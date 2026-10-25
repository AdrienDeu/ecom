# Documentation technique — ecom

## 1. Vue d'ensemble

| Domaine            | Choix                                                                 |
|--------------------|-----------------------------------------------------------------------|
| Langage            | Java 25 LTS                                                           |
| Framework          | Spring Boot 4.1.x (Spring Framework 7)                                |
| Build              | Maven (wrapper `mvnw`)                                                |
| Frontend           | SPA **Angular**, hébergée sur S3 + CloudFront                         |
| API                | REST stateless, authentification par JWT **Amazon Cognito**          |
| Région             | AWS `eu-west-3` (Paris)                                               |
| Base de données    | PostgreSQL (Amazon RDS) + migrations Flyway                           |
| Cache              | Valkey/Redis (Amazon ElastiCache)                                     |
| Paiement           | PayPal (API REST Orders v2), architecture extensible multi-fournisseurs |
| Observabilité      | Micrometer → Amazon Managed Prometheus → Amazon Managed Grafana ; OpenTelemetry (ADOT) → X-Ray ; logs → CloudWatch Logs |
| CI/CD              | GitHub Actions (OIDC vers AWS)                                        |
| Infrastructure     | OpenTofu sur AWS                                                      |
| Runtime            | ECS Fargate derrière un ALB — image dans ECR                          |
| Conteneurisation   | Image OCI (Buildpacks via `spring-boot:build-image` ou Jib)           |

### Architecture AWS

```
                         Route 53
                            │
                     ┌──────▼───────┐   AWS WAF
  Navigateur ───────▶│  CloudFront  │◀──────────
                     └──┬────────┬──┘
           /*  (statique)│        │ /api/*
                ┌────────▼──┐  ┌──▼──────────────────────────── VPC ─────────────┐
                │ S3 (SPA)  │  │  ALB (sous-réseaux publics)                      │
                └───────────┘  │   │                                              │
                               │  ┌▼─────────────────────────────────────────┐    │
                               │  │ ECS Fargate — ecom (Spring Boot)         │    │
                               │  │ Catalogue │ Panier │ Commande │ Paiement │    │
                               │  │                    PaymentProvider (port)│    │
                               │  │                    └ PayPalAdapter       │    │
                               │  └──┬──────────┬──────────┬────────────┬────┘    │
                               │     │          │          │ NAT        │ ADOT    │
                               │  RDS        ElastiCache   │            │ sidecar │
                               │  PostgreSQL (Valkey)      │            │         │
                               └───────────────────────────┼────────────┼─────────┘
                                                           ▼            ▼
                                     PayPal API (HTTPS + webhooks)   Managed Prometheus / X-Ray
                                                                       └▶ Managed Grafana
  Cognito User Pool : émission des JWT pour la SPA Angular
  Secrets Manager : secrets PayPal + BDD, injectés dans la tâche ECS
```

Servir SPA et API sous le **même domaine** via CloudFront (`/api/*` → ALB) évite la plupart des problèmes CORS et de cookies tiers.

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
| `spring-boot-starter-security-oauth2-resource-server` | Validation des JWT émis par Cognito                    | 3     |
| `spring-boot-starter-data-redis`                 | Accès Redis/Valkey (panier invité, cache)                   | 3     |
| `spring-boot-starter-cache`                      | Abstraction de cache (`@Cacheable`) sur le catalogue        | 3     |
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
| `io.awspring.cloud:spring-cloud-aws-starter-secrets-manager` *(optionnel)* | Lecture des secrets AWS via `spring.config.import` — inutile si ECS injecte les secrets en variables d'env | 7 |

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
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
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

## 3. Frontend Angular & authentification Cognito

### 3.1 Dépendances Angular (`frontend/package.json`)

| Paquet                                  | Rôle                                                       |
|-----------------------------------------|------------------------------------------------------------|
| `@angular/*` (dernière version stable)  | Framework (standalone components, signals, routing lazy)   |
| `angular-auth-oidc-client`              | OIDC Authorization Code + PKCE avec Cognito, refresh token |
| `@paypal/paypal-js`                     | Chargement du PayPal JS SDK (boutons PayPal)               |
| `ng-openapi-gen` *(dev)*                | Génération des services Angular typés depuis l'OpenAPI backend (pur Node, pas de JVM requise) |
| `angular-eslint` + `eslint` *(dev)*     | Lint                                                       |
| `vitest` + `jsdom` *(dev)*              | Tests unitaires (runner par défaut d'Angular)              |
| `@playwright/test` *(dev)*              | Tests E2E (exécutés en CD contre staging)                  |

Organisation : `core/` (auth, interceptors, config), `features/{catalog,cart,checkout,account,admin}/` en lazy loading, `shared/`, `api/` (client généré, non édité à la main).

- Un **interceptor HTTP** ajoute le jeton d'accès sur `/api/**` uniquement.
- **Guards** de route : `authGuard` (compte, checkout), `adminGuard` (groupe `ADMIN`).
- Configuration par environnement (`issuer`, `clientId`, `paypalClientId`) chargée au démarrage depuis `/config.json`, pour promouvoir le **même build** de staging à production.

### 3.2 Cognito

- **User Pool** en `eu-west-3`, un **client public** (sans secret) pour la SPA, flux Authorization Code + PKCE, Managed Login (pages de connexion hébergées par Cognito).
- **Groupes** `CUSTOMER` et `ADMIN` → claim `cognito:groups` dans les jetons.
- Issuer : `https://cognito-idp.eu-west-3.amazonaws.com/<user-pool-id>`
- Callback URLs : `https://<domaine>/auth/callback` (+ `http://localhost:4200/auth/callback` en staging uniquement).

### 3.3 Validation des JWT côté Spring

Les **access tokens** Cognito n'ont pas de claim `aud` mais un claim `client_id` : il faut le valider explicitement.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://cognito-idp.eu-west-3.amazonaws.com/${COGNITO_USER_POOL_ID}
ecom:
  security:
    cognito-client-id: ${COGNITO_CLIENT_ID}
```

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                    .requestMatchers("/api/webhooks/**", "/api/telemetry/**").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                    .jwtAuthenticationConverter(cognitoGroupsConverter())))
            .csrf(AbstractHttpConfigurer::disable)   // API stateless par Bearer token
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
}

@Bean
JwtAuthenticationConverter cognitoGroupsConverter() {
    JwtGrantedAuthoritiesConverter groups = new JwtGrantedAuthoritiesConverter();
    groups.setAuthoritiesClaimName("cognito:groups");
    groups.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(groups);
    return converter;
}
```

+ un `OAuth2TokenValidator<Jwt>` qui vérifie `token_use == "access"` et `client_id == ecom.security.cognito-client-id`.

---

## 4. Persistance

- Schéma géré **uniquement** par Flyway (`spring.jpa.hibernate.ddl-auto=validate`) — scripts dans `src/main/resources/db/migration/V1__init.sql`, etc.
- Montants stockés en `NUMERIC(19,4)` + code devise ISO 4217 (jamais de `double`).
- Verrouillage optimiste (`@Version`) sur `Order`, `Payment`, `Stock`.
- Tables principales : `product`, `category`, `stock`, `customer`, `address`, `cart`, `cart_item`, `customer_order`, `order_line`, `payment`, `payment_attempt`, `webhook_event`.

---

## 5. Architecture du paiement (extensible)

### 5.1 Principe

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

### 5.2 Flux PayPal (Orders v2)

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

### 5.3 Règles

- **Statuts** : `INITIATED → PENDING_APPROVAL → CAPTURED` ; `FAILED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`.
- **Idempotence** : chaque `PaymentAttempt` a une clé unique transmise à PayPal ; les webhooks sont dédoublonnés via `webhook_event.event_id` (contrainte unique).
- **Webhooks** : signature vérifiée (`/v1/notifications/verify-webhook-signature`) avant tout traitement ; endpoint public (sans JWT) mais rejeté si la signature est invalide. Exposé via ALB/CloudFront sur `/api/webhooks/paypal`.
- **Flux SPA** : la SPA appelle `POST /api/payments` → reçoit l'`orderId` PayPal → affiche les PayPal JS SDK Buttons → au `onApprove`, appelle `POST /api/payments/{id}/capture`. Le montant est **toujours** recalculé côté serveur à partir de la commande, jamais envoyé par la SPA.
- **Réconciliation** : job `@Scheduled` qui interroge PayPal pour les paiements `PENDING_APPROVAL` > 15 min.
- **Résilience** : timeouts explicites, retry uniquement sur erreurs réseau/5xx et toujours avec la même clé d'idempotence, circuit breaker.
- **Sécurité** : aucune donnée carte ne transite par le backend ; secrets PayPal dans le secrets manager du cloud.

---

## 6. Observabilité & métriques

### 6.1 Configuration

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

### 6.2 Métriques métier

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

### 6.3 Indicateurs demandés (PromQL)

| Indicateur                                  | Requête                                                                                                                 |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Temps de réponse p95 par endpoint           | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))`                            |
| Requêtes par seconde par endpoint           | `sum by (uri) (rate(http_server_requests_seconds_count[1m]))`                                                           |
| Pages vues par minute                       | `sum by (page) (rate(ecom_page_views_total[5m])) * 60`                                                                  |
| Taux de paiements échoués                   | `sum(rate(ecom_payment_attempts_total{outcome="failed"}[15m])) / sum(rate(ecom_payment_attempts_total[15m]))`           |
| Taux d'échec par fournisseur                | `sum by (provider) (rate(ecom_payment_attempts_total{outcome="failed"}[15m])) / sum by (provider) (rate(ecom_payment_attempts_total[15m]))` |
| Taux d'erreurs 5xx                          | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))`  |

> Avec une SPA, le serveur ne voit pas les changements de page (routage côté client). Deux options :
> - la SPA envoie un événement `POST /api/telemetry/page-view` (`{page: "product"}`, valeurs bornées) qui incrémente `ecom.page.views` → tout reste dans Prometheus/Grafana ;
> - ou un outil d'analytics respectueux du RGPD (Matomo, Plausible).
>
> Côté navigateur, mesurer aussi les **Core Web Vitals** (LCP, INP, CLS) — par ex. via CloudWatch RUM.

### 6.4 Collecte sur AWS

- Un **sidecar ADOT Collector** dans la tâche ECS scrape `/actuator/prometheus` (port 8081) et fait du `remote_write` vers **Amazon Managed Prometheus** ; il reçoit aussi les traces OTLP et les envoie à **X-Ray**.
- Logs JSON vers **CloudWatch Logs** (driver `awslogs`).
- **Amazon Managed Grafana** utilise Managed Prometheus, X-Ray et CloudWatch comme sources de données.
- Alarmes d'infrastructure (CPU ECS, connexions RDS, 5xx ALB) dans CloudWatch ; alertes métier dans Grafana/Prometheus.

### 6.5 Alertes (exemples)

- Taux d'échec paiement > 5 % sur 15 min → critique
- p95 > 300 ms sur 10 min → warning
- Taux 5xx > 1 % sur 5 min → critique
- Aucun webhook PayPal reçu depuis 1 h alors que des paiements sont initiés → warning

Dashboards et règles versionnés dans `observability/` et provisionnés automatiquement.

---

## 7. CI/CD (GitHub Actions)

### 7.1 Workflows

| Fichier                 | Déclencheur                    | Contenu                                                                                     |
|-------------------------|--------------------------------|---------------------------------------------------------------------------------------------|
| `ci.yml`                | PR + push `main`               | build, tests unitaires + intégration (Testcontainers), JaCoCo, Spotless, SpotBugs, CodeQL, Trivy (deps), build image + scan |
| `frontend-ci.yml`       | PR modifiant `frontend/**`     | `ng lint`, `ng test`, `ng build --configuration production`, `npm audit` / Trivy                                           |
| `infra-ci.yml`          | PR modifiant `infra/**`        | `tofu fmt -check`, `tofu validate`, `tflint`, `trivy config`, `tofu plan` posté en commentaire de PR |
| `cd.yml`                | push `main`                    | push image ECR (tag SHA) + cosign + SBOM → `tofu apply` staging → mise à jour du service ECS → SPA vers S3 + invalidation CloudFront → smoke/E2E → approbation → prod |
| `drift.yml`             | planifié (quotidien)           | `tofu plan -detailed-exitcode` par environnement, alerte en cas de dérive                   |

Filtrage par chemins (`paths:`) pour ne lancer que les workflows concernés dans le monorepo.
| `release.yml`           | push `main`                    | release-please : tag SemVer + changelog                                                     |

### 7.2 Bonnes pratiques

- Actions tierces **épinglées par SHA** de commit (mises à jour via Dependabot).
- `permissions:` minimales par workflow (`contents: read` par défaut, `id-token: write` seulement pour l'OIDC).
- Authentification AWS par **OIDC** (`aws-actions/configure-aws-credentials` + `role-to-assume`) — aucune clé d'accès AWS dans les secrets GitHub. La politique de confiance du rôle restreint `token.actions.githubusercontent.com:sub` au repo **et** à l'environnement (`repo:<owner>/ecom:environment:production`).
- Environnements GitHub `staging` / `production` avec reviewers obligatoires et secrets par environnement.
- `concurrency:` pour empêcher deux déploiements simultanés du même environnement.
- La même image (même digest) est promue de staging vers production — jamais reconstruite.

### 7.3 Squelette `ci.yml`

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

## 8. Infrastructure (OpenTofu)

### 8.1 Arborescence

```
infra/
├── bootstrap/           # bucket S3 d'état + clé KMS + OIDC GitHub (appliqué une fois)
├── modules/
│   ├── network/         # VPC, sous-réseaux, NAT, VPC endpoints
│   ├── database/        # RDS PostgreSQL, backups, chiffrement KMS
│   ├── cache/           # ElastiCache Valkey/Redis
│   ├── registry/        # ECR
│   ├── backend/         # ECS cluster, service Fargate, ALB, autoscaling, rôles IAM de tâche
│   ├── frontend/        # S3 + CloudFront (OAC)
│   ├── edge/            # Route 53, ACM, WAF
│   ├── identity/        # Cognito user pool + client SPA
│   ├── secrets/         # Secrets Manager (PayPal, BDD)
│   └── observability/   # Managed Prometheus, Managed Grafana, CloudWatch
└── envs/
    ├── staging/
    │   ├── main.tf
    │   ├── backend.tf
    │   └── terraform.tfvars
    └── production/
```

### 8.2 Règles

- État distant dans **S3** (versionné, chiffrement SSE-KMS, accès public bloqué) avec verrouillage natif S3 (`use_lockfile = true`, plus besoin de DynamoDB), et **chiffré côté client** via le bloc `encryption` natif d'OpenTofu (clé AWS KMS).
- Un compte AWS par environnement (recommandé), provider `aws` avec `default_tags` (`project`, `env`, `managed-by=opentofu`).
- `required_version` OpenTofu et versions des providers épinglées ; `.terraform.lock.hcl` committé.
- Modules versionnés, sans valeur codée en dur spécifique à un environnement.
- Aucun secret dans les `.tfvars` : valeurs créées dans le secrets manager, référencées par l'application.
- `tofu plan` sur chaque PR, `tofu apply` uniquement depuis la CD (jamais depuis un poste local en prod).
- Détection de dérive : workflow planifié quotidien `tofu plan -detailed-exitcode`.

```hcl
terraform {
  required_version = "~> 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket       = "ecom-tofu-state-<account-id>"
    key          = "staging/terraform.tfstate"
    region       = "eu-west-3"
    encrypt      = true
    use_lockfile = true
  }

  encryption {
    key_provider "aws_kms" "main" {
      kms_key_id = "alias/ecom-tofu-state"
      region     = "eu-west-3"
      key_spec   = "AES_256"
    }
    method "aes_gcm" "main" {
      keys = key_provider.aws_kms.main
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

## 9. Environnements

| Environnement | PayPal            | Données           | Déploiement                     |
|---------------|-------------------|-------------------|---------------------------------|
| `local`       | Sandbox / WireMock| Docker Compose    | `./mvnw spring-boot:run` + serveur de dev SPA |
| `staging`     | Sandbox           | Jeu de test       | Automatique sur merge `main` (compte AWS staging) |
| `production`  | Live              | Réelles           | Après approbation manuelle (compte AWS prod)      |

---

## 10. Décisions

| Sujet           | Décision                    | Statut      |
|-----------------|-----------------------------|-------------|
| Cloud           | AWS                         | ✅ Décidé   |
| Frontend        | SPA séparée                 | ✅ Décidé   |
| Java            | 25 LTS                      | ✅ Décidé   |
| IaC             | OpenTofu                    | ✅ Décidé   |
| Framework SPA   | Angular                     | ✅ Décidé   |
| Runtime backend | ECS Fargate                 | ✅ Décidé   |
| Identité        | Amazon Cognito              | ✅ Décidé   |
| Région AWS      | `eu-west-3` (Paris)         | ✅ Décidé   |
