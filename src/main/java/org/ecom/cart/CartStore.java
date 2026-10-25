package org.ecom.cart;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Carts live in Redis/Valkey as JSON, expiring after a period of inactivity.
 */
@Component
class CartStore {

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration ttl;

    CartStore(StringRedisTemplate redis, JsonMapper json, @Value("${ecom.cart.ttl:P30D}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    StoredCart load(CartOwner owner) {
        var value = redis.opsForValue().get(owner.redisKey());
        return value == null ? StoredCart.EMPTY : json.readValue(value, StoredCart.class);
    }

    void save(CartOwner owner, StoredCart cart) {
        if (cart.items().isEmpty()) {
            delete(owner);
        } else {
            redis.opsForValue().set(owner.redisKey(), json.writeValueAsString(cart), ttl);
        }
    }

    void delete(CartOwner owner) {
        redis.delete(owner.redisKey());
    }

    record StoredCart(List<CartItem> items) {

        static final StoredCart EMPTY = new StoredCart(List.of());

    }

}
