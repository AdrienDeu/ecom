import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { of } from 'rxjs';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  const authorize = vi.fn();

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: OidcSecurityService,
          useValue: {
            authenticated: signal({ isAuthenticated: true }),
            isAuthenticated$: of({ isAuthenticated: true }),
            userData: signal({ userData: { email: 'jeanne@example.com' } }),
            getPayloadFromAccessToken: () => of({ sub: 'u1', 'cognito:groups': ['CUSTOMER', 'ADMIN'] }),
            authorize,
          },
        },
      ],
    });
    auth = TestBed.inject(AuthService);
  });

  it('reads roles from the Cognito groups of the access token', () => {
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
    expect(auth.displayName()).toBe('jeanne@example.com');
  });

  it('returns to the requested page after login', () => {
    auth.login('/checkout');

    expect(authorize).toHaveBeenCalled();
    expect(auth.consumeReturnUrl()).toBe('/checkout');
    expect(auth.consumeReturnUrl()).toBe('/');
  });

  it('never redirects outside the site after login', () => {
    for (const url of ['https://evil.example', '//evil.example', 'javascript:alert(1)']) {
      sessionStorage.setItem('ecom.returnUrl', url);
      expect(auth.consumeReturnUrl()).toBe('/');
    }
  });
});
