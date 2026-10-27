import { CurrencyPipe } from '@angular/common';
import { Component, inject, input, resource, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { catalogProduct } from '../../api/fn/catalog-controller/catalog-product';
import { BRAND } from '../../core/brand';
import { CartStore } from '../../core/cart.store';
import { errorMessage } from '../../core/errors';

@Component({
  selector: 'app-product-page',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <a routerLink="/" class="back">← Catalogue</a>
    @if (product.error()) {
      <p class="alert error">Ce produit est introuvable ou n'est plus vendu.</p>
    } @else if (product.value(); as p) {
      <article class="layout">
        <div class="image card">
          @if (p.imageUrl) {
            <img [src]="p.imageUrl" [alt]="p.name" />
          }
        </div>
        <div class="stack">
          @if (p.category) {
            <a [routerLink]="['/']" [queryParams]="{ category: p.category.slug }" class="muted">{{ p.category.name }}</a>
          }
          <h1>{{ p.name }}</h1>
          <p class="price big">{{ p.price.amount | currency: p.price.currency }}</p>
          @if (p.description) {
            <p class="description">{{ p.description }}</p>
          }
          @if (p.kind === 'SERVICE') {
            <p class="muted">Prestation sur rendez-vous : nous vous contactons sous {{ brand.responseTime }} après la commande pour la planifier.</p>
          }
          @if (p.inStock) {
            <div class="row">
              <label>
                {{ p.kind === 'SERVICE' ? 'Nombre de prestations' : 'Quantité' }}
                <input #qty type="number" min="1" max="99" value="1" class="qty" />
              </label>
              <button type="button" class="btn" [disabled]="adding()" (click)="addToCart(p.id, qty.valueAsNumber)">
                Ajouter au panier
              </button>
            </div>
          } @else {
            <p class="status">Rupture de stock</p>
          }
          @if (message(); as m) {
            <p class="alert" [class.error]="m.error" [class.success]="!m.error" role="status">
              {{ m.text }}
              @if (!m.error) {
                <a routerLink="/cart">Voir le panier</a>
              }
            </p>
          }
          @if (p.kind === 'SERVICE') {
            <p class="muted">Besoin d’une prestation adaptée ? <a routerLink="/devis" [queryParams]="{ sujet: p.name }">Demander un devis</a></p>
          }
          <p class="muted sku">Réf. {{ p.sku }}</p>
        </div>
      </article>
    } @else {
      <p class="muted">Chargement…</p>
    }
  `,
  styles: `
    .back { display: inline-block; margin-bottom: 1.5rem; text-decoration: none; }
    .layout { display: grid; gap: 2rem; grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr)); align-items: start; }
    .image { aspect-ratio: 1; padding: 0; overflow: hidden; img { width: 100%; height: 100%; object-fit: cover; } }
    h1 { margin: 0; }
    .big { font-size: 1.5rem; margin: 0; }
    .description { white-space: pre-line; }
    .qty { width: 5rem; }
    .sku { font-size: 0.8125rem; }
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
      this.title.setTitle(product.name);
      return product;
    },
  });

  protected async addToCart(productId: string, quantity: number): Promise<void> {
    if (!Number.isInteger(quantity) || quantity < 1) {
      this.message.set({ text: 'Quantité invalide.', error: true });
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
