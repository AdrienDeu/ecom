package org.ecom.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

import org.ecom.customer.PostalAddress;
import org.ecom.shared.domain.Money;
import org.ecom.shared.web.BusinessException;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
class CustomerOrder {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String reference;

    private String customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount"))
    @AttributeOverride(name = "currency", column = @Column(name = "total_currency"))
    private Money total;

    @Embedded
    private PostalAddress shippingAddress;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant expiresAt;

    @Version
    private long version;

    protected CustomerOrder() {
    }

    CustomerOrder(String reference, String customerId, PostalAddress shippingAddress, List<OrderLine> lines,
                  Instant expiresAt) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        this.reference = reference;
        this.customerId = customerId;
        this.shippingAddress = shippingAddress;
        this.lines.addAll(lines);
        this.total = lines.stream().map(OrderLine::lineTotal).reduce(Money::plus).orElseThrow();
        this.status = OrderStatus.CREATED;
        this.expiresAt = expiresAt;
    }

    void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                    "Order %s cannot go from %s to %s".formatted(reference, status, target));
        }
        status = target;
        if (!OrderStatus.UNPAID.contains(target)) {
            expiresAt = null;
        }
    }

    UUID getId() {
        return id;
    }

    String getReference() {
        return reference;
    }

    String getCustomerId() {
        return customerId;
    }

    OrderStatus getStatus() {
        return status;
    }

    Money getTotal() {
        return total;
    }

    PostalAddress getShippingAddress() {
        return shippingAddress;
    }

    List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

}
