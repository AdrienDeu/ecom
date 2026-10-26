import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-admin-nav',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="row tabs" aria-label="Back-office">
      <a routerLink="/admin/products" routerLinkActive="active">Produits</a>
      <a routerLink="/admin/orders" routerLinkActive="active">Commandes</a>
    </nav>
  `,
  styles: `
    .tabs { margin-bottom: 1.5rem; gap: 1.5rem; border-bottom: 1px solid var(--border); }
    a { padding-bottom: 0.5rem; color: var(--text-muted); text-decoration: none; border-bottom: 2px solid transparent; }
    a.active { color: var(--text); border-bottom-color: var(--accent); }
  `,
})
export class AdminNav {}
