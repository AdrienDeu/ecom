import { CurrencyPipe } from '@angular/common';
import { Component, inject, input, resource, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { catalogProduct } from '../../api/fn/catalog-controller/catalog-product';
import { BRAND } from '../../core/brand';
import { CartStore } from '../../core/cart.store';
import { errorMessage } from '../../core/errors';
import { ProductVisual } from '../../shared/product-visual';
import { StockLed } from '../../shared/stock-led';

@Component({
  selector: 'app-product-page',
  imports: [CurrencyPipe, RouterLink, ProductVisual, StockLed],
  template: `
    @if (product.error()) {
      <p class="alert error">Ce produit n’existe pas ou n’est plus vendu.</p>
      <p><a routerLink="/conseil">Voir les forfaits de conseil</a> ou <a routerLink="/materiel">le matériel</a>.</p>
    } @else if (product.value(); as p) {
      @let service = p.kind === 'SERVICE';
      <nav class="crumbs" aria-label="Fil d’Ariane">
        <a [routerLink]="service ? '/conseil' : '/materiel'">{{ service ? 'Conseil' : 'Matériel' }}</a>
        @if (p.category) {
          <span aria-hidden="true">/</span>
          <a [routerLink]="service ? '/conseil' : '/materiel'" [queryParams]="{ category: p.category.slug }">{{ p.category.name }}</a>
        }
      </nav>

      <article class="layout" [class.layout--service]="service">
        @if (!service) {
          <app-product-visual class="visual" [name]="p.name" [imageUrl]="p.imageUrl" [categorySlug]="p.category?.slug" />
        }

        <div class="main">
          <h1>{{ p.name }}</h1>
          @if (p.description) {
            <p class="description">{{ p.description }}</p>
          }
          @if (service) {
            <section class="next">
              <h2>Après votre commande</h2>
              <p>
                Un consultant vous appelle sous {{ brand.responseTime }} pour préciser le périmètre et fixer les dates
                d’intervention, sur site ou à distance.
              </p>
            </section>
          } @else {
            <p class="muted sku">Réf. {{ p.sku }}</p>
          }
        </div>

        <aside class="buy card" aria-label="Commander">
          <p class="price big">{{ p.price.amount | currency: p.price.currency }}</p>
          <app-stock-led [kind]="p.kind" [inStock]="p.inStock" />
          @if (p.inStock) {
            <div class="row">
              <label>
                {{ service ? 'Nombre de forfaits' : 'Quantité' }}
                <input #qty type="number" min="1" max="99" value="1" class="qty" />
              </label>
              <button type="button" class="btn" [disabled]="adding()" (click)="addToCart(p.id, qty.valueAsNumber)">
                Ajouter au panier
              </button>
            </div>
          }
          @if (message(); as m) {
            <p class="alert" [class.error]="m.error" [class.success]="!m.error" role="status">
              {{ m.text }}
              @if (!m.error) {
                <a routerLink="/cart">Voir le panier</a>
              }
            </p>
          }
          @if (service) {
            <p class="adapt">
              Ce forfait ne correspond pas tout à fait à votre besoin ?
              <a routerLink="/devis" [queryParams]="{ sujet: p.name }">Demander un devis</a>
            </p>
          }
        </aside>
      </article>
    } @else {
      <p class="muted">Chargement…</p>
    }
  `,
  styles: `
    .crumbs { display: flex; gap: 0.5rem; margin-bottom: 1.5rem; font-size: 0.9375rem; color: var(--text-muted); }
    .crumbs a { color: var(--text-muted); }
    .layout { display: grid; gap: 2rem 3rem; align-items: start; }
    @media (min-width: 56rem) {
      .layout { grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); }
      .layout .buy { grid-column: 2; }
      .layout--service { grid-template-columns: minmax(0, 1.6fr) minmax(16rem, 1fr); }
      .layout--service .buy { position: sticky; top: 6rem; grid-row: 1; }
    }
    .visual { border-radius: var(--radius-lg); }
    .main { display: grid; gap: 1rem; align-content: start; }
    h1 { margin: 0; }
    .description { white-space: pre-line; margin: 0; max-width: 62ch; font-size: 1.0625rem; }
    .next { padding-top: 1.25rem; border-top: 1px solid var(--border); }
    .next h2 { margin: 0 0 0.5rem; font-size: 1.125rem; }
    .next p { margin: 0; max-width: 62ch; }
    .buy { display: grid; gap: 1rem; }
    .big { font-size: 2rem; font-weight: 750; font-stretch: 112%; margin: 0; }
    .qty { width: 6rem; }
    .row { align-items: end; }
    .adapt { margin: 0; padding-top: 1rem; border-top: 1px solid var(--border); font-size: 0.9375rem; color: var(--text-muted); }
    .sku { font-size: 0.8125rem; margin: 0; }
  `,
})
export class ProductPage {
  private readonly api = inject(Api);
  private readonly cart = inject(CartStore);
  private readonly title = inject(Title);
  protected readonly brand = BRAND;

  readonly slug = input.required<string>();

  protected readonly adding = signal(false);
  protected readonly message = signal<{ text: string; error: boolean } | null>(null);

  protected readonly product = resource({
    params: () => ({ slug: this.slug() }),
    loader: async ({ params }) => {
      const product = await this.api.invoke(catalogProduct, params);
      this.title.setTitle(`${product.name} | ${BRAND.name}`);
      return product;
    },
  });

  protected async addToCart(productId: string, quantity: number): Promise<void> {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99) {
      this.message.set({ text: 'Indiquez une quantité entre 1 et 99.', error: true });
      return;
    }
    this.adding.set(true);
    try {
      await this.cart.add(productId, quantity);
      this.message.set({ text: 'Ajouté au panier.', error: false });
    } catch (error) {
      this.message.set({ text: errorMessage(error), error: true });
    } finally {
      this.adding.set(false);
    }
  }
}
