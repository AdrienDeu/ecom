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
- Node.js LTS (frontend)
- OpenTofu ≥ 1.10

## Démarrer

```bash
./mvnw verify            # build + tests
./mvnw spring-boot:run   # lancer le backend
```

## Contribuer

- Branches : `main` (production), `dev` (intégration), `feat/*`, `fix/*`, `chore/*` à partir de `dev`.
- Commits et titres de PR au format [Conventional Commits](https://www.conventionalcommits.org/fr/).
- Toute modification passe par une PR avec CI verte.

## Documentation

- [Feuille de route](docs/TODO.md)
- [Documentation technique](docs/TECHNICAL.md)
- [Décisions d'architecture (ADR)](docs/adr/)
