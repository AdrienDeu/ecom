import { DOCUMENT } from '@angular/common';
import { computed, effect, inject, Injectable, signal } from '@angular/core';

/** What the visitor chose; `system` follows the operating system and its changes. */
export type ThemeMode = 'system' | 'light' | 'dark';

/** Shared with public/theme-init.js, which applies the theme before the application starts. */
const STORAGE_KEY = 'ecom.theme';

/**
 * Light or dark theme, set as `data-theme` on `<html>`: stylesheets define their colours for `[data-theme='dark']`.
 */
@Injectable({ providedIn: 'root' })
export class ThemeStore {
  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
  private readonly systemDark = signal(this.media?.matches ?? false);

  readonly mode = signal<ThemeMode>(readMode());
  readonly theme = computed<'light' | 'dark'>(() => {
    const mode = this.mode();
    return mode === 'system' ? (this.systemDark() ? 'dark' : 'light') : mode;
  });

  constructor() {
    this.media?.addEventListener('change', (event) => this.systemDark.set(event.matches));
    effect(() => {
      this.document.documentElement.dataset['theme'] = this.theme();
    });
  }

  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      if (mode === 'system') {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, mode);
      }
    } catch {
      // storage unavailable: the choice lasts for this visit only
    }
  }
}

function readMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved === 'light' || saved === 'dark' ? saved : 'system';
  } catch {
    return 'system';
  }
}
