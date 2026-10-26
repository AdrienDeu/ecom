package org.ecom.cart;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.UUID;
import java.util.function.Function;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.ecom.cart.CartService.CartView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Signed-in customers get their own cart. Guests are identified by the {@code X-Cart-Id} header: the server
 * creates the id on the first write and returns it in the same header, the SPA sends it back on later calls.
 */
@RestController
@RequestMapping("/api/cart")
class CartController {

    static final String CART_ID_HEADER = "X-Cart-Id";

    private final CartService carts;

    CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    ResponseEntity<CartView> cart(@AuthenticationPrincipal Jwt jwt,
                                  @RequestHeader(name = CART_ID_HEADER, required = false) UUID cartId) {
        if (jwt == null && cartId == null) {
            return ResponseEntity.ok(carts.view(CartOwner.guest(UUID.randomUUID())));
        }
        return respond(owner(jwt, cartId), carts::view);
    }

    @PostMapping("/items")
    ResponseEntity<CartView> add(@AuthenticationPrincipal Jwt jwt,
                                 @RequestHeader(name = CART_ID_HEADER, required = false) UUID cartId,
                                 @Valid @RequestBody AddItemRequest request) {
        return respond(owner(jwt, cartId), owner -> carts.add(owner, request.productId(), request.quantity()));
    }

    @PutMapping("/items/{productId}")
    ResponseEntity<CartView> setQuantity(@AuthenticationPrincipal Jwt jwt,
                                         @RequestHeader(name = CART_ID_HEADER, required = false) UUID cartId,
                                         @PathVariable UUID productId,
                                         @Valid @RequestBody UpdateQuantityRequest request) {
        return respond(owner(jwt, cartId), owner -> carts.setQuantity(owner, productId, request.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    ResponseEntity<CartView> remove(@AuthenticationPrincipal Jwt jwt,
                                    @RequestHeader(name = CART_ID_HEADER, required = false) UUID cartId,
                                    @PathVariable UUID productId) {
        return respond(owner(jwt, cartId), owner -> carts.remove(owner, productId));
    }

    /** Called by the SPA right after sign-in, with the guest cart id it was using. */
    @PostMapping("/merge")
    CartView merge(@AuthenticationPrincipal Jwt jwt, @RequestHeader(CART_ID_HEADER) UUID cartId) {
        if (jwt == null) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return carts.merge(new CartOwner.Guest(cartId), new CartOwner.Customer(jwt.getSubject()));
    }

    private static CartOwner owner(Jwt jwt, UUID cartId) {
        if (jwt != null) {
            return CartOwner.customer(jwt.getSubject());
        }
        return CartOwner.guest(cartId != null ? cartId : UUID.randomUUID());
    }

    private static ResponseEntity<CartView> respond(CartOwner owner, Function<CartOwner, CartView> action) {
        var body = action.apply(owner);
        var response = ResponseEntity.ok();
        if (owner instanceof CartOwner.Guest(var id)) {
            response.header(CART_ID_HEADER, id.toString());
        }
        return response.body(body);
    }

    record AddItemRequest(@NotNull UUID productId, @Min(1) @Max(CartService.MAX_QUANTITY_PER_LINE) int quantity) {
    }

    record UpdateQuantityRequest(@Min(0) @Max(CartService.MAX_QUANTITY_PER_LINE) int quantity) {
    }

}
