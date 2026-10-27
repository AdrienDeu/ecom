import { Component, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { RuntimeConfigStore } from './core/auth-config';
import { BRAND } from './core/brand';
import { CartStore } from './core/cart.store';
import { Icon } from './shared/icon';
import { ThemeToggle } from './shared/theme-toggle';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, ThemeToggle],
  template: `
    <header class="site-header">
      <div class="container site-header__inner">
        <a routerLink="/" class="brand">{{ brand.name }}</a>
        <nav class="site-nav" aria-label="Navigation principale">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Catalogue</a>
          <a routerLink="/devis" routerLinkActive="active">Demander un devis</a>
          @if (auth.isAuthenticated()) {
            <a routerLink="/account" routerLinkActive="active">{{ auth.displayName() }}</a>
            @if (auth.isAdmin()) {
              <a routerLink="/admin" routerLinkActive="active">Back-office</a>
            }
            <button type="button" class="link" (click)="logout()">Déconnexion</button>
          } @else {
            <button type="button" class="link" (click)="auth.login()">Connexion</button>
          }
          <app-theme-toggle />
          <a routerLink="/cart" routerLinkActive="active" class="cart-link">
            <app-icon name="cart" />
            Panier
            @if (cart.itemCount() > 0) {
              <span class="badge" aria-label="articles">{{ cart.itemCount() }}</span>
            }
          </a>
        </nav>
      </div>
    </header>
    <main class="container">
      <router-outlet />
    </main>
  `,
  styleUrl: './app.scss',
})
export class App implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartStore);
  protected readonly brand = BRAND;
  private readonly runtimeConfig = inject(RuntimeConfigStore);

  ngOnInit(): void {
    // The callback page merges the guest cart itself; everywhere else load the current cart
    if (!window.location.pathname.startsWith('/auth/callback')) {
      this.cart.load().catch(() => this.cart.reset());
    }
  }

  protected logout(): void {
    this.auth.logout(this.runtimeConfig.config()?.oidc.logoutUrl ?? null);
  }
}
