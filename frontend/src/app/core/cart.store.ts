import { computed, inject, Injectable, signal } from '@angular/core';
import { Api } from '../api/api';
import { cartAdd } from '../api/fn/cart-controller/cart-add';
import { cartCart } from '../api/fn/cart-controller/cart-cart';
import { cartMerge } from '../api/fn/cart-controller/cart-merge';
import { cartRemove } from '../api/fn/cart-controller/cart-remove';
import { cartSetQuantity } from '../api/fn/cart-controller/cart-set-quantity';
import { CartView } from '../api/models/cart-view';
import { StrictHttpResponse } from '../api/strict-http-response';
import { CartIdStorage } from './cart-id.storage';

const CART_ID_HEADER = 'X-Cart-Id';

/**
 * Single source of truth for the cart shown in the header and on the cart page. Guests are identified by the
 * X-Cart-Id returned by the API; signed-in customers by their token (sent by the auth interceptor).
 */
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly api = inject(Api);
  private readonly cartIds = inject(CartIdStorage);

  readonly cart = signal<CartView | null>(null);
  readonly itemCount = computed(() => this.cart()?.lines.reduce((sum, line) => sum + line.quantity, 0) ?? 0);

  async load(): Promise<void> {
    await this.apply(this.api.invoke$Response(cartCart, this.params()));
  }

  async add(productId: string, quantity = 1): Promise<void> {
    await this.apply(this.api.invoke$Response(cartAdd, { ...this.params(), body: { productId, quantity } }));
  }

  async setQuantity(productId: string, quantity: number): Promise<void> {
    await this.apply(
      this.api.invoke$Response(cartSetQuantity, { ...this.params(), productId, body: { quantity } }),
    );
  }

  async remove(productId: string): Promise<void> {
    await this.apply(this.api.invoke$Response(cartRemove, { ...this.params(), productId }));
  }

  /** Called right after sign-in: the guest cart is moved into the customer's cart. */
  async mergeGuestCart(): Promise<void> {
    const cartId = this.cartIds.get();
    if (cartId) {
      this.cart.set(await this.api.invoke(cartMerge, { [CART_ID_HEADER]: cartId }));
      this.cartIds.clear();
    } else {
      await this.load();
    }
  }

  /** After checkout the server empties the cart. */
  reset(): void {
    this.cart.set({ lines: [], total: { amount: 0, currency: 'EUR' }, checkoutAllowed: true });
  }

  private params(): { [CART_ID_HEADER]?: string } {
    const cartId = this.cartIds.get();
    return cartId ? { [CART_ID_HEADER]: cartId } : {};
  }

  private async apply(request: Promise<StrictHttpResponse<CartView>>): Promise<void> {
    const response = await request;
    const cartId = response.headers.get(CART_ID_HEADER);
    if (cartId) {
      this.cartIds.set(cartId);
    }
    this.cart.set(response.body);
  }
}
