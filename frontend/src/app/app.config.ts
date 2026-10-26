import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
  provideEnvironmentInitializer,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import {
  authInterceptor,
  provideAuth,
  StsConfigLoader,
  withAppInitializerAuthCheck,
} from 'angular-auth-oidc-client';
import { provideApiConfiguration } from './api/api-configuration';
import { routes } from './app.routes';
import { authConfigLoader } from './core/auth-config';
import { PageViewTracker } from './core/page-view-tracker';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor()])),
    // Same origin as the SPA: proxied to the backend in dev, routed by CloudFront in AWS
    provideApiConfiguration(''),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    provideAuth(
      { loader: { provide: StsConfigLoader, useFactory: authConfigLoader } },
      withAppInitializerAuthCheck(),
    ),
    provideEnvironmentInitializer(() => inject(PageViewTracker).start()),
  ],
};
