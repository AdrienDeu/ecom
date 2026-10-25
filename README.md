# ecom

Application e-commerce — backend Spring Boot, frontend Angular, paiement PayPal, déployée sur AWS avec OpenTofu.

[![CI](https://github.com/AdrienDeu/ecom/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/AdrienDeu/ecom/actions/workflows/ci.yml)
[![CodeQL](https://github.com/AdrienDeu/ecom/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/AdrienDeu/ecom/actions/workflows/codeql.yml)

## Structure

```
ecom/
├── src/              # backend Spring Boot (API REST)
├── frontend/         # SPA Angular
├── infra/            # OpenTofu (AWS)
│   ├── bootstrap/    # état distant + OIDC GitHub (appliqué une fois)
│   ├── modules/
│   └── envs/{staging,production}/
├── observability/    # dashboards Grafana, règles d'alerte
├── docs/             # feuille de route, doc technique, ADR
└── .github/          # workflows CI/CD, Dependabot, templates
```

## Prérequis

- Java 25 (Temurin)
- Docker (Testcontainers, Docker Compose local)
- Node.js 24 LTS (frontend)
- OpenTofu ≥ 1.10

## Démarrer

Backend (PostgreSQL, Valkey, Prometheus, Grafana et le serveur OIDC de test sont démarrés automatiquement via `compose.yaml`) :

```bash
./mvnw verify                                             # build + tests (Docker requis pour Testcontainers)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # lancer le backend
```

Frontend :

```bash
cd frontend
npm ci
npm start                # http://localhost:4200, /api proxifié vers le backend
npm test && npm run lint
```

| URL locale                              | Service                          |
|-----------------------------------------|----------------------------------|
| http://localhost:8080/api/swagger-ui    | Documentation de l'API           |
| http://localhost:8081/actuator/health   | Santé (port de management)       |
| http://localhost:9090                   | Prometheus                       |
| http://localhost:3000                   | Grafana (`GRAFANA_PORT` pour changer le port) |
| http://localhost:9000/ecom              | Serveur OIDC de test (remplace Cognito en local) |

Se connecter dans la SPA en local : la page de login du serveur OIDC de test demande un nom d'utilisateur et des claims. Pour un compte admin, saisir par exemple :

```json
{"client_id": "ecom-local", "token_use": "access", "cognito:groups": ["CUSTOMER", "ADMIN"]}
```

Obtenir un jeton admin en ligne de commande (claims définis dans `dev/mock-oidc.json`) :

```bash
curl -s -X POST localhost:9000/ecom/token \
  -d 'grant_type=client_credentials&client_id=ecom-local&client_secret=x&scope=openid' | jq -r .access_token
```

## Contribuer

- Branches : `main` (production), `dev` (intégration), `feat/*`, `fix/*`, `chore/*` à partir de `dev`.
- Commits et titres de PR au format [Conventional Commits](https://www.conventionalcommits.org/fr/).
- Toute modification passe par une PR avec CI verte.

## Documentation

- [Feuille de route](docs/TODO.md)
- [Documentation technique](docs/TECHNICAL.md)
- [Décisions d'architecture (ADR)](docs/adr/)
