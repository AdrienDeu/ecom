import { Component, inject } from '@angular/core';
import { ThemeMode, ThemeStore } from '../core/theme';
import { Icon, IconName } from './icon';

const OPTIONS: { mode: ThemeMode; icon: IconName; label: string }[] = [
  { mode: 'light', icon: 'sun', label: 'Thème clair' },
  { mode: 'dark', icon: 'moon', label: 'Thème sombre' },
  { mode: 'system', icon: 'monitor', label: 'Thème du système' },
];

/** Three-way switch: light, dark, or follow the system. */
@Component({
  selector: 'app-theme-toggle',
  imports: [Icon],
  template: `
    <div class="toggle" role="group" aria-label="Thème d'affichage">
      @for (option of options; track option.mode) {
        <button type="button" [attr.aria-pressed]="store.mode() === option.mode" [attr.aria-label]="option.label"
                [title]="option.label" (click)="store.setMode(option.mode)">
          <app-icon [name]="option.icon" />
        </button>
      }
    </div>
  `,
  styles: `
    .toggle {
      display: inline-flex;
      gap: 2px;
      padding: 2px;
      border: 1px solid var(--border);
      border-radius: 999px;
      background: var(--surface);
    }
    button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 1.9rem;
      height: 1.9rem;
      padding: 0;
      border: 0;
      border-radius: 999px;
      background: transparent;
      color: var(--text-muted);
      font-size: 0.875rem;
      cursor: pointer;
    }
    button:hover { color: var(--text); }
    button[aria-pressed='true'] { background: var(--accent); color: var(--on-accent); }
  `,
})
export class ThemeToggle {
  protected readonly store = inject(ThemeStore);
  protected readonly options = OPTIONS;
}
