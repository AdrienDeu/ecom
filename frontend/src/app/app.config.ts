import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { authInterceptor, provideAuth, StsConfigLoader, withAppInitializerAuthCheck } from 'angular-auth-oidc-client';
import { provideApiConfiguration } from './api/api-configuration';
import { routes } from './app.routes';
import { authConfigLoader } from './core/auth-config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(withInterceptors([authInterceptor()])),
    // Same origin as the SPA: proxied to the backend in dev, routed by CloudFront in AWS
    provideApiConfiguration(''),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    provideAuth({ loader: { provide: StsConfigLoader, useFactory: authConfigLoader } }, withAppInitializerAuthCheck()),
  ],
};
