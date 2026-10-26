import { Component, computed, effect, inject, input, resource, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { adminCatalogCreateCategory } from '../../api/fn/admin-catalog-controller/admin-catalog-create-category';
import { adminCatalogCreateProduct } from '../../api/fn/admin-catalog-controller/admin-catalog-create-product';
import { adminCatalogDeactivateProduct } from '../../api/fn/admin-catalog-controller/admin-catalog-deactivate-product';
import { adminCatalogProduct } from '../../api/fn/admin-catalog-controller/admin-catalog-product';
import { adminCatalogUpdateProduct } from '../../api/fn/admin-catalog-controller/admin-catalog-update-product';
import { adminCatalogUpdateStock } from '../../api/fn/admin-catalog-controller/admin-catalog-update-stock';
import { catalogCategories } from '../../api/fn/catalog-controller/catalog-categories';
import { errorMessage } from '../../core/errors';
import { AdminNav } from './admin-nav';

@Component({
  selector: 'app-admin-product-page',
  imports: [ReactiveFormsModule, RouterLink, AdminNav],
  template: `
    <h1>Back-office</h1>
    <app-admin-nav />
    <a routerLink="/admin/products" class="back">← Produits</a>
    <h2>{{ isNew() ? 'Nouveau produit' : 'Modifier le produit' }}</h2>
    @if (message(); as m) {
      <p class="alert" [class.error]="m.error" [class.success]="!m.error" role="status">{{ m.text }}</p>
    }

    <form [formGroup]="form" (ngSubmit)="save()" class="card stack" novalidate>
      <div class="form-grid">
        <label>
          SKU
          <input formControlName="sku" [readonly]="!isNew()" />
          @if (invalid('sku')) { <span class="field-error">Obligatoire (64 caractères max.)</span> }
        </label>
        <label>
          Nom
          <input formControlName="name" />
          @if (invalid('name')) { <span class="field-error">Obligatoire</span> }
        </label>
        <label>
          Prix (EUR)
          <input formControlName="price" type="number" min="0" step="0.01" />
          @if (invalid('price')) { <span class="field-error">Prix positif, 2 décimales max.</span> }
        </label>
        <label>
          Catégorie
          <select formControlName="categoryId">
            <option value="">Aucune</option>
            @for (c of categories.value() ?? []; track c.id) {
              <option [value]="c.id">{{ c.name }}</option>
            }
          </select>
        </label>
        <label>
          URL de l'image
          <input formControlName="imageUrl" type="url" />
        </label>
        @if (isNew()) {
          <label>
            Stock initial
            <input formControlName="initialStock" type="number" min="0" step="1" />
          </label>
        } @else {
          <label class="checkbox">
            <input formControlName="active" type="checkbox" /> En vente
          </label>
        }
      </div>
      <label>
        Description
        <textarea formControlName="description" rows="5"></textarea>
      </label>
      <div class="row">
        <button type="submit" class="btn" [disabled]="busy()">Enregistrer</button>
        @if (!isNew() && form.controls.active.value) {
          <button type="button" class="btn danger" [disabled]="busy()" (click)="deactivate()">Retirer de la vente</button>
        }
      </div>
    </form>

    @if (product.value(); as p) {
      <section class="card stack stock">
        <h3>Stock</h3>
        <p>Physique : <strong>{{ p.stock.onHand }}</strong> · Réservé (commandes non payées) : <strong>{{ p.stock.reserved }}</strong> · Disponible : <strong>{{ p.stock.available }}</strong></p>
        <form class="row" (submit)="$event.preventDefault(); updateStock(onHand.valueAsNumber)">
          <label>Nouveau stock physique <input #onHand type="number" min="0" step="1" [value]="p.stock.onHand" class="qty" /></label>
          <button type="submit" class="btn secondary" [disabled]="busy()">Mettre à jour</button>
        </form>
      </section>
    }

    <details class="card new-category">
      <summary>Créer une catégorie</summary>
      <form class="row" (submit)="$event.preventDefault(); createCategory(categoryName.value); categoryName.value = ''">
        <label class="grow">Nom <input #categoryName required maxlength="200" /></label>
        <button type="submit" class="btn secondary" [disabled]="busy()">Créer</button>
      </form>
    </details>
  `,
  styles: `
    .back { display: inline-block; margin-bottom: 1rem; text-decoration: none; }
    h2 { font-size: 1.25rem; }
    h3 { margin: 0; font-size: 1rem; }
    .checkbox { display: flex; align-items: center; gap: 0.5rem; align-self: end; }
    .stock, .new-category { margin-top: 1.5rem; }
    .qty { width: 7rem; }
    .grow { flex: 1 1 14rem; }
    summary { cursor: pointer; font-weight: 500; }
    details form { margin-top: 1rem; }
  `,
})
export class AdminProductPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** Undefined on /admin/products/new. */
  readonly id = input<string>();
  protected readonly isNew = computed(() => !this.id());

  protected readonly busy = signal(false);
  protected readonly message = signal<{ text: string; error: boolean } | null>(null);

  protected readonly categories = resource({ loader: () => this.api.invoke(catalogCategories) });

  protected readonly product = resource({
    params: () => (this.id() ? { id: this.id()! } : undefined),
    loader: ({ params }) => this.api.invoke(adminCatalogProduct, params),
  });

  protected readonly form = this.fb.group({
    sku: ['', [Validators.required, Validators.maxLength(64)]],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    price: [0, [Validators.required, Validators.min(0), Validators.pattern(/^\d+(\.\d{1,2})?$/)]],
    categoryId: [''],
    imageUrl: ['', Validators.maxLength(1000)],
    description: ['', Validators.maxLength(10_000)],
    initialStock: [0, [Validators.min(0)]],
    active: [true],
  });

  constructor() {
    effect(() => {
      const p = this.product.value();
      if (p) {
        this.form.reset({
          sku: p.sku,
          name: p.name,
          price: p.price.amount,
          categoryId: p.category?.id ?? '',
          imageUrl: p.imageUrl ?? '',
          description: p.description ?? '',
          active: p.active,
        });
      }
    });
  }

  protected invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  protected save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return Promise.resolve();
    }
    const v = this.form.getRawValue();
    const common = {
      name: v.name,
      price: Number(v.price),
      categoryId: v.categoryId || undefined,
      imageUrl: v.imageUrl || undefined,
      description: v.description || undefined,
    };
    return this.run('Produit enregistré.', async () => {
      const id = this.id();
      if (id) {
        this.product.set(await this.api.invoke(adminCatalogUpdateProduct, { id, body: { ...common, active: v.active } }));
      } else {
        const created = await this.api.invoke(adminCatalogCreateProduct, {
          body: { ...common, sku: v.sku, initialStock: Number(v.initialStock) },
        });
        await this.router.navigate(['/admin/products', created.id]);
      }
    });
  }

  protected deactivate(): Promise<void> {
    return this.run('Produit retiré de la vente.', async () => {
      await this.api.invoke(adminCatalogDeactivateProduct, { id: this.id()! });
      this.product.reload();
    });
  }

  protected updateStock(onHand: number): Promise<void> {
    if (!Number.isInteger(onHand) || onHand < 0) {
      this.message.set({ text: 'Stock invalide.', error: true });
      return Promise.resolve();
    }
    return this.run('Stock mis à jour.', async () => {
      this.product.set(await this.api.invoke(adminCatalogUpdateStock, { id: this.id()!, body: { onHand } }));
    });
  }

  protected createCategory(name: string): Promise<void> {
    if (!name.trim()) {
      return Promise.resolve();
    }
    return this.run('Catégorie créée.', async () => {
      const category = await this.api.invoke(adminCatalogCreateCategory, { body: { name: name.trim() } });
      this.categories.reload();
      this.form.controls.categoryId.setValue(category.id);
    });
  }

  private async run(success: string, action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.message.set(null);
    try {
      await action();
      this.message.set({ text: success, error: false });
    } catch (error) {
      this.message.set({ text: errorMessage(error), error: true });
    } finally {
      this.busy.set(false);
    }
  }
}
