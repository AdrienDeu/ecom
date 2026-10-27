import { Component, computed, inject, input, numberAttribute, resource } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { catalogCategories } from '../../api/fn/catalog-controller/catalog-categories';
import { catalogProducts } from '../../api/fn/catalog-controller/catalog-products';
import { BRAND } from '../../core/brand';
import { ProductKind } from '../../core/labels';
import { ProductGrid } from './product-grid';
import { ServiceList } from './service-list';

const PAGE_SIZE = 24;

const COPY: Record<ProductKind, { title: string; intro: string; search: string; empty: string }> = {
  SERVICE: {
    title: 'Forfaits de conseil',
    intro: 'Un périmètre défini, un prix fixe. Commandez en ligne : un consultant vous appelle pour planifier l’intervention.',
    search: 'Rechercher un forfait',
    empty: 'Aucun forfait ne correspond à votre recherche.',
  },
  PRODUCT: {
    title: 'Matériel informatique',
    intro: 'Postes de travail, écrans, réseau et accessoires sélectionnés pour un usage professionnel.',
    search: 'Rechercher un produit',
    empty: 'Aucun produit ne correspond à votre recherche.',
  },
};

/** Consulting packages (/conseil) or hardware (/materiel): the route sets the kind. */
@Component({
  selector: 'app-catalog-page',
  imports: [RouterLink, ProductGrid, ServiceList],
  template: `
    <header class="intro">
      <h1>{{ copy().title }}</h1>
      <p class="muted">{{ copy().intro }}</p>
    </header>

    <form class="row filters" role="search" (submit)="$event.preventDefault(); search(query.value, categorySelect.value)">
      <label class="grow">
        <span class="sr-only">{{ copy().search }}</span>
        <input #query type="search" [placeholder]="copy().search" [value]="q() ?? ''" />
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
      <button type="submit" class="btn secondary">Rechercher</button>
    </form>

    @if (products.error()) {
      <p class="alert error">Le catalogue ne peut pas être chargé. Vérifiez votre connexion puis rechargez la page.</p>
    } @else if (products.value(); as result) {
      @if (result.content.length === 0) {
        <p class="muted">{{ copy().empty }} <a [routerLink]="[]">Voir tout</a></p>
      } @else if (kind() === 'SERVICE') {
        <app-service-list [services]="result.content" />
      } @else {
        <app-product-grid [products]="result.content" />
      }
      @if (result.page.totalPages > 1) {
        <nav class="row pagination" aria-label="Pagination">
          <button type="button" class="btn secondary" [disabled]="result.page.number === 0" (click)="goToPage(result.page.number - 1)">
            Précédent
          </button>
          <span class="muted">Page {{ result.page.number + 1 }} sur {{ result.page.totalPages }}</span>
          <button type="button" class="btn secondary" [disabled]="result.page.number + 1 >= result.page.totalPages" (click)="goToPage(result.page.number + 1)">
            Suivant
          </button>
        </nav>
      }
    } @else {
      <p class="muted">Chargement…</p>
    }

    @if (kind() === 'SERVICE') {
      <aside class="custom card">
        <div>
          <h2>Besoin d’un accompagnement sur mesure ?</h2>
          <p class="muted">Décrivez votre projet, nous revenons vers vous sous {{ brand.responseTime }} avec une proposition.</p>
        </div>
        <a routerLink="/devis" class="btn">Demander un devis</a>
      </aside>
    }
  `,
  styles: `
    .intro { max-width: 44rem; margin-bottom: 2rem; }
    .intro h1 { margin-bottom: 0.5rem; }
    .intro p { margin: 0; font-size: 1.0625rem; }
    .filters { margin-bottom: 2rem; }
    .grow { flex: 1 1 16rem; }
    .pagination { justify-content: center; margin-top: 2.5rem; }
    .custom {
      display: grid;
      gap: 1rem 2rem;
      align-items: center;
      margin-top: 3rem;
      border-left: 4px solid var(--accent);
    }
    @media (min-width: 48rem) {
      .custom { grid-template-columns: 1fr auto; }
    }
    .custom h2 { margin: 0 0 0.25rem; font-size: 1.25rem; }
    .custom p { margin: 0; }
  `,
})
export class CatalogPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly brand = BRAND;

  /** From the route data. */
  readonly kind = input<ProductKind>('PRODUCT');
  /** Bound from the query string (?q=&category=&page=). */
  readonly q = input<string>();
  readonly category = input<string>();
  readonly page = input(0, { transform: (value: unknown) => numberAttribute(value, 0) });

  protected readonly copy = computed(() => COPY[this.kind()]);

  protected readonly categories = resource({
    params: () => ({ kind: this.kind() }),
    loader: ({ params }) => this.api.invoke(catalogCategories, params),
  });

  protected readonly products = resource({
    params: () => ({
      kind: this.kind(),
      q: this.q() || undefined,
      category: this.category() || undefined,
      page: this.page(),
    }),
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
