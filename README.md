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
./mvnw verify                                             # build, tests, Spotless, SpotBugs, couverture (Docker requis pour Testcontainers)
./mvnw spotless:apply                                     # corriger l'ordre des imports et les espaces
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
| http://localhost:3000                   | Grafana : dashboards `ecom — API` et `ecom — Métier`, traces (Tempo) et logs (Loki) dans Explore (`GRAFANA_PORT` pour changer le port) |
| http://localhost:9000/ecom              | Serveur OIDC de test (remplace Cognito en local) |

Se connecter dans la SPA en local : la page de login du serveur OIDC de test demande un nom d'utilisateur et des claims. Pour un compte admin, saisir par exemple :

```json
{"client_id": "ecom-local", "token_use": "access", "cognito:groups": ["CUSTOMER", "ADMIN"]}
```

Paiement PayPal en local (désactivé par défaut) : créer une app sur le [dashboard développeur PayPal](https://developer.paypal.com/dashboard/applications/sandbox), puis lancer le backend avec ses identifiants sandbox. Les webhooks (`/api/webhooks/paypal`) exigent une URL publique (tunnel) et l'id du webhook créé dans l'app ; sans eux, la réconciliation finit de régler les paiements.

```bash
PAYPAL_ENABLED=true PAYPAL_CLIENT_ID=... PAYPAL_CLIENT_SECRET=... PAYPAL_WEBHOOK_ID=... \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
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

## Boutiques (branches `shop/*`)

`dev` et `main` portent la **plateforme** commune : catalogue, panier, commande, paiement, devis, back-office,
observabilité, infrastructure. Chaque boutique concrète vit dans sa propre branche longue durée
`shop/<nom>` (ex. `shop/relais-informatique`), qui n'ajoute que ce qui lui est propre :

- identité : `frontend/src/app/core/brand.ts` (nom, accroche, contact) ;
- design et pages : styles, page d'accueil, présentation du catalogue, textes ;
- catalogue de démonstration local (`src/main/resources/db/demo/`, profil `local`).

Règles :

- Une évolution utile à toutes les boutiques (fonctionnalité, correctif, sécurité) se fait **dans `dev`**, jamais
  dans une branche boutique.
- La plateforme descend dans les boutiques par une branche de synchronisation : `sync/<nom>` créée depuis
  `shop/<nom>`, dans laquelle on fusionne `dev`, puis PR `sync/<nom>` → `shop/<nom>` fusionnée en *merge commit*
  (pas de squash ni de rebase), pour garder l'historique commun et des fusions sans conflit. Une PR directe
  `dev` → `shop/<nom>` ne peut pas être « à jour » avec la boutique, comme l'exigent les checks obligatoires.

  ```bash
  git fetch origin
  git switch -c sync/<nom> origin/shop/<nom>
  git merge --no-ff origin/dev
  git push -u origin sync/<nom>
  gh pr create --base shop/<nom> --title "chore: sync platform from dev into shop/<nom>"
  ```
- Le travail propre à une boutique se fait dans `feat/<nom>-*` à partir de `shop/<nom>`, en PR vers cette branche.
- Nouvelle boutique : créer `shop/<nom>` depuis `dev`, puis personnaliser les fichiers ci-dessus.

## Documentation

- [Feuille de route](docs/TODO.md)
- [Documentation technique](docs/TECHNICAL.md)
- [Décisions d'architecture (ADR)](docs/adr/)
