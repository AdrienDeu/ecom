package org.ecom.cart;

import java.util.UUID;

public record CartItem(UUID productId, int quantity) {
}
