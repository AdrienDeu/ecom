package org.ecom.cart;

import java.util.UUID;

/**
 * Who a cart belongs to: an anonymous visitor identified by an unguessable cart id, or a signed-in customer.
 */
public sealed interface CartOwner {

    String redisKey();

    static CartOwner guest(UUID cartId) {
        return new Guest(cartId);
    }

    static CartOwner customer(String customerId) {
        return new Customer(customerId);
    }

    record Guest(UUID cartId) implements CartOwner {

        @Override
        public String redisKey() {
            return "cart:guest:" + cartId;
        }

    }

    record Customer(String customerId) implements CartOwner {

        @Override
        public String redisKey() {
            return "cart:customer:" + customerId;
        }

    }

}
