import { DatePipe } from '@angular/common';
import { Component, inject, input, resource, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../../api/api';
import { adminQuoteChangeStatus } from '../../api/fn/admin-quote-controller/admin-quote-change-status';
import { adminQuoteList } from '../../api/fn/admin-quote-controller/admin-quote-list';
import { QuoteRequestView } from '../../api/models/quote-request-view';
import { errorMessage } from '../../core/errors';
import { QUOTE_STATUS_LABELS, QUOTE_STATUSES, QuoteStatus } from '../../core/labels';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-quotes-page',
  imports: [DatePipe, AdminNav],
  template: `
    <h1>Back-office</h1>
    <app-admin-nav />
    <label class="filter">
      Statut
      <select [value]="status() ?? ''" (change)="filter($any($event.target).value)">
        <option value="">Toutes</option>
        @for (s of statuses; track s) {
          <option [value]="s">{{ labels[s] }}</option>
        }
      </select>
    </label>
    @if (error(); as e) {
      <p class="alert error" role="alert">{{ e }}</p>
    }
    @if (quotes.value(); as page) {
      <ul class="quotes">
        @for (q of page.content; track q.id) {
          <li class="card">
            <div class="row spread">
              <div>
                <h2>{{ q.topic || 'Demande sans sujet' }}</h2>
                <p class="muted">Reçue le {{ q.createdAt | date: 'medium' }}</p>
              </div>
              <label class="state">
                <span class="sr-only">Statut de la demande de {{ q.fullName }}</span>
                <select [value]="q.status" [disabled]="busy()" (change)="changeStatus(q, $any($event.target).value)">
                  @for (s of statuses; track s) {
                    <option [value]="s">{{ labels[s] }}</option>
                  }
                </select>
              </label>
            </div>
            <p class="contact">
              <strong>{{ q.fullName }}</strong>
              @if (q.company) { , {{ q.company }} }
              <br />
              <a [href]="'mailto:' + q.email">{{ q.email }}</a>
              @if (q.phone) { <span class="muted"> ou </span><a [href]="'tel:' + q.phone">{{ q.phone }}</a> }
            </p>
            <p class="message">{{ q.message }}</p>
          </li>
        } @empty {
          <li class="muted">Aucune demande de devis.</li>
        }
      </ul>
    } @else if (quotes.error()) {
      <p class="alert error">Impossible de charger les demandes de devis.</p>
    }
  `,
  styles: `
    .filter { max-width: 16rem; margin-bottom: 1.5rem; }
    .quotes { list-style: none; margin: 0; padding: 0; display: grid; gap: 1rem; }
    h2 { margin: 0; font-size: 1.125rem; }
    .row.spread { align-items: start; }
    .row p, .contact { margin: 0.25rem 0 0; }
    .state { min-width: 10rem; }
    .contact { margin-top: 1rem; }
    .message { white-space: pre-line; margin: 1rem 0 0; max-width: 72ch; }
  `,
})
export class AdminQuotesPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  /** Bound from ?status=. */
  readonly status = input<QuoteStatus>();

  protected readonly labels = QUOTE_STATUS_LABELS;
  protected readonly statuses = QUOTE_STATUSES;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly quotes = resource({
    params: () => ({ status: this.status() || undefined }),
    loader: ({ params }) => this.api.invoke(adminQuoteList, params),
  });

  protected filter(status: string): void {
    this.router.navigate([], { queryParams: { status: status || null } });
  }

  protected async changeStatus(quote: QuoteRequestView, status: QuoteStatus): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.api.invoke(adminQuoteChangeStatus, { id: quote.id, body: { status } });
      this.quotes.reload();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
