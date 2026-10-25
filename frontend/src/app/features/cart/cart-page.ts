import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CartStore } from '../../core/cart.store';
import { errorMessage } from '../../core/errors';

@Component({
  selector: 'app-cart-page',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <h1>Panier</h1>
    @if (error(); as e) {
      <p class="alert error" role="alert">{{ e }}</p>
    }
    @if (store.cart(); as cart) {
      @if (cart.lines.length === 0) {
        <p class="muted">Votre panier est vide. <a routerLink="/">Continuer mes achats</a></p>
      } @else {
        <div class="card table-scroll">
          <table>
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
                    @if (!line.available) {
                      <div class="field-error">Indisponible en quantité suffisante</div>
                    }
                  </td>
                  <td class="price">{{ line.unitPrice.amount | currency: line.unitPrice.currency }}</td>
                  <td>
                    <label>
                      <span class="sr-only">Quantité pour {{ line.name }}</span>
                      <input type="number" min="0" max="99" class="qty" [value]="line.quantity" [disabled]="busy()"
                             (change)="setQuantity(line.productId, $any($event.target).valueAsNumber)" />
                    </label>
                  </td>
                  <td class="price">{{ line.lineTotal.amount | currency: line.lineTotal.currency }}</td>
                  <td><button type="button" class="link" [disabled]="busy()" (click)="remove(line.productId)">Retirer</button></td>
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
