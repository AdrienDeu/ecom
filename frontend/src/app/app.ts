import { Component, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { RuntimeConfigStore } from './core/auth-config';
import { CartStore } from './core/cart.store';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="site-header">
      <div class="container site-header__inner">
        <a routerLink="/" class="brand">ecom</a>
        <nav class="site-nav" aria-label="Navigation principale">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Catalogue</a>
          @if (auth.isAuthenticated()) {
            <a routerLink="/account" routerLinkActive="active">{{ auth.displayName() }}</a>
            @if (auth.isAdmin()) {
              <a routerLink="/admin" routerLinkActive="active">Back-office</a>
            }
            <button type="button" class="link" (click)="logout()">Déconnexion</button>
          } @else {
            <button type="button" class="link" (click)="auth.login()">Connexion</button>
          }
          <a routerLink="/cart" routerLinkActive="active" class="cart-link">
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
