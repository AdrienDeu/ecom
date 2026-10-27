package org.ecom.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import org.ecom.catalog.ProductKind;
import org.ecom.customer.PostalAddress;
import org.ecom.shared.domain.Money;

public record OrderView(UUID id, String reference, OrderStatus status, Money total, PostalAddress shippingAddress,
                        List<Line> lines,
                        @Schema(description = "Nothing to ship: SHIPPED means the service is scheduled, DELIVERED that it was performed")
                        boolean servicesOnly,
                        Instant createdAt,
                        @Schema(nullable = true, description = "Unpaid orders are cancelled after this instant") Instant expiresAt) {

    static OrderView of(CustomerOrder order) {
        var lines = order.getLines().stream()
                .map(line -> new Line(line.getProductId(), line.getSku(), line.getName(), line.getKind(),
                        line.getUnitPrice(), line.getQuantity(), line.lineTotal()))
                .toList();
        var servicesOnly = lines.stream().allMatch(line -> line.kind() == ProductKind.SERVICE);
        return new OrderView(order.getId(), order.getReference(), order.getStatus(), order.getTotal(),
                order.getShippingAddress(), lines, servicesOnly, order.getCreatedAt(), order.getExpiresAt());
    }

    public record Line(UUID productId, String sku, String name, ProductKind kind, Money unitPrice, int quantity,
                       Money lineTotal) {
    }

}
