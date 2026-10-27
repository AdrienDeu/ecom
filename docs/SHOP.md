# Boutique — Relais (conseil et matériel informatique)

Branche `shop/relais-informatique`. Plateforme commune : voir le [README](../README.md#boutiques-branches-shop) ;
tout ce qui sert aux autres boutiques se fait dans `dev`.

## Positionnement

Prestataire informatique pour TPE et PME : **forfaits de conseil** à prix affiché (audit, cloud, DSI à temps
partagé, sensibilisation), commandés et payés en ligne, et **matériel** (postes, écrans, réseau, accessoires).
Les besoins hors forfait passent par le formulaire de devis.

## Ce que cette branche personnalise

| Élément | Fichier(s) |
|---|---|
| Nom (provisoire), accroche, contact | `frontend/src/app/core/brand.ts` |
| Design : papier gris-vert, encre, vert circuit imprimé, voyants ambre/vert ; police Archivo auto-hébergée | `frontend/src/styles.scss`, `frontend/src/app/app.scss` |
| Accueil (« On conseille. On équipe. », schéma réseau) | `frontend/src/app/features/home/` |
| Pages `/conseil` (grille tarifaire) et `/materiel` (grille, voyants de stock, visuels de catégorie) | `frontend/src/app/features/catalog/`, `frontend/src/app/shared/` |
| Textes des pages produit, devis, panier | `features/catalog/product-page.ts`, `features/quote/quote-page.ts`, `features/cart/cart-page.ts` |
| Catalogue de démonstration (6 forfaits, 10 produits), profil `local` uniquement | `src/main/resources/db/demo/`, `application-local.yaml` |

## À décider

- [ ] Nom définitif et adresse de contact
- [ ] Prix affichés HT ou TTC (clientèle professionnelle)
- [ ] Photos produits
