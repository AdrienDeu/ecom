import { computed, inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { map, of, switchMap } from 'rxjs';

const RETURN_URL_KEY = 'ecom.returnUrl';

interface AccessTokenClaims {
  sub?: string;
  username?: string;
  'cognito:groups'?: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);

  readonly isAuthenticated = computed(() => this.oidc.authenticated().isAuthenticated);

  private readonly claims = toSignal(
    this.oidc.isAuthenticated$.pipe(
      switchMap(({ isAuthenticated }) =>
        isAuthenticated ? this.oidc.getPayloadFromAccessToken() : of(null),
      ),
      map((payload) => payload as AccessTokenClaims | null),
    ),
    { initialValue: null },
  );

  readonly isAdmin = computed(() => this.claims()?.['cognito:groups']?.includes('ADMIN') ?? false);

  readonly displayName = computed(() => {
    const user = this.oidc.userData().userData as { email?: string; name?: string } | null;
    return user?.name ?? user?.email ?? this.claims()?.username ?? 'Mon compte';
  });

  /** Redirects to the identity provider; the user comes back to `returnUrl` after the callback. */
  login(returnUrl = window.location.pathname + window.location.search): void {
    try {
      sessionStorage.setItem(RETURN_URL_KEY, returnUrl);
    } catch {
      // storage unavailable: the user lands on the home page after login
    }
    this.oidc.authorize();
  }

  consumeReturnUrl(): string {
    try {
      const url = sessionStorage.getItem(RETURN_URL_KEY);
      sessionStorage.removeItem(RETURN_URL_KEY);
      // Only same-site paths: never redirect to an external URL taken from storage
      return url?.startsWith('/') && !url.startsWith('//') ? url : '/';
    } catch {
      return '/';
    }
  }

  logout(logoutUrl: string | null): void {
    if (logoutUrl) {
      this.oidc.logoffLocal();
      window.location.href = logoutUrl;
    } else {
      this.oidc.logoff().subscribe();
    }
  }
}
