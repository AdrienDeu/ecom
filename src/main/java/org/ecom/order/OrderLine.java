package org.ecom.order;

import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import org.ecom.catalog.ProductKind;
import org.ecom.shared.domain.Money;
import org.hibernate.annotations.UuidGenerator;

/**
 * Snapshot of a product at checkout time: later catalog changes do not alter past orders.
 */
@Entity
class OrderLine {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID productId;

    private String sku;

    private String name;

    @Enumerated(EnumType.STRING)
    private ProductKind kind;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "unit_price_amount"))
    @AttributeOverride(name = "currency", column = @Column(name = "unit_price_currency"))
    private Money unitPrice;

    private int quantity;

    protected OrderLine() {
    }

    OrderLine(UUID productId, String sku, String name, ProductKind kind, Money unitPrice, int quantity) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.kind = kind;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    Money lineTotal() {
        return unitPrice.times(quantity);
    }

    UUID getProductId() {
        return productId;
    }

    String getSku() {
        return sku;
    }

    ProductKind getKind() {
        return kind;
    }

    String getName() {
        return name;
    }

    Money getUnitPrice() {
        return unitPrice;
    }

    int getQuantity() {
        return quantity;
    }

}
