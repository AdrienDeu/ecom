import { CurrencyPipe } from '@angular/common';
import { Component, inject, input, numberAttribute, resource } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { catalogCategories } from '../../api/fn/catalog-controller/catalog-categories';
import { catalogProducts } from '../../api/fn/catalog-controller/catalog-products';

const PAGE_SIZE = 24;

@Component({
  selector: 'app-catalog-page',
  imports: [RouterLink, CurrencyPipe],
  template: `
    <h1>Catalogue</h1>

    <form class="row filters" role="search" (submit)="$event.preventDefault(); search(query.value, categorySelect.value)">
      <label class="grow">
        <span class="sr-only">Rechercher</span>
        <input #query type="search" placeholder="Rechercher un produit" [value]="q() ?? ''" />
      </label>
      <label>
        <span class="sr-only">Catégorie</span>
        <select #categorySelect [value]="category() ?? ''" (change)="search(query.value, categorySelect.value)">
          <option value="">Toutes les catégories</option>
          @for (c of categories.value() ?? []; track c.id) {
            <option [value]="c.slug">{{ c.name }}</option>
          }
        </select>
      </label>
      <button type="submit" class="btn">Rechercher</button>
    </form>

    @if (products.error()) {
      <p class="alert error">Le catalogue est momentanément indisponible.</p>
    } @else if (products.value(); as result) {
      @if (result.content.length === 0) {
        <p class="muted">Aucun produit ne correspond à votre recherche.</p>
      } @else {
        <ul class="grid">
          @for (product of result.content; track product.id) {
            <li class="card product">
              <a [routerLink]="['/products', product.slug]" class="product__link">
                <div class="product__image">
                  @if (product.imageUrl) {
                    <img [src]="product.imageUrl" [alt]="product.name" loading="lazy" />
                  }
                </div>
                <h2>{{ product.name }}</h2>
              </a>
              <div class="row spread">
                <span class="price">{{ product.price.amount | currency: product.price.currency }}</span>
                @if (!product.inStock) {
                  <span class="status">Rupture</span>
                }
              </div>
            </li>
          }
        </ul>
        @if (result.page.totalPages > 1) {
          <nav class="row pagination" aria-label="Pagination">
            <button type="button" class="btn secondary" [disabled]="result.page.number === 0" (click)="goToPage(result.page.number - 1)">
              Précédent
            </button>
            <span class="muted">Page {{ result.page.number + 1 }} / {{ result.page.totalPages }}</span>
            <button type="button" class="btn secondary" [disabled]="result.page.number + 1 >= result.page.totalPages" (click)="goToPage(result.page.number + 1)">
              Suivant
            </button>
          </nav>
        }
      }
    } @else {
      <p class="muted">Chargement…</p>
    }
  `,
  styles: `
    .filters { margin-bottom: 1.5rem; }
    .grow { flex: 1 1 16rem; }
    .grid {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fill, minmax(13rem, 1fr));
    }
    .product { display: grid; gap: 0.75rem; align-content: space-between; }
    .product__link { color: inherit; text-decoration: none; }
    .product__image {
      aspect-ratio: 4 / 3;
      border-radius: calc(var(--radius) - 0.25rem);
      background: var(--bg);
      overflow: hidden;
      img { width: 100%; height: 100%; object-fit: cover; }
    }
    h2 { font-size: 1rem; font-weight: 500; margin: 0.75rem 0 0; }
    .pagination { justify-content: center; margin-top: 2rem; }
  `,
})
export class CatalogPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  /** Bound from the query string (?q=&category=&page=). */
  readonly q = input<string>();
  readonly category = input<string>();
  readonly page = input(0, { transform: (value: unknown) => numberAttribute(value, 0) });

  protected readonly categories = resource({ loader: () => this.api.invoke(catalogCategories) });

  protected readonly products = resource({
    params: () => ({ q: this.q() || undefined, category: this.category() || undefined, page: this.page() }),
    loader: ({ params }) => this.api.invoke(catalogProducts, { ...params, size: PAGE_SIZE }),
  });

  protected search(q: string, category: string): void {
    this.navigate({ q: q.trim() || null, category: category || null, page: null });
  }

  protected goToPage(page: number): void {
    this.navigate({ page: page || null });
  }

  private navigate(queryParams: Record<string, string | number | null>): void {
    this.router.navigate([], { queryParams, queryParamsHandling: 'merge' });
  }
}
