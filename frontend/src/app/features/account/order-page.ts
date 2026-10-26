import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input, resource, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { orderCancel } from '../../api/fn/order-controller/order-cancel';
import { orderMyOrder } from '../../api/fn/order-controller/order-my-order';
import { errorMessage } from '../../core/errors';
import { isCancellable, ORDER_STATUS_LABELS } from '../../core/labels';
import { PaymentPanel } from './payment-panel';

@Component({
  selector: 'app-order-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, PaymentPanel],
  template: `
    <a routerLink="/account/orders" class="back">← Mes commandes</a>
    @if (created()) {
      <p class="alert success" role="status">Merci ! Votre commande est enregistrée, il ne reste plus qu'à la payer.</p>
    }
    @if (error(); as e) {
      <p class="alert error" role="alert">{{ e }}</p>
    }
    @if (order.error()) {
      <p class="alert error">Commande introuvable.</p>
    } @else if (order.value(); as o) {
      <div class="row spread">
        <h1>Commande {{ o.reference }}</h1>
        <span class="status">{{ labels[o.status] }}</span>
      </div>
      <p class="muted">Passée le {{ o.createdAt | date: 'long' }}</p>
      @if (o.expiresAt) {
        <p class="alert">
          Paiement attendu avant {{ o.expiresAt | date: 'shortTime' }}, après quoi la commande sera annulée.
        </p>
      }
      @if (cancellable(o.status)) {
        <app-payment-panel [orderId]="o.id" (settled)="order.reload()" />
      }
      @if (o.status === 'PAID') {
        <p class="alert success" role="status">Paiement reçu, merci ! Nous préparons votre commande.</p>
      }
      <div class="layout">
        <section class="card table-scroll">
          <table>
            <thead><tr><th>Article</th><th>Quantité</th><th>Prix unitaire</th><th>Total</th></tr></thead>
            <tbody>
              @for (line of o.lines; track line.productId) {
                <tr>
                  <td>{{ line.name }} <span class="muted">({{ line.sku }})</span></td>
                  <td>{{ line.quantity }}</td>
                  <td class="price">{{ line.unitPrice.amount | currency: line.unitPrice.currency }}</td>
                  <td class="price">{{ line.lineTotal.amount | currency: line.lineTotal.currency }}</td>
                </tr>
              }
            </tbody>
            <tfoot>
              <tr><th colspan="3">Total</th><td class="price">{{ o.total.amount | currency: o.total.currency }}</td></tr>
            </tfoot>
          </table>
        </section>
        <section class="card">
          <h2>Livraison</h2>
          <address>
            {{ o.shippingAddress.fullName }}<br />
            {{ o.shippingAddress.line1 }}<br />
            @if (o.shippingAddress.line2) { {{ o.shippingAddress.line2 }}<br /> }
            {{ o.shippingAddress.postalCode }} {{ o.shippingAddress.city }}<br />
            {{ o.shippingAddress.country }}
          </address>
        </section>
      </div>
      @if (cancellable(o.status)) {
        <button type="button" class="btn danger" [disabled]="busy()" (click)="cancel(o.id)">Annuler la commande</button>
      }
    } @else {
      <p class="muted">Chargement…</p>
    }
  `,
  styles: `
    .back { display: inline-block; margin-bottom: 1.5rem; text-decoration: none; }
    h1 { margin: 0; }
    .layout { display: grid; gap: 1.5rem; grid-template-columns: 2fr 1fr; margin-block: 1.5rem; align-items: start; }
    @media (max-width: 48rem) { .layout { grid-template-columns: 1fr; } }
    h2 { font-size: 1rem; margin-top: 0; }
    address { font-style: normal; }
  `,
})
export class OrderPage {
  private readonly api = inject(Api);

  readonly id = input.required<string>();
  /** Set by the checkout page (?created=1). */
  readonly created = input(false, { transform: (value: unknown) => value === '1' || value === true });

  protected readonly labels = ORDER_STATUS_LABELS;
  protected readonly cancellable = isCancellable;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly order = resource({
    params: () => ({ id: this.id() }),
    loader: ({ params }) => this.api.invoke(orderMyOrder, params),
  });

  protected async cancel(id: string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.order.set(await this.api.invoke(orderCancel, { id }));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
