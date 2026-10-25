package org.ecom.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import org.ecom.customer.PostalAddress;
import org.ecom.shared.domain.Money;

public record OrderView(UUID id, String reference, OrderStatus status, Money total, PostalAddress shippingAddress,
                        List<Line> lines, Instant createdAt,
                        @Schema(nullable = true, description = "Unpaid orders are cancelled after this instant") Instant expiresAt) {

    static OrderView of(CustomerOrder order) {
        var lines = order.getLines().stream()
                .map(line -> new Line(line.getProductId(), line.getSku(), line.getName(), line.getUnitPrice(),
                        line.getQuantity(), line.lineTotal()))
                .toList();
        return new OrderView(order.getId(), order.getReference(), order.getStatus(), order.getTotal(),
                order.getShippingAddress(), lines, order.getCreatedAt(), order.getExpiresAt());
    }

    public record Line(UUID productId, String sku, String name, Money unitPrice, int quantity, Money lineTotal) {
    }

}
