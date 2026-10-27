import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CartStore } from '../../core/cart.store';
import { errorMessage } from '../../core/errors';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-cart-page',
  imports: [CurrencyPipe, RouterLink, Icon],
  template: `
    <h1>Panier</h1>
    @if (error(); as e) {
      <p class="alert error" role="alert">{{ e }}</p>
    }
    @if (store.cart(); as cart) {
      @if (cart.lines.length === 0) {
        <p class="muted">Votre panier est vide. <a routerLink="/">Continuer mes achats</a></p>
      } @else {
        <div class="card">
          <table class="lines">
            <thead>
              <tr>
                <th>Produit</th>
                <th>Prix unitaire</th>
                <th>Quantité</th>
                <th>Total</th>
                <th><span class="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              @for (line of cart.lines; track line.productId) {
                <tr [class.unavailable]="!line.available">
                  <td>
                    <a [routerLink]="['/products', line.slug]">{{ line.name }}</a>
                    @if (line.kind === 'SERVICE') {
                      <div class="muted kind">Prestation, planifiée après la commande</div>
                    }
                    @if (!line.available) {
                      <div class="field-error">Indisponible en quantité suffisante</div>
                    }
                  </td>
                  <td class="price" data-label="Prix unitaire">{{ line.unitPrice.amount | currency: line.unitPrice.currency }}</td>
                  <td data-label="Quantité">
                    <label>
                      <span class="sr-only">Quantité pour {{ line.name }}</span>
                      <input type="number" min="0" max="99" class="qty" [value]="line.quantity" [disabled]="busy()"
                             (change)="setQuantity(line.productId, $any($event.target).valueAsNumber)" />
                    </label>
                  </td>
                  <td class="price" data-label="Total">{{ line.lineTotal.amount | currency: line.lineTotal.currency }}</td>
                  <td class="remove">
                    <button type="button" class="link" [disabled]="busy()" (click)="remove(line.productId)"
                            [attr.aria-label]="'Retirer ' + line.name + ' du panier'" title="Retirer du panier">
                      <app-icon name="trash" />
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="row spread summary">
          <a routerLink="/">← Continuer mes achats</a>
          <div class="row">
            <span>Total <strong class="price">{{ cart.total.amount | currency: cart.total.currency }}</strong></span>
            <button type="button" class="btn" [disabled]="!cart.checkoutAllowed || busy()" (click)="checkout()">Commander</button>
          </div>
        </div>
        @if (!cart.checkoutAllowed) {
          <p class="muted">Retirez ou ajustez les articles indisponibles pour commander.</p>
        }
      }
    } @else {
      <p class="muted">Chargement…</p>
    }
  `,
  styles: `
    .qty { width: 5rem; }
    .remove { text-align: right; }
    .remove button { padding: 0.4rem; border-radius: var(--radius); }
    .remove button:hover:not(:disabled) { color: var(--danger); }
    /* Narrow screens: each line becomes a small card, the column headers move next to the values */
    @media (max-width: 40rem) {
      .lines thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
      .lines tr {
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 0.35rem 1rem;
        padding: 0.9rem 0;
        border-bottom: 1px solid var(--border);
      }
      .lines tr:last-child { border-bottom: 0; }
      .lines td { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0; border: 0; }
      .lines td:first-child { grid-column: 1; }
      .lines td.remove { grid-column: 2; grid-row: 1; align-items: start; }
      .lines td[data-label] { grid-column: 1 / -1; }
      .lines td[data-label]::before { content: attr(data-label); color: var(--text-muted); font-size: 0.875rem; font-weight: 400; }
    }
    .kind { font-size: 0.8125rem; }
    .unavailable td { opacity: 0.7; }
    .summary { margin-top: 1.5rem; }
  `,
})
export class CartPage {
  protected readonly store = inject(CartStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected setQuantity(productId: string, quantity: number): Promise<void> {
    if (!Number.isInteger(quantity) || quantity < 0) {
      return Promise.resolve();
    }
    return this.run(() => this.store.setQuantity(productId, quantity));
  }

  protected remove(productId: string): Promise<void> {
    return this.run(() => this.store.remove(productId));
  }

  protected checkout(): void {
    if (this.auth.isAuthenticated()) {
      this.router.navigate(['/checkout']);
    } else {
      // The guest cart is merged into the customer's cart on return from the identity provider
      this.auth.login('/checkout');
    }
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(errorMessage(error));
      await this.store.load().catch(() => undefined);
    } finally {
      this.busy.set(false);
    }
  }
}
