package org.ecom.order;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office order management. Payment-related statuses (PAID, REFUNDED) are only set by the payment module.
 */
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController {

    private final OrderService orders;

    AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    PagedModel<OrderView> list(@RequestParam(required = false) OrderStatus status,
                               @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(orders.list(status, pageable));
    }

    @GetMapping("/{id}")
    OrderView get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @PostMapping("/{id}/ship")
    OrderView ship(@PathVariable UUID id) {
        return orders.changeStatus(id, OrderStatus.SHIPPED);
    }

    @PostMapping("/{id}/deliver")
    OrderView deliver(@PathVariable UUID id) {
        return orders.changeStatus(id, OrderStatus.DELIVERED);
    }

    @PostMapping("/{id}/cancel")
    OrderView cancel(@PathVariable UUID id) {
        return orders.changeStatus(id, OrderStatus.CANCELLED);
    }

}
