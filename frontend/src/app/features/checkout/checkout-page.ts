import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, resource, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { addressCreate } from '../../api/fn/address-controller/address-create';
import { addressList } from '../../api/fn/address-controller/address-list';
import { orderCheckout } from '../../api/fn/order-controller/order-checkout';
import { PostalAddress } from '../../api/models/postal-address';
import { CartStore } from '../../core/cart.store';
import { errorMessage } from '../../core/errors';
import { AddressForm } from '../account/address-form';

@Component({
  selector: 'app-checkout-page',
  imports: [CurrencyPipe, RouterLink, AddressForm],
  template: `
    <h1>Finaliser la commande</h1>
    @if (error(); as e) {
      <p class="alert error" role="alert">{{ e }}</p>
    }
    <div class="layout">
      <section class="card stack" aria-labelledby="shipping">
        <h2 id="shipping">{{ servicesOnly() ? 'Adresse de facturation' : 'Adresse de livraison et de facturation' }}</h2>
        @if (addresses.value(); as list) {
          @if (list.length > 0 && !adding()) {
            <fieldset class="stack">
              <legend class="sr-only">Choisir une adresse</legend>
              @for (a of list; track a.id) {
                <label class="choice">
                  <input type="radio" name="address" [value]="a.id" [checked]="a.id === selectedId()" (change)="selectedId.set(a.id)" />
                  <span>
                    <strong>{{ a.address.fullName }}</strong><br />
                    {{ a.address.line1 }}@if (a.address.line2) {, {{ a.address.line2 }}}<br />
                    {{ a.address.postalCode }} {{ a.address.city }}, {{ a.address.country }}
                  </span>
                </label>
              }
            </fieldset>
            <button type="button" class="link" (click)="adding.set(true)">+ Nouvelle adresse</button>
          } @else {
            <app-address-form [busy]="busy()" submitLabel="Utiliser cette adresse" (saved)="createAddress($event)" (cancelled)="adding.set(false)" />
          }
        } @else {
          <p class="muted">Chargement…</p>
        }
      </section>

      <aside class="card stack" aria-labelledby="summary">
        <h2 id="summary">Récapitulatif</h2>
        @if (cart.cart(); as c) {
          @for (line of c.lines; track line.productId) {
            <div class="row spread">
              <span>{{ line.quantity }} × {{ line.name }}</span>
              <span class="price">{{ line.lineTotal.amount | currency: line.lineTotal.currency }}</span>
            </div>
          }
          <div class="row spread total">
            <strong>Total</strong>
            <strong class="price">{{ c.total.amount | currency: c.total.currency }}</strong>
          </div>
          <button type="button" class="btn" [disabled]="!canOrder() || busy()" (click)="placeOrder()">Valider la commande</button>
          <p class="muted small">Les articles sont réservés 30 minutes, le temps du paiement.</p>
          <a routerLink="/cart">Modifier le panier</a>
        }
      </aside>
    </div>
  `,
  styles: `
    .layout { display: grid; gap: 1.5rem; grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr)); align-items: start; }
    h2 { font-size: 1.125rem; margin: 0; }
    fieldset { border: 0; padding: 0; margin: 0; }
    .choice { display: flex; gap: 0.75rem; align-items: flex-start; font-weight: 400; font-size: 1rem; cursor: pointer; }
    .link { justify-self: start; }
    .total { border-top: 1px solid var(--border); padding-top: 0.75rem; }
    .small { font-size: 0.8125rem; margin: 0; }
  `,
})
export class CheckoutPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly cart = inject(CartStore);

  protected readonly busy = signal(false);
  protected readonly adding = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedId = signal<string | null>(null);
  /** One key per visit: a double click or a retry after a network error places a single order. */
  private readonly idempotencyKey = crypto.randomUUID();

  protected readonly addresses = resource({
    loader: async () => {
      const list = await this.api.invoke(addressList);
      this.selectedId.set(this.selectedId() ?? list[0]?.id ?? null);
      return list;
    },
  });

  /** Nothing to ship: the address is only used for the invoice. */
  protected readonly servicesOnly = computed(() => {
    const lines = this.cart.cart()?.lines ?? [];
    return lines.length > 0 && lines.every((line) => line.kind === 'SERVICE');
  });

  protected readonly canOrder = computed(() => {
    const cart = this.cart.cart();
    return !!this.selectedId() && !this.adding() && !!cart && cart.lines.length > 0 && cart.checkoutAllowed;
  });

  protected async createAddress(address: PostalAddress): Promise<void> {
    await this.run(async () => {
      const created = await this.api.invoke(addressCreate, { body: address });
      this.selectedId.set(created.id);
      this.adding.set(false);
      this.addresses.reload();
    });
  }

  protected async placeOrder(): Promise<void> {
    const addressId = this.selectedId();
    if (!addressId) {
      return;
    }
    await this.run(async () => {
      const order = await this.api.invoke(orderCheckout, { 'Idempotency-Key': this.idempotencyKey, body: { addressId } });
      this.cart.reset();
      await this.router.navigate(['/account/orders', order.id], { queryParams: { created: 1 } });
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(errorMessage(error));
      await this.cart.load().catch(() => undefined);
    } finally {
      this.busy.set(false);
    }
  }
}
