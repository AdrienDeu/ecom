import { Component, computed, input } from '@angular/core';

interface Shape {
  paths?: string[];
  circles?: { cx: number; cy: number; r: number }[];
  rects?: { x: number; y: number; width: number; height: number; rx: number }[];
}

/**
 * Line icons from Lucide (https://lucide.dev, ISC licence), drawn inline: no icon font nor extra request, and they
 * take the text colour, so they follow the theme.
 */
const ICONS = {
  sun: {
    circles: [{ cx: 12, cy: 12, r: 4 }],
    paths: ['M12 2v2', 'M12 20v2', 'm4.93 4.93 1.41 1.41', 'm17.66 17.66 1.41 1.41', 'M2 12h2', 'M20 12h2',
      'm6.34 17.66-1.41 1.41', 'm19.07 4.93-1.41 1.41'],
  },
  moon: { paths: ['M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z'] },
  monitor: { rects: [{ x: 2, y: 3, width: 20, height: 14, rx: 2 }], paths: ['M8 21h8', 'M12 17v4'] },
  cart: {
    circles: [{ cx: 8, cy: 21, r: 1 }, { cx: 19, cy: 21, r: 1 }],
    paths: ['M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12'],
  },
  user: { circles: [{ cx: 12, cy: 7, r: 4 }], paths: ['M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2'] },
  'log-in': { paths: ['M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4', 'm10 17 5-5-5-5', 'M15 12H3'] },
  'log-out': { paths: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'] },
  trash: { paths: ['M3 6h18', 'M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6', 'M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2'] },
  'chevron-down': { paths: ['m6 9 6 6 6-6'] },
} satisfies Record<string, Shape>;

export type IconName = keyof typeof ICONS;

/** Decorative by default: give the surrounding control an accessible name. */
@Component({
  selector: 'app-icon',
  template: `
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      @for (d of shape().paths ?? []; track $index) {
        <path [attr.d]="d" />
      }
      @for (c of shape().circles ?? []; track $index) {
        <circle [attr.cx]="c.cx" [attr.cy]="c.cy" [attr.r]="c.r" />
      }
      @for (r of shape().rects ?? []; track $index) {
        <rect [attr.x]="r.x" [attr.y]="r.y" [attr.width]="r.width" [attr.height]="r.height" [attr.rx]="r.rx" />
      }
    </svg>
  `,
  styles: `
    :host { display: inline-flex; flex: none; width: 1.15em; height: 1.15em; }
    svg { width: 100%; height: 100%; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
  `,
})
export class Icon {
  readonly name = input.required<IconName>();
  protected readonly shape = computed<Shape>(() => ICONS[this.name()]);
}
