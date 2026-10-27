import { CurrencyPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductView } from '../../api/models/product-view';
import { summary } from './summary';

/** Consulting packages as a price sheet: one row per package, the price aligned on the right. */
@Component({
  selector: 'app-service-list',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <ul>
      @for (service of services(); track service.id) {
        <li>
          <div class="what">
            <h3><a [routerLink]="['/products', service.slug]">{{ service.name }}</a></h3>
            <p class="muted">{{ teaser(service.description) }}</p>
          </div>
          @if (service.category) {
            <span class="area">{{ service.category.name }}</span>
          }
          <span class="price">{{ service.price.amount | currency: service.price.currency : 'symbol' : '1.0-2' }}</span>
        </li>
      }
    </ul>
  `,
  styles: `
    ul { list-style: none; margin: 0; padding: 0; border-top: 2px solid var(--text); }
    li {
      position: relative;
      display: grid;
      gap: 0.25rem 2rem;
      grid-template-columns: 1fr auto;
      align-items: baseline;
      padding: 1.25rem 0.25rem;
      border-bottom: 1px solid var(--border);
    }
    li:hover { background: color-mix(in srgb, var(--accent-soft) 55%, transparent); }
    @media (min-width: 48rem) {
      li { grid-template-columns: minmax(0, 1fr) 12rem 8rem; padding-inline: 0.75rem; }
      .price { text-align: right; }
    }
    h3 { margin: 0; font-size: 1.125rem; }
    h3 a { color: var(--text); text-decoration: none; }
    /* The whole row is clickable, the link stays the only focusable element */
    h3 a::after { content: ''; position: absolute; inset: 0; }
    h3 a:focus-visible { outline: none; }
    li:has(a:focus-visible) { outline: 2px solid var(--accent); outline-offset: -2px; }
    p { margin: 0.35rem 0 0; max-width: 60ch; font-size: 0.9375rem; }
    .area { font-size: 0.875rem; color: var(--text-muted); grid-column: 1; grid-row: 2; }
    .price { font-size: 1.125rem; grid-column: 2; grid-row: 1; }
    @media (min-width: 48rem) {
      .area { grid-column: 2; grid-row: 1; }
      .price { grid-column: 3; }
    }
  `,
})
export class ServiceList {
  readonly services = input.required<ProductView[]>();

  protected teaser(description: string | null | undefined): string {
    return summary(description);
  }
}
