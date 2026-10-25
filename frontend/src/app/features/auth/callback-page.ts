import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CartStore } from '../../core/cart.store';

/**
 * Return point from the identity provider. The authorization code has already been exchanged by the OIDC
 * library at startup (withAppInitializerAuthCheck); this page finishes the sign-in.
 */
@Component({
  selector: 'app-callback-page',
  imports: [RouterLink],
  template: `
    @if (failed()) {
      <p class="alert error">La connexion a échoué. <a routerLink="/">Retour à l'accueil</a></p>
    } @else {
      <p class="muted">Connexion en cours…</p>
    }
  `,
})
export class CallbackPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartStore);
  private readonly router = inject(Router);

  protected readonly failed = signal(false);

  async ngOnInit(): Promise<void> {
    if (!this.auth.isAuthenticated()) {
      this.failed.set(true);
      return;
    }
    await this.cart.mergeGuestCart().catch(() => this.cart.load());
    await this.router.navigateByUrl(this.auth.consumeReturnUrl(), { replaceUrl: true });
  }
}
