import { Component, ElementRef, HostListener, inject, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { RuntimeConfigStore } from '../core/auth-config';
import { Icon } from './icon';

/** Sign-in button, or once signed in a menu with the account pages, the back-office and sign-out. */
@Component({
  selector: 'app-account-menu',
  imports: [RouterLink, Icon],
  template: `
    @if (auth.isAuthenticated()) {
      <details #menu>
        <summary [attr.aria-label]="'Mon compte, ' + auth.displayName()">
          <app-icon name="user" />
          <span class="name">{{ auth.displayName() }}</span>
          <app-icon name="chevron-down" class="chevron" />
        </summary>
        <div class="panel">
          <a routerLink="/account/orders" (click)="close()">Mes commandes</a>
          <a routerLink="/account/addresses" (click)="close()">Mes adresses</a>
          @if (auth.isAdmin()) {
            <a routerLink="/admin" (click)="close()">Back-office</a>
          }
          <button type="button" (click)="logout()"><app-icon name="log-out" />Déconnexion</button>
        </div>
      </details>
    } @else {
      <button type="button" class="sign-in" (click)="auth.login()">
        <app-icon name="log-in" /><span class="label">Connexion</span>
      </button>
    }
  `,
  styles: `
    :host { position: relative; display: inline-flex; }
    summary, .sign-in {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.4rem 0.6rem;
      border: 1px solid transparent;
      border-radius: var(--radius);
      background: none;
      color: var(--text-muted);
      font: inherit;
      font-weight: 550;
      cursor: pointer;
      list-style: none;
    }
    summary::-webkit-details-marker { display: none; }
    summary:hover, .sign-in:hover, details[open] summary { color: var(--text); background: var(--surface); border-color: var(--border); }
    .chevron { width: 0.9em; height: 0.9em; transition: transform 120ms ease; }
    details[open] .chevron { transform: rotate(180deg); }
    .name { max-width: 12rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .panel {
      position: absolute;
      right: 0;
      top: calc(100% + 0.4rem);
      z-index: 20;
      display: grid;
      min-width: 13rem;
      padding: 0.35rem;
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      background: var(--surface);
      box-shadow: 0 12px 32px color-mix(in srgb, var(--text) 14%, transparent);
    }
    .panel a, .panel button {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.55rem 0.7rem;
      border: 0;
      border-radius: var(--radius);
      background: none;
      color: var(--text);
      font: inherit;
      text-align: left;
      text-decoration: none;
      cursor: pointer;
    }
    .panel a:hover, .panel button:hover { background: var(--accent-soft); }
    .panel button { margin-top: 0.25rem; border-top: 1px solid var(--border); border-radius: 0 0 var(--radius) var(--radius); color: var(--text-muted); }
    @media (max-width: 47.99rem) {
      .name, .label { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
      .chevron { display: none; }
    }
  `,
})
export class AccountMenu {
  protected readonly auth = inject(AuthService);
  private readonly runtimeConfig = inject(RuntimeConfigStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly menu = viewChild<ElementRef<HTMLDetailsElement>>('menu');

  /** Closes the menu when the visitor clicks anywhere else. */
  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  /** Escape closes the menu and gives the focus back to its button. */
  @HostListener('keydown.escape')
  protected onEscape(): void {
    this.close(true);
  }

  protected close(refocus = false): void {
    const menu = this.menu()?.nativeElement;
    if (menu?.open) {
      menu.open = false;
      if (refocus) {
        menu.querySelector('summary')?.focus();
      }
    }
  }

  protected logout(): void {
    this.auth.logout(this.runtimeConfig.config()?.oidc.logoutUrl ?? null);
  }
}
