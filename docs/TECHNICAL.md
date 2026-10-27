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
├── quote/             # demandes de devis (conseil sur mesure)
├── notification/      # e-mails à l'équipe : SMTP (Mailpit) en local, API SES en AWS
├── media/             # images produits : disque en local, bucket S3 en AWS, servies sous /media/*
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

Organisation : `core/` (auth, panier, config, erreurs, guards), `features/{catalog,cart,checkout,account,admin,auth}/` en lazy loading, `api/` (client généré, non édité à la main, exclu du lint).

- Composants standalone, **signals** et `resource()` pour les chargements, formulaires réactifs, locale `fr-FR`.
- L'interceptor `authInterceptor()` de la bibliothèque OIDC ajoute le jeton d'accès sur `/api/**` uniquement (`secureRoutes`).
- **Guards** de route : `authGuard` (compte, checkout → redirection vers Cognito puis retour à la page demandée), `adminGuard` (groupe `ADMIN` ; confort d'affichage uniquement, l'API applique le contrôle).
- Configuration par environnement chargée au démarrage depuis **`/config.json`** (`oidc.authority`, `oidc.clientId`, `oidc.scope`, `oidc.logoutUrl`) : le **même build** est promu de staging à production, seul ce fichier change (écrit au déploiement). En local il pointe vers le serveur OIDC de test.
- **Panier invité** : l'`X-Cart-Id` renvoyé par l'API est conservé en `localStorage` ; à la connexion, la page `/auth/callback` appelle `POST /api/cart/merge` puis renvoie vers la page demandée (`triggerAuthorizationResultEvent: true` désactive la redirection automatique de la bibliothèque).
- **Jetons** : stockés en `sessionStorage` (stockage par défaut de la bibliothèque, perdu à la fermeture de l'onglet), jamais en `localStorage` ; renouvellement par refresh token. L'URL de retour après login n'accepte que des chemins internes (pas d'open redirect).
- **Déconnexion Cognito** : Cognito ne publie pas d'`end_session_endpoint` ; `oidc.logoutUrl` (`https://<domaine>.auth.eu-west-3.amazoncognito.com/logout?client_id=…&logout_uri=…`) est utilisé après une déconnexion locale.
- **Client API** : `npm run generate:api` régénère `src/app/api` depuis `http://localhost:8080/api/openapi` (backend lancé). Le backend garantit des `operationId` uniques (`<controller><Méthode>`) et marque les propriétés de réponse non nulles comme `required` ; un test d'intégration protège ce contrat.

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
            .csrf(CsrfConfigurer::spa)   // cookie XSRF-TOKEN + en-tête X-XSRF-TOKEN, géré nativement par Angular
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

**CSRF** : mode SPA de Spring Security (double-submit cookie). Le serveur pose le cookie `XSRF-TOKEN`, le `HttpClient` d'Angular le renvoie dans l'en-tête `X-XSRF-TOKEN` sur les requêtes même origine. Les requêtes authentifiées par bearer token en sont exemptées par Spring Security (le jeton n'est pas envoyé automatiquement par le navigateur) ; les requêtes anonymes qui modifient l'état (panier invité) exigent le jeton. Les webhooks PayPal (étape 4) devront être explicitement exclus, leur authenticité étant vérifiée par signature.

---

## 4. Persistance

- Schéma géré **uniquement** par Flyway (`spring.jpa.hibernate.ddl-auto=validate`) — scripts dans `src/main/resources/db/migration/`. Une migration déjà mergée n'est jamais modifiée.
- Montants : `Money` (record embarqué) stocké en `NUMERIC(19,4)` + code devise ISO 4217, arrondi au centime (`HALF_EVEN`) ; jamais de `double`.
- Identifiants UUID v7 (triables dans le temps, générés par Hibernate).
- Verrouillage optimiste (`@Version`) sur `product`, `stock`, `customer_order` ; un conflit renvoie `409 CONCURRENT_MODIFICATION`.
- Les clients sont identifiés par le `sub` Cognito (`customer_id`) : pas de table client, les identités vivent dans Cognito.
- Les paniers ne sont pas en base : JSON dans Valkey/Redis (`cart:guest:<uuid>`, `cart:customer:<sub>`), TTL 30 jours.

| Migration | Tables |
|-----------|--------|
| `V1__catalog_and_inventory.sql` | `category`, `product`, `stock` (`on_hand`, `reserved`, contrainte `reserved <= on_hand`) |
| `V2__customer_address.sql` | `address` |
| `V3__orders.sql` | `customer_order` (statut, total, adresse figée, `expires_at`), `order_line` (snapshot sku/nom/prix) |
| `V6__services_and_quotes.sql` | `product.kind` (`PRODUCT` / `SERVICE`), `stock.tracked`, `quote_request` |
| `V7__order_line_kind.sql` | `order_line.kind` : type figé au checkout ; une commande sans produit à livrer expose `servicesOnly` (statuts affichés « Planifiée » / « Réalisée ») |

### 4.1 Stock et réservations

- `available = on_hand - reserved`.
- **Prestations de conseil** (`kind = SERVICE`) : stock non suivi (`tracked = false`), toujours disponible ; les mêmes requêtes de réservation, validation et libération s'y appliquent sans modifier les quantités, si bien que panier, checkout et annulation n'ont aucun cas particulier. Le stock d'une prestation ne se règle pas (`409 STOCK_NOT_TRACKED`).
- **Checkout** : chaque ligne fait un `UPDATE stock SET reserved = reserved + q WHERE on_hand - reserved >= q` ; 0 ligne modifiée → `409 INSUFFICIENT_STOCK` et rollback de toute la commande. Le verrou de ligne PostgreSQL rend la survente impossible, sans verrou applicatif.
- **Paiement** (`PAID`) : `on_hand -= q`, `reserved -= q`.
- **Annulation / expiration** : `reserved -= q`. Le job `OrderExpiryJob` (chaque minute) annule les commandes non payées après `ecom.order.reservation-ttl` (30 min) ; `FOR UPDATE SKIP LOCKED` permet de le faire tourner sur plusieurs tâches ECS sans double traitement.

### 4.2 API REST

| Méthode & chemin | Accès | Rôle |
|---|---|---|
| `GET /api/catalog/products?kind=&category=&q=&page=&size=&sort=` | public | Liste paginée des produits actifs (`kind` : `PRODUCT` ou `SERVICE`) |
| `GET /api/catalog/products/{slug}` | public | Fiche produit |
| `GET /api/catalog/categories?kind=` | public | Catégories (avec `kind` : seulement celles qui ont des produits actifs de ce type) |
| `GET\|POST /api/cart`, `/api/cart/items[/{productId}]` | public (`X-Cart-Id`) ou client | Panier |
| `POST /api/cart/merge` | client | Fusion du panier invité (`X-Cart-Id`) |
| `GET\|POST\|PUT\|DELETE /api/me/addresses[/{id}]` | client | Adresses |
| `POST /api/orders` | client | Checkout du panier (`{addressId}`) |
| `GET /api/orders[/{id}]`, `POST /api/orders/{id}/cancel` | client | Commandes du client |
| `/api/admin/categories`, `/api/admin/products[/{id}[/stock]]` | `ADMIN` | Back-office catalogue et stock |
| `POST /api/admin/products/{id}/image` (multipart, champ `file`) | `ADMIN` | Image du produit : JPEG, PNG ou WebP reconnus sur leurs premiers octets (pas de SVG), 5 Mo ; nouveau nom à chaque envoi, l'ancienne image envoyée est supprimée après le commit. `415 UNSUPPORTED_IMAGE`, `413` au-delà de 5 Mo |
| `GET /media/**` | public | Images envoyées : servies par l'application en local, par CloudFront depuis le bucket `media` en AWS |
| `GET /api/admin/orders?status=`, `POST /api/admin/orders/{id}/{ship\|deliver\|cancel}` | `ADMIN` | Back-office commandes |
| `POST /api/quote-requests` | public | Demande de devis (champ piège `website` contre les robots, limite de débit WAF par IP) |
| `GET /api/admin/quote-requests?status=`, `POST /api/admin/quote-requests/{id}/status` | `ADMIN` | Suivi des demandes de devis |

Erreurs : `ProblemDetail` ; les erreurs métier (409) portent un champ `code` stable (`INSUFFICIENT_STOCK`, `EMPTY_CART`, `INVALID_STATUS_TRANSITION`, `QUANTITY_TOO_HIGH`…) que la SPA peut traduire. Les ressources d'un autre client renvoient 404 (et non 403) pour ne pas révéler leur existence.

---

## 5. Architecture du paiement (extensible)

### 5.1 Principe

Architecture **ports & adapters** : le domaine `order`/`payment` ne connaît que l'interface `PaymentProvider`. Chaque moyen de paiement est un adapter Spring découvert automatiquement.

```java
public interface PaymentProvider {

    /** Identifiant stable : "paypal", "stripe"... */
    String id();

    /** Paramètres publics nécessaires à la SPA (ex. client id PayPal), jamais de secret. */
    Map<String, String> clientSettings();

    /** Crée le paiement chez le fournisseur, à faire approuver par le client. */
    PaymentInitiation initiate(PaymentRequest request);

    /** Finalise le paiement (capture) après approbation du client. */
    PaymentResult capture(PaymentReference reference, String idempotencyKey);

    /** État du paiement chez le fournisseur (job de réconciliation). */
    PaymentResult retrieve(PaymentReference reference);

    RefundResult refund(PaymentReference reference, Money amount, String idempotencyKey);

    /** Vérifie et traduit un webhook fournisseur en événement indépendant du fournisseur. */
    PaymentEvent parseWebhook(WebhookPayload payload);
}
```

Un adapter signale deux types d'échec : `PaymentProviderException` (fournisseur injoignable, issue inconnue → `503 PAYMENT_PROVIDER_UNAVAILABLE`, rejouable avec la même clé) et `PaymentRejectedException` (refus définitif).

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

La configuration PayPal (`PaypalConfiguration`) est annotée `@ConditionalOnBooleanProperty("ecom.payment.providers.paypal.enabled")` ; une fois activée, les identifiants sont obligatoires et validés au démarrage. **Ajouter Stripe = créer `adapter/stripe/` + sa configuration**, sans toucher au domaine.

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
- **Flux SPA** : la page de la commande charge `GET /api/payments/methods` (client id PayPal), puis la SPA appelle `POST /api/payments` → reçoit l'`orderId` PayPal (`providerReference`) → affiche les PayPal JS SDK Buttons → au `onApprove`, appelle `POST /api/payments/{id}/capture`. Le montant est **toujours** recalculé côté serveur à partir de la commande, jamais envoyé par la SPA.
- **Clés d'idempotence déterministes** : `initiate-{paymentId}` et `capture-{paymentId}` ; la capture peut venir du client, du webhook `CHECKOUT.ORDER.APPROVED` ou de la réconciliation, la même clé garantit qu'elle n'a lieu qu'une fois. Chaque appel est journalisé dans `payment_attempt` (opération, clé, issue).
- **Pas de transaction pendant les appels HTTP** : chaque étape (création du `Payment`, appel fournisseur, application du résultat) committe séparément (`TransactionTemplate`) ; le résultat est appliqué en relisant le paiement, le premier chemin qui le règle gagne, les autres deviennent sans effet.
- **Commande expirée ou annulée pendant l'approbation** : la capture n'est pas tentée (paiement `CANCELLED`) ; si l'argent a malgré tout été capturé (webhook), il est **remboursé automatiquement** (clé `compensation-{paymentId}`).
- **Un seul paiement vivant par commande** (index unique partiel hors `FAILED`/`CANCELLED`) : un paiement refusé laisse la commande en `CREATED`, le client peut réessayer.
- **Remboursements** : back-office `POST /api/admin/payments/{id}/refund` (total par défaut, partiel avec `amount`) ; les remboursements faits depuis le dashboard PayPal sont enregistrés via le webhook `PAYMENT.CAPTURE.REFUNDED`, dédoublonnés par id de remboursement. La commande passe `REFUNDED` quand il ne reste rien à rembourser.
- **Réconciliation** : job `@Scheduled` (toutes les 5 min, `SKIP LOCKED` multi-instances) qui interroge PayPal pour les paiements ouverts depuis plus de 15 min : approuvé → capture, capturé → commande payée, abandonné sur une commande expirée → annulé.
- **Résilience** : timeouts explicites (2 s connexion, 10 s lecture), 2 retries (backoff 200 ms ×2) uniquement sur erreurs réseau, 5xx, 429 et 401 (jeton renouvelé) avec la même clé d'idempotence (`RetryTemplate` de Spring Framework 7), circuit breaker Resilience4j (50 % d'échecs sur 20 appels → ouvert 30 s). Jeton OAuth2 mis en cache jusqu'à 1 min avant son expiration.
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
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms, 300ms, 1s   # buckets exacts aux seuils des SLO
  tracing:
    sampling:
      probability: 0.1                 # 1.0 en local et en staging
  otlp:
    metrics:
      export:
        enabled: false                 # métriques scrapées par Prometheus, pas poussées en OTLP
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces   # sidecar ADOT en AWS, Tempo en local
    logging:
      export:
        otlp:
          endpoint: http://localhost:3100/otlp/v1/logs # local uniquement (Loki)
logging:
  structured:
    format:
      console: ecs                     # staging/prod : logs JSON avec traceId/spanId → CloudWatch
```

- **Traces** : `spring-boot-starter-opentelemetry` (pont Micrometer Tracing → OpenTelemetry, export OTLP). Les requêtes `/actuator/**` (healthchecks, scrapes) ne produisent pas de trace (`ObservabilityConfig`).
- **Exemplars** : chaque bucket de latence Prometheus porte un `trace_id` échantillonné ; dans Grafana, un point du graphe de latence ouvre la trace dans Tempo.
- **Logs** : `traceId`/`spanId` dans chaque ligne (MDC). En local, l'appender Logback OpenTelemetry (`OpenTelemetryLogsConfig`, activé seulement si un endpoint OTLP de logs est défini) les envoie à Loki, où Grafana relie log ↔ trace.

| URL locale (`compose.yaml`)  | Service                              |
|------------------------------|--------------------------------------|
| http://localhost:9090        | Prometheus (règles d'alerte chargées depuis `observability/alerts/`) |
| http://localhost:3000        | Grafana : dashboards `ecom — API`, `ecom — Métier`, Explore Tempo/Loki |
| http://localhost:3200        | Tempo (API)                          |
| http://localhost:3100        | Loki (API)                           |

### 6.2 Métriques métier

| Métrique (Prometheus)                    | Type      | Tags                                  | Source |
|------------------------------------------|-----------|---------------------------------------|--------|
| `ecom_page_views_total`                  | counter   | `page` (liste fermée `PageType`)      | `POST /api/telemetry/page-views` envoyé par la SPA à chaque navigation |
| `ecom_payment_attempts_total`            | counter   | `provider`, `outcome` (`success`, `failed`, `cancelled`), `reason` | paiement arrivé à une issue définitive |
| `ecom_payment_duration_seconds`          | histogram | `provider`, `operation` (`initiate`, `capture`, `retrieve`, `refund`), `outcome` (`succeeded`, `rejected`, `error`) | chaque appel au fournisseur |
| `ecom_payment_webhooks_total`            | counter   | `provider`, `type`, `result` (`processed`, `duplicate`, `rejected`) | webhooks reçus |
| `ecom_payment_refunds_total`             | counter   | `provider`                            | remboursements enregistrés |
| `ecom_orders_placed_total`               | counter   | —                                     | checkout validé (après commit) |
| `ecom_quote_requests_total`              | counter   | `outcome` (`received`, `spam`)        | demandes de devis (`spam` : champ piège rempli, rien n'est enregistré) |
| `ecom_notifications_total`               | counter   | `type` (`quote_request`), `outcome` (`sent`, `failed`) | e-mails à l'équipe, envoyés après la transaction sur un autre thread : un échec n'affecte jamais la requête |
| `ecom_orders_expired_total`              | counter   | —                                     | commande non payée annulée à l'expiration de sa réservation : l'indicateur d'**abandon au paiement** (le panier lui-même n'est qu'une clé temporaire dans Valkey) |

> Attention à la **cardinalité** des tags : jamais d'identifiant client/commande comme tag. Les raisons d'échec sont les codes du fournisseur (`INSTRUMENT_DECLINED`…) ; toute autre valeur devient `OTHER`. Les pages vues sont une liste fermée : une valeur inconnue est refusée (400).
>
> Ne pas nommer un compteur `*.created` : le client Prometheus retire ce suffixe, réservé par OpenMetrics.

### 6.3 Indicateurs demandés (PromQL)

| Indicateur                                  | Requête                                                                                                                 |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Temps de réponse p95 par endpoint           | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))`                            |
| Requêtes par seconde par endpoint           | `sum by (uri) (rate(http_server_requests_seconds_count[1m]))`                                                           |
| Pages vues par minute                       | `sum by (page) (rate(ecom_page_views_total[5m])) * 60`                                                                  |
| Taux d'abandon au paiement                  | `sum(increase(ecom_orders_expired_total[1d])) / sum(increase(ecom_orders_placed_total[1d]))`                            |
| Taux de paiements échoués                   | `sum(rate(ecom_payment_attempts_total{outcome="failed"}[15m])) / sum(rate(ecom_payment_attempts_total[15m]))`           |
| Taux d'échec par fournisseur                | `sum by (provider) (rate(ecom_payment_attempts_total{outcome="failed"}[15m])) / sum by (provider) (rate(ecom_payment_attempts_total[15m]))` |
| Taux d'erreurs 5xx                          | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))`  |

> Avec une SPA, le serveur ne voit pas les changements de page (routage côté client) : la SPA envoie `POST /api/telemetry/page-views` (`{"page": "PRODUCT"}`) à chaque navigation (`PageViewTracker`). Seul le type de page est transmis, jamais l'URL ni l'identité du visiteur. L'endpoint est public : un rate limiting AWS WAF le protégera de l'inflation artificielle (étape 9).
>
> Côté navigateur, mesurer aussi les **Core Web Vitals** (LCP, INP, CLS) — par ex. via CloudWatch RUM.

### 6.4 Collecte sur AWS

- Un **sidecar ADOT Collector** dans la tâche ECS scrape `/actuator/prometheus` (port 8081) et fait du `remote_write` vers **Amazon Managed Prometheus** ; il reçoit aussi les traces OTLP et les envoie à **X-Ray**.
- Logs JSON vers **CloudWatch Logs** (driver `awslogs`).
- **Amazon Managed Grafana** utilise Managed Prometheus, X-Ray et CloudWatch comme sources de données.
- Alarmes d'infrastructure (CPU ECS, connexions RDS, 5xx ALB) dans CloudWatch ; alertes métier dans Grafana/Prometheus.

### 6.5 Alertes

Règles Prometheus versionnées dans `observability/alerts/ecom.yml`, testées avec `promtool test rules` (`observability/alerts/tests/`) :

| Alerte                          | Condition                                                                 | Sévérité |
|---------------------------------|---------------------------------------------------------------------------|----------|
| `EcomDown`                      | cible de scrape injoignable depuis 1 min                                  | critique |
| `EcomHighErrorRate`             | > 1 % de 5xx sur 5 min                                                    | critique |
| `EcomSlowResponses`             | p95 > 300 ms pendant 10 min                                               | warning  |
| `EcomPaymentFailureRateHigh`    | > 5 % de paiements refusés sur 15 min (au moins 10 paiements), par fournisseur | critique |
| `EcomPaymentProviderErrors`     | appels au fournisseur en erreur (injoignable, 5xx) pendant 5 min          | critique |
| `EcomPaymentWebhooksRejected`   | > 5 webhooks à signature invalide en 15 min                               | warning  |
| `EcomPaymentWebhooksMissing`    | paiements initiés mais aucun webhook depuis 1 h                           | warning  |

En local, Prometheus évalue les règles (onglet *Alerts*) sans Alertmanager. En AWS, elles sont chargées dans Amazon Managed Prometheus, qui notifie via SNS (étape 7).

Dashboards Grafana versionnés dans `observability/grafana/dashboards/` et provisionnés automatiquement : **ecom — API** (SLO, latence p50/p95/p99 avec exemplars, trafic par endpoint, statuts, ressources) et **ecom — Métier** (paiements, appels fournisseur, webhooks, commandes, abandon, pages vues). Modifier un dashboard dans Grafana puis exporter son JSON dans le dépôt.

---

## 7. CI/CD (GitHub Actions)

### 7.1 Workflows

| Fichier          | Déclencheur                         | Contenu |
|------------------|-------------------------------------|---------|
| `ci.yml`         | PR + push `main`/`dev`              | jobs parallèles, détaillés ci-dessous |
| `codeql.yml`     | PR + push + hebdomadaire            | CodeQL `security-extended` : Java, TypeScript, workflows GitHub Actions |
| `infra-ci.yml`   | PR modifiant `infra/**`             | `tofu fmt -check`, `tofu validate` (sans backend), `tflint`, `trivy config` ; `tofu plan` de chaque environnement commenté dans la PR (rôle en lecture seule via OIDC) |
| `cd.yml`         | push `main`, manuel                 | build unique de l'image (archive + digest) et de la SPA, puis `deploy.yml` en staging, puis en production après approbation |
| `deploy.yml`     | appelé par `cd.yml`                 | push ECR (tag `sha-<commit>`, digest vérifié) + signature cosign + attestation SBOM → `tofu apply` → attente du déploiement ECS (échec si rollback) → SPA vers S3 + invalidation CloudFront → smoke tests |
| `drift.yml`      | planifié (quotidien), manuel        | `tofu plan -detailed-exitcode` par environnement (rôle lecture seule) ; échec en cas de dérive |
| `release.yml`    | push `main` (étape 8)               | release-please : tag SemVer + changelog |

Jobs de `ci.yml` :

| Job                                         | Contenu | Bloquant |
|---------------------------------------------|---------|----------|
| **Backend (build & tests)**                 | `./mvnw verify` : Spotless (imports, espaces), tests unitaires + intégration (Testcontainers, WireMock), SpotBugs + FindSecBugs, couverture JaCoCo fusionnée (seuils : 80 % des lignes, 70 % des branches), résumé de couverture dans la PR | oui (ruleset) |
| **Frontend (lint, test, build)**            | `npm audit` (high), ESLint, Vitest, build de production | oui (ruleset) |
| API client up to date                       | régénère le client Angular depuis l'OpenAPI écrit par `EcomApplicationIT` et échoue s'il diffère du client commité | oui |
| Security (dependencies & secrets)           | Trivy `sbom` sur le SBOM CycloneDX généré par Maven (arbre exact des dépendances, sans requêtes vers Maven Central qui limite les runners partagés), Trivy `fs` pour npm, secrets et misconfigurations → onglet Security (SARIF) ; échec sur HIGH/CRITICAL corrigeable ou secret | oui |
| Image OCI (build & scan)                    | `spring-boot:build-image` (Buildpacks Paketo) + Trivy `image` → SARIF ; échec sur CRITICAL corrigeable | oui |
| Observability (alert rules & dashboards)    | `promtool check rules` + `promtool test rules`, validité des dashboards | oui |

Le frontend reste dans `ci.yml` plutôt que dans un workflow filtré par chemins : un check obligatoire d'un workflow non déclenché resterait « en attente » et bloquerait les PR qui ne touchent pas `frontend/`. `infra-ci.yml` est filtré par chemins et n'est donc pas obligatoire.

En local : `./mvnw verify` exécute les mêmes contrôles que le job backend ; `./mvnw spotless:apply` corrige l'ordre des imports. Les faux positifs SpotBugs sont écartés dans `spotbugs-exclude.xml`, chaque exclusion étant justifiée.

### 7.2 Bonnes pratiques

- Actions tierces **épinglées par SHA** de commit (mises à jour via Dependabot). Les images d'outils lancées par `docker run` (Trivy, promtool) sont épinglées par digest dans `env:` ; Dependabot ne les suit pas, elles sont mises à jour à la main.
- `permissions:` minimales par workflow (`contents: read` par défaut, `id-token: write` seulement pour l'OIDC).
- Authentification AWS par **OIDC** (`aws-actions/configure-aws-credentials` + `role-to-assume`) — aucune clé d'accès AWS dans les secrets GitHub. La politique de confiance du rôle restreint `token.actions.githubusercontent.com:sub` au repo **et** à l'environnement (`repo:<owner>/ecom:environment:production`).
- Environnements GitHub `staging` / `production` avec reviewers obligatoires et secrets par environnement.
- `concurrency:` pour empêcher deux déploiements simultanés du même environnement.
- La même image (même digest) est promue de staging vers production — jamais reconstruite.

---

## 8. Infrastructure (OpenTofu)

### 8.1 Arborescence

```
infra/
├── bootstrap/           # bucket S3 d'état + clé KMS + OIDC GitHub (appliqué une fois)
├── modules/
│   ├── platform/        # un environnement complet, seul module appelé par envs/*
│   ├── network/         # VPC, sous-réseaux, NAT, VPC endpoints
│   ├── database/        # RDS PostgreSQL, backups, chiffrement KMS
│   ├── cache/           # ElastiCache Valkey/Redis
│   ├── registry/        # ECR
│   ├── backend/         # ECS cluster, service Fargate, ALB, autoscaling, rôles IAM de tâche
│   ├── frontend/        # S3 + CloudFront (OAC)
│   ├── edge/            # ACM et WAF de CloudFront (us-east-1)
│   ├── identity/        # Cognito user pool + client SPA
│   ├── secrets/         # Secrets Manager (PayPal ; BDD via le secret géré par RDS)
│   └── observability/   # Managed Prometheus, Managed Grafana, CloudWatch
└── envs/
    ├── staging/
    │   ├── main.tf
    │   ├── backend.tf
    │   └── terraform.tfvars
    └── production/
```

Procédure d'amorçage et de premier déploiement : [infra/README.md](../infra/README.md).

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
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.68.0"
    }
  }

  # bucket fourni à l'init : tofu init -backend-config="bucket=ecom-tofu-state-<account-id>"
  backend "s3" {
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
      method   = method.aes_gcm.main
      enforced = true
    }
    plan {
      method   = method.aes_gcm.main
      enforced = true
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
