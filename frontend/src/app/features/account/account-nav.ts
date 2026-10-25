import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-account-nav',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="row tabs" aria-label="Mon compte">
      <a routerLink="/account/orders" routerLinkActive="active">Mes commandes</a>
      <a routerLink="/account/addresses" routerLinkActive="active">Mes adresses</a>
    </nav>
  `,
  styles: `
    .tabs { margin-bottom: 1.5rem; gap: 1.5rem; border-bottom: 1px solid var(--border); }
    a { padding-bottom: 0.5rem; color: var(--text-muted); text-decoration: none; border-bottom: 2px solid transparent; }
    a.active { color: var(--text); border-bottom-color: var(--accent); }
  `,
})
export class AccountNav {}
