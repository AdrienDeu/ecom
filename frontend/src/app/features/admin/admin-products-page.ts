import { CurrencyPipe } from '@angular/common';
import { Component, inject, input, resource } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { adminCatalogProducts } from '../../api/fn/admin-catalog-controller/admin-catalog-products';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-products-page',
  imports: [CurrencyPipe, RouterLink, AdminNav],
  template: `
    <h1>Back-office</h1>
    <app-admin-nav />
    <div class="row spread toolbar">
      <form role="search" class="row" (submit)="$event.preventDefault(); search(query.value)">
        <label><span class="sr-only">Rechercher</span><input #query type="search" placeholder="Nom du produit" [value]="q() ?? ''" /></label>
        <button type="submit" class="btn secondary">Rechercher</button>
      </form>
      <a routerLink="new" class="btn">Nouveau produit</a>
    </div>
    @if (products.value(); as page) {
      <div class="card table-scroll">
        <table>
          <thead>
            <tr><th>SKU</th><th>Nom</th><th>Prix</th><th>Stock (dispo / physique)</th><th>Statut</th></tr>
          </thead>
          <tbody>
            @for (p of page.content; track p.id) {
              <tr>
                <td><a [routerLink]="[p.id]">{{ p.sku }}</a></td>
                <td>{{ p.name }}</td>
                <td class="price">{{ p.price.amount | currency: p.price.currency }}</td>
                @if (p.stock.tracked) {
                  <td [class.low]="p.stock.available === 0">{{ p.stock.available }} / {{ p.stock.onHand }}</td>
                } @else {
                  <td class="muted">Prestation, sans stock</td>
                }
                <td><span class="status">{{ p.active ? 'En vente' : 'Désactivé' }}</span></td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="muted">Aucun produit.</td></tr>
            }
          </tbody>
        </table>
      </div>
    } @else if (products.error()) {
      <p class="alert error">Impossible de charger les produits.</p>
    }
  `,
  styles: `
    .toolbar { margin-bottom: 1rem; }
    .low { color: var(--danger); font-weight: 600; }
  `,
})
export class AdminProductsPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  readonly q = input<string>();

  protected readonly products = resource({
    params: () => ({ q: this.q() || undefined }),
    loader: ({ params }) => this.api.invoke(adminCatalogProducts, { ...params, size: 100 }),
  });

  protected search(q: string): void {
    this.router.navigate([], { queryParams: { q: q.trim() || null } });
  }
}
