import { Component, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { quoteSubmit } from '../../api/fn/quote-controller/quote-submit';
import { BRAND } from '../../core/brand';
import { errorMessage } from '../../core/errors';

@Component({
  selector: 'app-quote-page',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="layout">
      <header class="intro">
        <h1>Demander un devis</h1>
        <p>Pour un besoin sur mesure ou une prestation qui n’est pas au catalogue, décrivez votre projet.</p>
        <p class="muted">
          Nous vous répondons sous {{ brand.responseTime }} avec une proposition ou quelques questions. Votre demande
          ne sert qu’à vous recontacter.
        </p>
      </header>

      @if (sent()) {
        <section class="card done" role="status">
          <h2>Demande envoyée</h2>
          <p>Merci. Nous revenons vers vous sous {{ brand.responseTime }} à l’adresse indiquée.</p>
          <p><a routerLink="/">Retour au catalogue</a></p>
        </section>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="card stack" novalidate>
          @if (error(); as e) {
            <p class="alert error" role="alert">{{ e }}</p>
          }
          <div class="form-grid">
            <label>
              Nom et prénom
              <input formControlName="fullName" autocomplete="name" />
              @if (invalid('fullName')) { <span class="field-error">Indiquez votre nom.</span> }
            </label>
            <label>
              E-mail professionnel
              <input formControlName="email" type="email" autocomplete="email" />
              @if (invalid('email')) { <span class="field-error">Indiquez une adresse e-mail valide.</span> }
            </label>
            <label>
              <span>Entreprise <span class="optional">(facultatif)</span></span>
              <input formControlName="company" autocomplete="organization" />
            </label>
            <label>
              <span>Téléphone <span class="optional">(facultatif)</span></span>
              <input formControlName="phone" type="tel" autocomplete="tel" />
            </label>
          </div>
          <label>
            <span>Sujet <span class="optional">(facultatif)</span></span>
            <input formControlName="topic" />
          </label>
          <label>
            Votre besoin
            <textarea formControlName="message" rows="7"
                      placeholder="Contexte, nombre de postes ou de sites, échéance, budget envisagé…"></textarea>
            @if (invalid('message')) { <span class="field-error">Décrivez votre besoin en quelques phrases (20 caractères minimum).</span> }
          </label>
          <!-- Honeypot: hidden from people and assistive technologies, filled in by bots -->
          <label class="trap" aria-hidden="true">
            Site web
            <input formControlName="website" tabindex="-1" autocomplete="off" />
          </label>
          <div>
            <button type="submit" class="btn large" [disabled]="busy()">Envoyer la demande</button>
          </div>
        </form>
      }
    </div>
  `,
  styles: `
    .layout { display: grid; gap: 2rem 4rem; align-items: start; }
    @media (min-width: 56rem) {
      .layout { grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr); }
    }
    .intro p { max-width: 36rem; font-size: 1.0625rem; }
    .optional { font-weight: 400; color: var(--text-muted); }
    .trap { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }
    .done h2 { margin-top: 0; }
  `,
})
export class QuotePage {
  private readonly api = inject(Api);
  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly brand = BRAND;

  /** ?sujet= prefills the topic, from a consulting package's page. */
  readonly sujet = input<string>();

  protected readonly busy = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    company: ['', Validators.maxLength(200)],
    phone: ['', Validators.maxLength(30)],
    topic: ['', Validators.maxLength(200)],
    message: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(5000)]],
    website: [''],
  });

  constructor() {
    effect(() => {
      const topic = this.sujet();
      if (topic) {
        this.form.controls.topic.setValue(topic.slice(0, 200));
      }
    });
  }

  protected invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.api.invoke(quoteSubmit, {
        body: {
          fullName: v.fullName,
          email: v.email,
          company: v.company || undefined,
          phone: v.phone || undefined,
          topic: v.topic || undefined,
          message: v.message,
          website: v.website || undefined,
        },
      });
      this.sent.set(true);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}
