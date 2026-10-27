import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input, resource, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../../api/api';
import { adminOrderCancel } from '../../api/fn/admin-order-controller/admin-order-cancel';
import { adminOrderDeliver } from '../../api/fn/admin-order-controller/admin-order-deliver';
import { adminOrderList } from '../../api/fn/admin-order-controller/admin-order-list';
import { adminOrderShip } from '../../api/fn/admin-order-controller/admin-order-ship';
import { adminPaymentList } from '../../api/fn/admin-payment-controller/admin-payment-list';
import { adminPaymentRefund } from '../../api/fn/admin-payment-controller/admin-payment-refund';
import { OrderView } from '../../api/models/order-view';
import { errorMessage } from '../../core/errors';
import { isCancellable, ORDER_STATUSES, OrderStatus, orderStatusFilterLabel, orderStatusLabel } from '../../core/labels';
import { AdminNav } from './admin-nav';

type Action = 'ship' | 'deliver' | 'cancel';

const ACTIONS = { ship: adminOrderShip, deliver: adminOrderDeliver, cancel: adminOrderCancel } as const;

@Component({
  selector: 'app-admin-orders-page',
  imports: [CurrencyPipe, DatePipe, AdminNav],
  template: `
    <h1>Back-office</h1>
    <app-admin-nav />
    <label class="filter">
      Statut
      <select [value]="status() ?? ''" (change)="filter($any($event.target).value)">
        <option value="">Tous</option>
        @for (s of statuses; track s) {
          <option [value]="s">{{ filterLabel(s) }}</option>
        }
      </select>
    </label>
    @if (error(); as e) {
      <p class="alert error" role="alert">{{ e }}</p>
    }
    @if (orders.value(); as page) {
      <div class="card table-scroll">
        <table>
          <thead>
            <tr><th>Référence</th><th>Date</th><th>Client</th><th>Total</th><th>Statut</th><th>Actions</th></tr>
          </thead>
          <tbody>
            @for (o of page.content; track o.id) {
              <tr>
                <td>{{ o.reference }}</td>
                <td>{{ o.createdAt | date: 'short' }}</td>
                <td>{{ o.shippingAddress.fullName }}<br /><span class="muted">{{ o.shippingAddress.city }}</span></td>
                <td class="price">{{ o.total.amount | currency: o.total.currency }}</td>
                <td><span class="status">{{ statusLabel(o) }}</span></td>
                <td>
                  <div class="row actions">
                  @if (o.status === 'PAID') {
                    <button type="button" class="btn secondary" [disabled]="busy()" (click)="act(o, 'ship')">{{ o.servicesOnly ? 'Planifier' : 'Expédier' }}</button>
                  }
                  @if (o.status === 'SHIPPED') {
                    <button type="button" class="btn secondary" [disabled]="busy()" (click)="act(o, 'deliver')">{{ o.servicesOnly ? 'Marquer réalisée' : 'Livrée' }}</button>
                  }
                  @if (cancellable(o.status)) {
                    <button type="button" class="btn danger" [disabled]="busy()" (click)="act(o, 'cancel')">Annuler</button>
                  }
                  @if (refundable(o.status)) {
                    <button type="button" class="btn danger" [disabled]="busy()" (click)="refund(o)">Rembourser</button>
                  }
                  </div>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="muted">Aucune commande.</td></tr>
            }
          </tbody>
        </table>
      </div>
    } @else if (orders.error()) {
      <p class="alert error">Impossible de charger les commandes.</p>
    }
  `,
  styles: `
    .filter { max-width: 16rem; margin-bottom: 1rem; }
    .actions { gap: 0.5rem; flex-wrap: nowrap; }
  `,
})
export class AdminOrdersPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  readonly status = input<OrderStatus>();

  protected readonly statusLabel = orderStatusLabel;
  protected readonly filterLabel = orderStatusFilterLabel;
  protected readonly statuses = ORDER_STATUSES;
  protected readonly cancellable = isCancellable;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly orders = resource({
    params: () => ({ status: this.status() || undefined }),
    loader: ({ params }) => this.api.invoke(adminOrderList, { ...params, size: 100 }),
  });

  protected refundable(status: OrderStatus): boolean {
    return status === 'PAID' || status === 'SHIPPED' || status === 'DELIVERED';
  }

  protected filter(status: string): void {
    this.router.navigate([], { queryParams: { status: status || null } });
  }

  protected act(order: OrderView, action: Action): Promise<void> {
    return this.run(async () => {
      await this.api.invoke(ACTIONS[action], { id: order.id });
    });
  }

  /** Refunds whatever remains of the order's payment; partial refunds go through the API. */
  protected refund(order: OrderView): Promise<void> {
    return this.run(async () => {
      const payments = await this.api.invoke(adminPaymentList, { orderId: order.id });
      const payment = payments.find((p) => p.status === 'CAPTURED' || p.status === 'PARTIALLY_REFUNDED');
      if (!payment) {
        this.error.set('Aucun paiement remboursable pour cette commande.');
        return;
      }
      if (!confirm(`Rembourser la commande ${order.reference} au client ?`)) {
        return;
      }
      await this.api.invoke(adminPaymentRefund, { id: payment.id, body: {} });
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
      this.orders.reload();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
