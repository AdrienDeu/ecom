import { Component } from '@angular/core';

interface Node {
  id: string;
  label: string;
  x: number;
  y: number;
}

/**
 * The hero's picture: a small company network, hardware as square pads with a status light, consulting as rings
 * whose dashed lines show what they cover. Cables draw themselves once on load (not with reduced motion).
 */
@Component({
  selector: 'app-network-diagram',
  template: `
    <svg viewBox="0 0 480 380" role="img"
         aria-label="Schéma d'un réseau d'entreprise : Internet, pare-feu, switch, Wi-Fi et postes de travail, couverts par l'audit de sécurité, l'étude cloud et le DSI à temps partagé.">
      <g class="cables">
        @for (cable of cables; track $index; let i = $index) {
          <path [attr.d]="cable" pathLength="1" [style.animation-delay.ms]="200 + i * 140" />
        }
      </g>
      <g class="coverage">
        @for (line of coverage; track $index) {
          <path [attr.d]="line" />
        }
      </g>
      @for (n of hardware; track n.id) {
        <g class="hardware" [attr.transform]="'translate(' + n.x + ' ' + n.y + ')'">
          <rect x="-15" y="-15" width="30" height="30" rx="4" />
          <circle r="4.5" class="led" />
          <text y="34">{{ n.label }}</text>
        </g>
      }
      @for (n of services; track n.id) {
        <g class="service" [attr.transform]="'translate(' + n.x + ' ' + n.y + ')'">
          <circle r="17" />
          <circle r="6" class="core" />
          <text y="37">{{ n.label }}</text>
        </g>
      }
    </svg>
    <p class="legend">
      <span><span class="key key--hardware" aria-hidden="true"></span>Matériel</span>
      <span><span class="key key--service" aria-hidden="true"></span>Conseil</span>
    </p>
  `,
  styles: `
    :host { display: block; }
    svg { display: block; width: 100%; height: auto; overflow: visible; }
    text {
      font-family: var(--font);
      font-size: 15px;
      font-weight: 600;
      text-anchor: middle;
      fill: var(--text);
      paint-order: stroke;
      stroke: var(--bg);
      stroke-width: 5px;
      stroke-linejoin: round;
    }
    .cables path {
      fill: none;
      stroke: var(--text);
      stroke-width: 2;
      stroke-linecap: round;
      stroke-dasharray: 1;
      stroke-dashoffset: 1;
      animation: draw 700ms cubic-bezier(0.6, 0, 0.2, 1) forwards;
    }
    .coverage path {
      fill: none;
      stroke: var(--accent);
      stroke-width: 1.5;
      stroke-dasharray: 4 5;
      opacity: 0;
      animation: appear 600ms ease 1.3s forwards;
    }
    .hardware rect { fill: var(--surface); stroke: var(--text); stroke-width: 2; }
    .hardware .led { fill: var(--led-on); }
    .service circle { fill: var(--bg); stroke: var(--accent); stroke-width: 2; }
    .service .core { fill: var(--accent); stroke: none; }
    @keyframes draw { to { stroke-dashoffset: 0; } }
    @keyframes appear { to { opacity: 1; } }
    @media (prefers-reduced-motion: reduce) {
      .cables path { animation: none; stroke-dashoffset: 0; }
      .coverage path { animation: none; opacity: 1; }
    }
    .legend { display: flex; gap: 1.25rem; justify-content: center; margin: 0.5rem 0 0; font-size: 0.875rem; color: var(--text-muted); }
    .legend > span { display: inline-flex; align-items: center; gap: 0.4rem; }
    .key { display: inline-block; width: 0.8rem; height: 0.8rem; }
    .key--hardware { border: 2px solid var(--text); border-radius: 2px; }
    .key--service { border: 2px solid var(--accent); border-radius: 50%; }
  `,
})
export class NetworkDiagram {
  protected readonly hardware: Node[] = [
    { id: 'internet', label: 'Internet', x: 60, y: 60 },
    { id: 'firewall', label: 'Pare-feu', x: 200, y: 60 },
    { id: 'switch', label: 'Switch', x: 200, y: 200 },
    { id: 'wifi', label: 'Wi-Fi', x: 380, y: 140 },
    { id: 'desks', label: 'Postes', x: 380, y: 270 },
    { id: 'screens', label: 'Écrans', x: 200, y: 320 },
  ];

  protected readonly services: Node[] = [
    { id: 'audit', label: 'Audit sécurité', x: 360, y: 40 },
    { id: 'cloud', label: 'Étude cloud', x: 60, y: 190 },
    { id: 'dsi', label: 'DSI', x: 60, y: 320 },
  ];

  /** In drawing order, from the Internet inwards. */
  protected readonly cables = [
    'M75 60 H185',
    'M200 75 V185',
    'M215 200 C290 200 300 140 365 140',
    'M215 200 C290 200 300 270 365 270',
    'M200 215 V305',
  ];

  protected readonly coverage = ['M343 42 L215 58', 'M343 46 C300 90 230 150 213 186', 'M77 190 C130 190 150 120 188 72', 'M77 320 C130 320 160 250 188 212'];
}
