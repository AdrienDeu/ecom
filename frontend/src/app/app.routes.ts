import { Routes } from '@angular/router';
import { BRAND } from './core/brand';
import { adminGuard, authGuard } from './core/guards';

const name = BRAND.name;

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: `${name} | ${BRAND.tagline}`,
    loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
  },
  {
    path: 'conseil',
    title: `Forfaits de conseil | ${name}`,
    data: { kind: 'SERVICE' },
    loadComponent: () => import('./features/catalog/catalog-page').then((m) => m.CatalogPage),
  },
  {
    path: 'materiel',
    title: `Matériel informatique | ${name}`,
    data: { kind: 'PRODUCT' },
    loadComponent: () => import('./features/catalog/catalog-page').then((m) => m.CatalogPage),
  },
  {
    path: 'devis',
    title: `Demander un devis | ${name}`,
    loadComponent: () => import('./features/quote/quote-page').then((m) => m.QuotePage),
  },
  {
    path: 'products/:slug',
    loadComponent: () => import('./features/catalog/product-page').then((m) => m.ProductPage),
  },
  {
    path: 'cart',
    title: 'Panier',
    loadComponent: () => import('./features/cart/cart-page').then((m) => m.CartPage),
  },
  {
    path: 'checkout',
    title: 'Commande',
    canActivate: [authGuard],
    loadComponent: () => import('./features/checkout/checkout-page').then((m) => m.CheckoutPage),
  },
  {
    path: 'account',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'orders' },
      {
        path: 'orders',
        title: 'Mes commandes',
        loadComponent: () => import('./features/account/orders-page').then((m) => m.OrdersPage),
      },
      {
        path: 'orders/:id',
        title: 'Commande',
        loadComponent: () => import('./features/account/order-page').then((m) => m.OrderPage),
      },
      {
        path: 'addresses',
        title: 'Mes adresses',
        loadComponent: () => import('./features/account/addresses-page').then((m) => m.AddressesPage),
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'products',
        title: 'Back-office · Produits',
        loadComponent: () => import('./features/admin/admin-products-page').then((m) => m.AdminProductsPage),
      },
      {
        path: 'products/new',
        title: 'Back-office · Nouveau produit',
        loadComponent: () => import('./features/admin/admin-product-page').then((m) => m.AdminProductPage),
      },
      {
        path: 'products/:id',
        title: 'Back-office · Produit',
        loadComponent: () => import('./features/admin/admin-product-page').then((m) => m.AdminProductPage),
      },
      {
        path: 'orders',
        title: 'Back-office · Commandes',
        loadComponent: () => import('./features/admin/admin-orders-page').then((m) => m.AdminOrdersPage),
      },
      {
        path: 'quotes',
        title: 'Back-office · Devis',
        loadComponent: () => import('./features/admin/admin-quotes-page').then((m) => m.AdminQuotesPage),
      },
    ],
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./features/auth/callback-page').then((m) => m.CallbackPage),
  },
  {
    path: '**',
    title: 'Page introuvable',
    loadComponent: () => import('./features/not-found-page').then((m) => m.NotFoundPage),
  },
];
