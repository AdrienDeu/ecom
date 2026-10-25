import { Component, effect, inject, input, output } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PostalAddress } from '../../api/models/postal-address';

/** Mirrors the server-side validation of PostalAddress. */
@Component({
  selector: 'app-address-form',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
      <div class="form-grid">
        <label>
          Nom complet
          <input formControlName="fullName" autocomplete="name" />
          @if (invalid('fullName')) { <span class="field-error">Obligatoire</span> }
        </label>
        <label>
          Téléphone (facultatif)
          <input formControlName="phone" autocomplete="tel" />
          @if (invalid('phone')) { <span class="field-error">Numéro invalide</span> }
        </label>
        <label>
          Adresse
          <input formControlName="line1" autocomplete="address-line1" />
          @if (invalid('line1')) { <span class="field-error">Obligatoire</span> }
        </label>
        <label>
          Complément (facultatif)
          <input formControlName="line2" autocomplete="address-line2" />
        </label>
        <label>
          Code postal
          <input formControlName="postalCode" autocomplete="postal-code" />
          @if (invalid('postalCode')) { <span class="field-error">Obligatoire</span> }
        </label>
        <label>
          Ville
          <input formControlName="city" autocomplete="address-level2" />
          @if (invalid('city')) { <span class="field-error">Obligatoire</span> }
        </label>
        <label>
          Pays
          <select formControlName="country" autocomplete="country">
            @for (c of countries; track c.code) {
              <option [value]="c.code">{{ c.name }}</option>
            }
          </select>
        </label>
      </div>
      <div class="row">
        <button type="submit" class="btn" [disabled]="busy()">{{ submitLabel() }}</button>
        <button type="button" class="btn secondary" (click)="cancelled.emit()">Annuler</button>
      </div>
    </form>
  `,
})
export class AddressForm {
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly address = input<PostalAddress | null>(null);
  readonly busy = input(false);
  readonly submitLabel = input('Enregistrer');
  readonly saved = output<PostalAddress>();
  readonly cancelled = output<void>();

  protected readonly countries = [
    { code: 'FR', name: 'France' },
    { code: 'BE', name: 'Belgique' },
    { code: 'CH', name: 'Suisse' },
    { code: 'LU', name: 'Luxembourg' },
  ];

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    line1: ['', [Validators.required, Validators.maxLength(200)]],
    line2: ['', Validators.maxLength(200)],
    postalCode: ['', [Validators.required, Validators.maxLength(20)]],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    country: ['FR', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^\+?[0-9 .()-]*$/)]],
  });

  constructor() {
    effect(() => {
      const address = this.address();
      if (address) {
        this.form.reset({ ...address, line2: address.line2 ?? '', phone: address.phone ?? '' });
      }
    });
  }

  protected invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saved.emit({ ...value, line2: value.line2 || undefined, phone: value.phone || undefined });
  }
}
