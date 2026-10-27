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
import { adminCatalogUploadImage } from '../../api/fn/admin-catalog-controller/admin-catalog-upload-image';
import { catalogCategories } from '../../api/fn/catalog-controller/catalog-categories';
import { errorMessage } from '../../core/errors';
import { ProductKind } from '../../core/labels';
import { AdminNav } from './admin-nav';

/** Same limits as the API (ImageFormat, spring.servlet.multipart.max-file-size), checked first for a clear message. */
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;

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
        @if (isNew()) {
          <label>
            Type
            <select formControlName="kind">
              <option value="PRODUCT">Matériel (avec stock)</option>
              <option value="SERVICE">Prestation de conseil (sans stock)</option>
            </select>
          </label>
        } @else {
          <label>
            Type
            <input [value]="form.controls.kind.value === 'SERVICE' ? 'Prestation de conseil' : 'Matériel'" readonly />
          </label>
        }
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
        @if (isNew() && form.controls.kind.value === 'PRODUCT') {
          <label>
            Stock initial
            <input formControlName="initialStock" type="number" min="0" step="1" />
          </label>
        } @else if (!isNew()) {
          <label class="checkbox">
            <input formControlName="active" type="checkbox" /> En vente
          </label>
        }
      </div>
      <fieldset class="image-field">
        <legend>Image</legend>
        <div class="image-row">
          <div class="preview">
            @if (preview(); as src) {
              <img [src]="src" alt="Aperçu de l'image du produit" />
            } @else {
              <span class="muted">Aucune image</span>
            }
          </div>
          <div class="stack image-actions">
            <div class="row">
              <label class="btn secondary file">
                Choisir une image
                <input type="file" accept="image/jpeg,image/png,image/webp" (change)="pickImage($event)" />
              </label>
              @if (pickedFile() && !isNew()) {
                <button type="button" class="btn" [disabled]="busy()" (click)="uploadImage()">Enregistrer l'image</button>
              }
            </div>
            <p class="muted hint">
              JPEG, PNG ou WebP, 5 Mo maximum.
              @if (isNew()) { L'image est envoyée à l'enregistrement du produit. }
              @if (pickedFile(); as file) { Choisie : {{ file.name }}. }
            </p>
            <label>
              Ou adresse d'une image en ligne
              <input formControlName="imageUrl" type="url" placeholder="https://…" />
            </label>
          </div>
        </div>
      </fieldset>
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
      @if (p.stock.tracked) {
      <section class="card stack stock">
        <h3>Stock</h3>
        <p>Physique : <strong>{{ p.stock.onHand }}</strong> · Réservé (commandes non payées) : <strong>{{ p.stock.reserved }}</strong> · Disponible : <strong>{{ p.stock.available }}</strong></p>
        <form class="row" (submit)="$event.preventDefault(); updateStock(onHand.valueAsNumber)">
          <label>Nouveau stock physique <input #onHand type="number" min="0" step="1" [value]="p.stock.onHand" class="qty" /></label>
          <button type="submit" class="btn secondary" [disabled]="busy()">Mettre à jour</button>
        </form>
      </section>
      }
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
    .image-field { margin: 0; padding: 1rem; border: 1px solid var(--border); border-radius: var(--radius); }
    .image-field legend { padding: 0 0.35rem; font-size: 0.875rem; font-weight: 550; }
    .image-row { display: grid; gap: 1rem 1.5rem; grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr)); align-items: start; }
    .preview {
      display: grid;
      place-items: center;
      aspect-ratio: 4 / 3;
      max-width: 16rem;
      overflow: hidden;
      border: 1px dashed var(--border);
      border-radius: var(--radius);
      background: var(--bg);
    }
    .preview img { width: 100%; height: 100%; object-fit: cover; }
    .image-actions { grid-column: span 2; }
    .file { position: relative; }
    /* The native input stays focusable and clickable, laid over its button */
    .file input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .file:focus-within { outline: 2px solid var(--accent); outline-offset: 2px; }
    .hint { margin: 0; font-size: 0.875rem; }
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
  /** Image chosen on this computer, not uploaded yet, and its preview (a data: URL, allowed by the CSP). */
  protected readonly pickedFile = signal<File | null>(null);
  private readonly pickedPreview = signal<string | null>(null);
  protected readonly preview = computed(() => this.pickedPreview() ?? this.product.value()?.imageUrl ?? null);
  protected readonly message = signal<{ text: string; error: boolean } | null>(null);

  protected readonly categories = resource({ loader: () => this.api.invoke(catalogCategories) });

  protected readonly product = resource({
    params: () => (this.id() ? { id: this.id()! } : undefined),
    loader: ({ params }) => this.api.invoke(adminCatalogProduct, params),
  });

  protected readonly form = this.fb.group({
    kind: ['PRODUCT' as ProductKind],
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
          kind: p.kind,
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
          body: {
            ...common,
            kind: v.kind,
            sku: v.sku,
            initialStock: v.kind === 'PRODUCT' ? Number(v.initialStock) : undefined,
          },
        });
        const file = this.pickedFile();
        if (file) {
          await this.api.invoke(adminCatalogUploadImage, { id: created.id, body: { file } });
          this.clearPickedImage();
        }
        await this.router.navigate(['/admin/products', created.id]);
      }
    });
  }

  protected pickImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!IMAGE_TYPES.includes(file.type)) {
      this.message.set({ text: 'Choisissez une image JPEG, PNG ou WebP.', error: true });
      return;
    }
    if (file.size > MAX_IMAGE_SIZE) {
      this.message.set({ text: "L'image dépasse 5 Mo.", error: true });
      return;
    }
    this.message.set(null);
    this.pickedFile.set(file);
    const reader = new FileReader();
    reader.onload = () => this.pickedPreview.set(reader.result as string);
    reader.readAsDataURL(file);
  }

  protected uploadImage(): Promise<void> {
    const file = this.pickedFile();
    if (!file) {
      return Promise.resolve();
    }
    return this.run('Image enregistrée.', async () => {
      const updated = await this.api.invoke(adminCatalogUploadImage, { id: this.id()!, body: { file } });
      this.product.set(updated);
      this.form.controls.imageUrl.setValue(updated.imageUrl ?? '');
      this.clearPickedImage();
    });
  }

  private clearPickedImage(): void {
    this.pickedFile.set(null);
    this.pickedPreview.set(null);
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
