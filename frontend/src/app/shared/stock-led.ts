import { Component, computed, input } from '@angular/core';
import { ProductKind } from '../core/labels';

/** Availability as a status light, the way network gear shows it. */
@Component({
  selector: 'app-stock-led',
  template: `<span class="led" [class]="state()" aria-hidden="true"></span>{{ label() }}`,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 0.45rem; font-size: 0.875rem; color: var(--text-muted); }
    .led { width: 0.55rem; height: 0.55rem; border-radius: 50%; background: var(--led-off); }
    .led.on { background: var(--led-on); box-shadow: 0 0 0 3px color-mix(in srgb, var(--led-on) 22%, transparent); }
  `,
})
export class StockLed {
  readonly kind = input.required<ProductKind>();
  readonly inStock = input.required<boolean>();

  protected readonly state = computed(() => (this.inStock() ? 'on' : 'off'));
  protected readonly label = computed(() => {
    if (this.kind() === 'SERVICE') return 'Sur rendez-vous';
    return this.inStock() ? 'En stock' : 'Rupture de stock';
  });
}
