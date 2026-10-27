import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { CartView } from '../api/models/cart-view';
import { CartIdStorage } from './cart-id.storage';
import { CartStore } from './cart.store';

const line = {
  productId: 'p1',
  sku: 'SKU-1',
  slug: 'shirt',
  name: 'Shirt',
  kind: 'PRODUCT' as const,
  imageUrl: null,
  unitPrice: { amount: 10, currency: 'EUR' },
  quantity: 2,
  lineTotal: { amount: 20, currency: 'EUR' },
  available: true,
};
const cart: CartView = { lines: [line], total: { amount: 20, currency: 'EUR' }, checkoutAllowed: true };

describe('CartStore', () => {
  let store: CartStore;
  let http: HttpTestingController;
  let cartIds: CartIdStorage;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiConfiguration('')],
    });
    store = TestBed.inject(CartStore);
    http = TestBed.inject(HttpTestingController);
    cartIds = TestBed.inject(CartIdStorage);
  });

  afterEach(() => http.verify());

  it('remembers the guest cart id returned by the API and sends it back', async () => {
    const added = store.add('p1', 2);
    const first = http.expectOne('/api/cart/items');
    expect(first.request.headers.has('X-Cart-Id')).toBe(false);
    expect(first.request.body).toEqual({ productId: 'p1', quantity: 2 });
    first.flush(cart, { headers: { 'X-Cart-Id': 'guest-123' } });
    await added;

    expect(cartIds.get()).toBe('guest-123');
    expect(store.itemCount()).toBe(2);

    const loaded = store.load();
    const second = http.expectOne('/api/cart');
    expect(second.request.headers.get('X-Cart-Id')).toBe('guest-123');
    second.flush(cart);
    await loaded;
  });

  it('merges the guest cart after sign-in and forgets its id', async () => {
    cartIds.set('guest-456');

    const merged = store.mergeGuestCart();
    const request = http.expectOne('/api/cart/merge');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-Cart-Id')).toBe('guest-456');
    request.flush(cart);
    await merged;

    expect(cartIds.get()).toBeNull();
    expect(store.cart()).toEqual(cart);
  });

  it('just loads the cart when there is no guest cart to merge', async () => {
    const merged = store.mergeGuestCart();
    http.expectOne('/api/cart').flush(cart);
    await merged;

    expect(store.itemCount()).toBe(2);
  });
});
