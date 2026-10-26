import { inject, Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { Api } from '../api/api';
import { telemetryPageView } from '../api/fn/telemetry-controller/telemetry-page-view';
import { PageViewRequest } from '../api/models/page-view-request';

export type PageType = PageViewRequest['page'];

/** Kind of page for a URL. Only this bounded category is reported, never the URL itself. */
export function pageType(url: string): PageType | null {
  const path = url.split(/[?#]/)[0];
  if (path.startsWith('/auth/')) {
    return null; // technical redirect of the login flow
  }
  if (path === '/' || path === '') return 'CATALOG';
  if (path.startsWith('/products/')) return 'PRODUCT';
  if (path === '/cart') return 'CART';
  if (path === '/checkout') return 'CHECKOUT';
  if (path.startsWith('/account/orders/')) return 'ORDER';
  if (path.startsWith('/account')) return 'ACCOUNT';
  if (path.startsWith('/admin')) return 'ADMIN';
  return 'OTHER';
}

/**
 * Reports each page displayed by the SPA (`ecom.page.views` metric): the server never sees client-side navigation.
 * Fire and forget: a lost page view must never disturb the visitor.
 */
@Injectable({ providedIn: 'root' })
export class PageViewTracker {
  private readonly router = inject(Router);
  private readonly api = inject(Api);

  start(): void {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event) => {
        const page = pageType(event.urlAfterRedirects);
        if (page) {
          this.api.invoke(telemetryPageView, { body: { page } }).catch(() => undefined);
        }
      });
  }
}
