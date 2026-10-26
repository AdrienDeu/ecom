import { Component, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { RuntimeConfigStore } from './core/auth-config';
import { BRAND } from './core/brand';
import { CartStore } from './core/cart.store';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip-link" href="#content">Aller au contenu</a>
    <header class="site-header">
      <div class="container site-header__inner">
        <a routerLink="/" class="brand" [attr.aria-label]="brand.name + ', accueil'">
          <span class="brand__mark" aria-hidden="true"></span>{{ brand.name }}
        </a>
        <nav class="site-nav" aria-label="Navigation principale">
          <a routerLink="/conseil" routerLinkActive="active">Conseil</a>
          <a routerLink="/materiel" routerLinkActive="active">Matériel</a>
          <a routerLink="/devis" routerLinkActive="active">Demander un devis</a>
        </nav>
        <nav class="site-nav site-nav--user" aria-label="Mon compte">
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
              <span class="badge"><span class="sr-only">, articles :</span>{{ cart.itemCount() }}</span>
            }
          </a>
        </nav>
      </div>
    </header>
    <main id="content" class="container">
      <router-outlet />
    </main>
    <footer class="site-footer">
      <div class="container site-footer__inner">
        <div class="stack">
          <span class="brand"><span class="brand__mark" aria-hidden="true"></span>{{ brand.name }}</span>
          <p class="muted">{{ brand.tagline }}.</p>
        </div>
        <nav aria-label="Offre">
          <h2>Offre</h2>
          <a routerLink="/conseil">Forfaits de conseil</a>
          <a routerLink="/materiel">Matériel informatique</a>
          <a routerLink="/devis">Demander un devis</a>
        </nav>
        <nav aria-label="Compte">
          <h2>Compte</h2>
          <a routerLink="/account/orders">Mes commandes</a>
          <a routerLink="/account/addresses">Mes adresses</a>
        </nav>
        <div>
          <h2>Contact</h2>
          <a [href]="'mailto:' + brand.email">{{ brand.email }}</a>
          <p class="muted">Réponse sous {{ brand.responseTime }}. Paiement sécurisé par PayPal.</p>
        </div>
      </div>
    </footer>
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
