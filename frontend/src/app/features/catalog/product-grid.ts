import { CurrencyPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductView } from '../../api/models/product-view';
import { ProductVisual } from '../../shared/product-visual';
import { StockLed } from '../../shared/stock-led';

@Component({
  selector: 'app-product-grid',
  imports: [CurrencyPipe, RouterLink, ProductVisual, StockLed],
  template: `
    <ul>
      @for (product of products(); track product.id) {
        <li>
          <app-product-visual [name]="product.name" [imageUrl]="product.imageUrl" [categorySlug]="product.category?.slug" />
          <h3><a [routerLink]="['/products', product.slug]">{{ product.name }}</a></h3>
          <div class="row spread">
            <span class="price">{{ product.price.amount | currency: product.price.currency }}</span>
            <app-stock-led [kind]="product.kind" [inStock]="product.inStock" />
          </div>
        </li>
      }
    </ul>
  `,
  styles: `
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 2rem 1rem;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 10rem), 1fr));
    }
    @media (min-width: 48rem) {
      ul { gap: 2rem 1.25rem; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); }
    }
    li { position: relative; display: grid; gap: 0.75rem; align-content: start; }
    h3 { margin: 0; font-size: 1rem; font-weight: 600; font-stretch: 100%; letter-spacing: 0; }
    h3 a { color: var(--text); text-decoration: none; }
    h3 a::after { content: ''; position: absolute; inset: 0; }
    li:hover h3 a { text-decoration: underline; }
    h3 a:focus-visible { outline: none; }
    li:has(a:focus-visible) { outline: 2px solid var(--accent); outline-offset: 4px; border-radius: var(--radius); }
  `,
})
export class ProductGrid {
  readonly products = input.required<ProductView[]>();
}
