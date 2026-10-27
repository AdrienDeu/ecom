import { Component, computed, input } from '@angular/core';

type Drawing = 'laptop' | 'screen' | 'network' | 'accessory' | 'generic';

const DRAWINGS: Record<string, Drawing> = {
  ordinateurs: 'laptop',
  ecrans: 'screen',
  reseau: 'network',
  accessoires: 'accessory',
};

/**
 * Product image, or a line drawing of its category on squared drawing paper when the back-office set no image.
 */
@Component({
  selector: 'app-product-visual',
  template: `
    @if (imageUrl()) {
      <img [src]="imageUrl()" [alt]="name()" loading="lazy" />
    } @else {
      <svg viewBox="0 0 120 90" role="img" [attr.aria-label]="name()">
        @switch (drawing()) {
          @case ('laptop') {
            <rect x="30" y="22" width="60" height="38" rx="2" />
            <rect x="35" y="27" width="50" height="28" class="fill" />
            <path d="M22 64 h76 l-4 6 h-68 z" />
          }
          @case ('screen') {
            <rect x="24" y="18" width="72" height="44" rx="2" />
            <rect x="29" y="23" width="62" height="34" class="fill" />
            <path d="M54 62 v8 M66 62 v8 M46 72 h28" />
          }
          @case ('network') {
            <rect x="22" y="48" width="76" height="18" rx="2" />
            <path d="M40 48 v-14 M80 48 v-14 M40 34 c0 -8 40 -8 40 0" />
            @for (x of ports; track x) {
              <rect [attr.x]="x" y="54" width="6" height="6" class="fill" />
            }
          }
          @case ('accessory') {
            <rect x="18" y="38" width="62" height="24" rx="3" />
            <path d="M24 45 h50 M24 51 h50 M32 57 h34" />
            <rect x="86" y="36" width="16" height="26" rx="8" />
            <path d="M94 36 v8" />
          }
          @default {
            <rect x="38" y="23" width="44" height="44" rx="3" />
            <rect x="48" y="33" width="24" height="24" class="fill" />
            <path d="M46 23 v-7 M56 23 v-7 M66 23 v-7 M74 23 v-7 M46 67 v7 M56 67 v7 M66 67 v7 M74 67 v7 M38 31 h-7 M38 45 h-7 M38 59 h-7 M82 31 h7 M82 45 h7 M82 59 h7" />
          }
        }
      </svg>
    }
  `,
  styles: `
    :host {
      display: block;
      aspect-ratio: 4 / 3;
      overflow: hidden;
      border-radius: var(--radius);
      color: var(--accent);
      background-color: var(--accent-soft);
      background-image:
        linear-gradient(color-mix(in srgb, var(--accent) 9%, transparent) 1px, transparent 1px),
        linear-gradient(90deg, color-mix(in srgb, var(--accent) 9%, transparent) 1px, transparent 1px);
      background-size: 12px 12px;
    }
    img { width: 100%; height: 100%; object-fit: cover; background: var(--surface); }
    svg { width: 100%; height: 100%; fill: none; stroke: currentColor; stroke-width: 1.4; stroke-linejoin: round; stroke-linecap: round; }
    .fill { fill: color-mix(in srgb, currentColor 14%, transparent); stroke-width: 1; }
  `,
})
export class ProductVisual {
  readonly name = input.required<string>();
  readonly imageUrl = input<string | null>();
  readonly categorySlug = input<string | null>();

  protected readonly ports = [28, 37, 46, 55, 64, 73, 82];
  protected readonly drawing = computed<Drawing>(() => DRAWINGS[this.categorySlug() ?? ''] ?? 'generic');
}
