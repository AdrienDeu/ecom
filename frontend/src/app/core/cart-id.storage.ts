import { Injectable } from '@angular/core';

const KEY = 'ecom.cartId';

/**
 * Remembers the guest cart id between visits. Storage can be unavailable (private browsing, blocked cookies):
 * the cart then simply lives for the current page only.
 */
@Injectable({ providedIn: 'root' })
export class CartIdStorage {
  private memory: string | null = null;

  get(): string | null {
    try {
      return localStorage.getItem(KEY) ?? this.memory;
    } catch {
      return this.memory;
    }
  }

  set(cartId: string): void {
    this.memory = cartId;
    try {
      localStorage.setItem(KEY, cartId);
    } catch {
      // keep the in-memory value
    }
  }

  clear(): void {
    this.memory = null;
    try {
      localStorage.removeItem(KEY);
    } catch {
      // nothing to clear
    }
  }
}
