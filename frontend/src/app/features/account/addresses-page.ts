import { Component, inject, resource, signal } from '@angular/core';
import { Api } from '../../api/api';
import { addressCreate } from '../../api/fn/address-controller/address-create';
import { addressDelete } from '../../api/fn/address-controller/address-delete';
import { addressList } from '../../api/fn/address-controller/address-list';
import { addressUpdate } from '../../api/fn/address-controller/address-update';
import { AddressView } from '../../api/models/address-view';
import { PostalAddress } from '../../api/models/postal-address';
import { errorMessage } from '../../core/errors';
import { AccountNav } from './account-nav';
import { AddressForm } from './address-form';

@Component({
  selector: 'app-addresses-page',
  imports: [AccountNav, AddressForm],
  template: `
    <h1>Mon compte</h1>
    <app-account-nav />
    @if (error(); as e) {
      <p class="alert error" role="alert">{{ e }}</p>
    }
    @if (editing(); as target) {
      <section class="card">
        <h2>{{ target === 'new' ? 'Nouvelle adresse' : 'Modifier l’adresse' }}</h2>
        <app-address-form [address]="target === 'new' ? null : target.address" [busy]="busy()"
                          (saved)="save(target, $event)" (cancelled)="editing.set(null)" />
      </section>
    } @else {
      <button type="button" class="btn" (click)="editing.set('new')">Ajouter une adresse</button>
    }
    @if (addresses.value(); as list) {
      <ul class="grid">
        @for (a of list; track a.id) {
          <li class="card stack">
            <address>
              <strong>{{ a.address.fullName }}</strong><br />
              {{ a.address.line1 }}<br />
              @if (a.address.line2) { {{ a.address.line2 }}<br /> }
              {{ a.address.postalCode }} {{ a.address.city }}, {{ a.address.country }}
              @if (a.address.phone) { <br />{{ a.address.phone }} }
            </address>
            <div class="row">
              <button type="button" class="link" (click)="editing.set(a)">Modifier</button>
              <button type="button" class="link" [disabled]="busy()" (click)="remove(a.id)">Supprimer</button>
            </div>
          </li>
        } @empty {
          <li class="muted">Aucune adresse enregistrée.</li>
        }
      </ul>
    }
  `,
  styles: `
    h2 { font-size: 1.125rem; margin-top: 0; }
    .grid { list-style: none; padding: 0; margin: 1.5rem 0 0; display: grid; gap: 1rem; grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr)); }
    address { font-style: normal; }
  `,
})
export class AddressesPage {
  private readonly api = inject(Api);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal<AddressView | 'new' | null>(null);

  protected readonly addresses = resource({ loader: () => this.api.invoke(addressList) });

  protected save(target: AddressView | 'new', address: PostalAddress): Promise<void> {
    return this.run(async () => {
      if (target === 'new') {
        await this.api.invoke(addressCreate, { body: address });
      } else {
        await this.api.invoke(addressUpdate, { id: target.id, body: address });
      }
      this.editing.set(null);
    });
  }

  protected remove(id: string): Promise<void> {
    return this.run(() => this.api.invoke(addressDelete, { id }));
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
      this.addresses.reload();
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
