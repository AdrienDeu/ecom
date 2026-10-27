import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, resource } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { orderMyOrders } from '../../api/fn/order-controller/order-my-orders';
import { orderStatusLabel, orderStatusTone } from '../../core/labels';
import { AccountNav } from './account-nav';

@Component({
  selector: 'app-orders-page',
  imports: [CurrencyPipe, DatePipe, RouterLink, AccountNav],
  template: `
    <h1>Mon compte</h1>
    <app-account-nav />
    @if (orders.error()) {
      <p class="alert error">Impossible de charger vos commandes.</p>
    } @else if (orders.value(); as page) {
      @if (page.content.length === 0) {
        <p class="muted">Vous n'avez pas encore passé de commande. <a routerLink="/">Découvrir le catalogue</a></p>
      } @else {
        <div class="card table-scroll">
          <table>
            <thead>
              <tr><th>Référence</th><th>Date</th><th>Statut</th><th>Total</th></tr>
            </thead>
            <tbody>
              @for (order of page.content; track order.id) {
                <tr>
                  <td><a [routerLink]="[order.id]">{{ order.reference }}</a></td>
                  <td>{{ order.createdAt | date: 'medium' }}</td>
                  <td><span class="status" [attr.data-tone]="statusTone(order.status)">{{ statusLabel(order) }}</span></td>
                  <td class="price">{{ order.total.amount | currency: order.total.currency }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    } @else {
      <p class="muted">Chargement…</p>
    }
  `,
})
export class OrdersPage {
  private readonly api = inject(Api);
  protected readonly statusLabel = orderStatusLabel;
  protected readonly statusTone = orderStatusTone;
  protected readonly orders = resource({ loader: () => this.api.invoke(orderMyOrders, { size: 50 }) });
}
