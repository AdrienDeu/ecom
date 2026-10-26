import { pageType } from './page-view-tracker';

describe('pageType', () => {
  it('maps URLs to bounded page kinds', () => {
    expect(pageType('/')).toBe('CATALOG');
    expect(pageType('/?q=chaussures&page=2')).toBe('CATALOG');
    expect(pageType('/products/chaussure-rouge')).toBe('PRODUCT');
    expect(pageType('/cart')).toBe('CART');
    expect(pageType('/checkout')).toBe('CHECKOUT');
    expect(pageType('/account/orders/0193')).toBe('ORDER');
    expect(pageType('/account/addresses')).toBe('ACCOUNT');
    expect(pageType('/admin/orders?status=PAID')).toBe('ADMIN');
    expect(pageType('/nope')).toBe('OTHER');
  });

  it('ignores the login callback', () => {
    expect(pageType('/auth/callback?code=x')).toBeNull();
  });
});
