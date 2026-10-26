/** The subset of the PayPal JS SDK used by the payment panel. https://developer.paypal.com/sdk/js/reference/ */
export interface PaypalButtonsOptions {
  style?: Record<string, string>;
  /** Returns the PayPal order id, created by the backend. */
  createOrder: () => Promise<string>;
  onApprove: (data: { orderID: string }) => Promise<void>;
  onCancel?: () => void;
  onError?: (error: unknown) => void;
}

export interface PaypalButtons {
  render(container: HTMLElement): Promise<void>;
  close(): Promise<void>;
}

export interface PaypalNamespace {
  Buttons(options: PaypalButtonsOptions): PaypalButtons;
}

const SDK_URL = 'https://www.paypal.com/sdk/js';

let loading: Promise<PaypalNamespace> | null = null;

/** Loads the SDK once per page; its client id comes from the backend (`GET /api/payments/methods`). */
export function loadPaypalSdk(clientId: string, currency: string): Promise<PaypalNamespace> {
  loading ??= new Promise<PaypalNamespace>((resolve, reject) => {
    const params = new URLSearchParams({
      'client-id': clientId,
      currency,
      intent: 'capture',
      components: 'buttons',
    });
    const script = document.createElement('script');
    script.src = `${SDK_URL}?${params}`;
    script.async = true;
    script.onload = () => {
      const paypal = (window as unknown as { paypal?: PaypalNamespace }).paypal;
      if (paypal) {
        resolve(paypal);
      } else {
        reject(new Error('PayPal SDK loaded without its namespace'));
      }
    };
    script.onerror = () => reject(new Error('PayPal SDK could not be loaded'));
    document.head.appendChild(script);
  }).catch((error: unknown) => {
    loading = null; // allow a retry, e.g. after a network hiccup
    throw error;
  });
  return loading;
}
