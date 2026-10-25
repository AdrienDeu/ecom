import { HttpBackend, HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { LogLevel, OpenIdConfiguration, StsConfigHttpLoader, StsConfigLoader } from 'angular-auth-oidc-client';
import { map, tap } from 'rxjs';
import { RUNTIME_CONFIG_URL, RuntimeConfig } from './runtime-config';

@Injectable({ providedIn: 'root' })
export class RuntimeConfigStore {
  readonly config = signal<RuntimeConfig | null>(null);
}

export function toOidcConfig(config: RuntimeConfig, origin: string): OpenIdConfiguration {
  return {
    authority: config.oidc.authority,
    clientId: config.oidc.clientId,
    scope: config.oidc.scope,
    responseType: 'code',
    redirectUrl: `${origin}/auth/callback`,
    postLogoutRedirectUri: origin,
    silentRenew: true,
    useRefreshToken: true,
    renewTimeBeforeTokenExpiresInSeconds: 60,
    // Cognito does not return a nonce in ID tokens obtained with a refresh token
    ignoreNonceAfterRefresh: true,
    // Our callback page merges the guest cart and restores the requested URL: no automatic redirect
    triggerAuthorizationResultEvent: true,
    // The access token is only attached to our own API, never to third-party URLs
    secureRoutes: ['/api/'],
    logLevel: LogLevel.Warn,
  };
}

/** Loads /config.json once at startup, without going through the HTTP interceptors. */
export function authConfigLoader(): StsConfigLoader {
  const http = new HttpClient(inject(HttpBackend));
  const store = inject(RuntimeConfigStore);
  return new StsConfigHttpLoader(
    http.get<RuntimeConfig>(RUNTIME_CONFIG_URL).pipe(
      tap((config) => store.config.set(config)),
      map((config) => toOidcConfig(config, window.location.origin)),
    ),
  );
}
