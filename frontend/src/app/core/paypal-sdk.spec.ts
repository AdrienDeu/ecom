import { loadPaypalSdk } from './paypal-sdk';

describe('loadPaypalSdk', () => {
  afterEach(() => {
    document.head.querySelectorAll('script').forEach((script) => script.remove());
    delete (window as unknown as { paypal?: unknown }).paypal;
  });

  it('loads the SDK once with the backend settings', async () => {
    const first = loadPaypalSdk('client-1', 'EUR');
    const second = loadPaypalSdk('client-1', 'EUR');
    const scripts = document.head.querySelectorAll('script');
    expect(scripts).toHaveLength(1);
    const url = new URL(scripts[0].src);
    expect(url.origin + url.pathname).toBe('https://www.paypal.com/sdk/js');
    expect(url.searchParams.get('client-id')).toBe('client-1');
    expect(url.searchParams.get('currency')).toBe('EUR');
    expect(url.searchParams.get('intent')).toBe('capture');

    const paypal = { Buttons: vi.fn() };
    (window as unknown as { paypal: unknown }).paypal = paypal;
    scripts[0].dispatchEvent(new Event('load'));

    expect(await first).toBe(paypal);
    expect(await second).toBe(paypal);
  });
});
