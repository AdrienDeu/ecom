package org.ecom.order;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Checkout of the signed-in customer's cart. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderView checkout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
        return orders.checkout(jwt.getSubject(), request.addressId());
    }

    @GetMapping
    PagedModel<OrderView> myOrders(@AuthenticationPrincipal Jwt jwt,
                                   @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(orders.customerOrders(jwt.getSubject(), pageable));
    }

    @GetMapping("/{id}")
    OrderView myOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return orders.customerOrder(jwt.getSubject(), id);
    }

    @PostMapping("/{id}/cancel")
    OrderView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return orders.cancelByCustomer(jwt.getSubject(), id);
    }

    record CheckoutRequest(@NotNull UUID addressId) {
    }

}
