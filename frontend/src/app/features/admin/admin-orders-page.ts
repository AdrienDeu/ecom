import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input, resource, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../../api/api';
import { adminOrderCancel } from '../../api/fn/admin-order-controller/admin-order-cancel';
import { adminOrderDeliver } from '../../api/fn/admin-order-controller/admin-order-deliver';
import { adminOrderList } from '../../api/fn/admin-order-controller/admin-order-list';
import { adminOrderShip } from '../../api/fn/admin-order-controller/admin-order-ship';
import { OrderView } from '../../api/models/order-view';
import { errorMessage } from '../../core/errors';
import { isCancellable, ORDER_STATUS_LABELS, ORDER_STATUSES, OrderStatus } from '../../core/labels';
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
          <option [value]="s">{{ labels[s] }}</option>
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
                <td><span class="status">{{ labels[o.status] }}</span></td>
                <td>
                  <div class="row actions">
                  @if (o.status === 'PAID') {
                    <button type="button" class="btn secondary" [disabled]="busy()" (click)="act(o, 'ship')">Expédier</button>
                  }
                  @if (o.status === 'SHIPPED') {
                    <button type="button" class="btn secondary" [disabled]="busy()" (click)="act(o, 'deliver')">Livrée</button>
                  }
                  @if (cancellable(o.status)) {
                    <button type="button" class="btn danger" [disabled]="busy()" (click)="act(o, 'cancel')">Annuler</button>
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

  protected readonly labels = ORDER_STATUS_LABELS;
  protected readonly statuses = ORDER_STATUSES;
  protected readonly cancellable = isCancellable;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly orders = resource({
    params: () => ({ status: this.status() || undefined }),
    loader: ({ params }) => this.api.invoke(adminOrderList, { ...params, size: 100 }),
  });

  protected filter(status: string): void {
    this.router.navigate([], { queryParams: { status: status || null } });
  }

  protected async act(order: OrderView, action: Action): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.api.invoke(ACTIONS[action], { id: order.id });
      this.orders.reload();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
