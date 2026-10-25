package org.ecom.order;

import java.util.Set;

/**
 * <pre>
 * CREATED ──▶ PAYMENT_PENDING ──▶ PAID ──▶ SHIPPED ──▶ DELIVERED
 *    │  ▲            │              │         │           │
 *    │  └─ (failed) ─┤              └─────────┴───────────┴──▶ REFUNDED
 *    └──────────────▶└──▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {

    CREATED, PAYMENT_PENDING, PAID, SHIPPED, DELIVERED, CANCELLED, REFUNDED;

    /** Statuses holding a stock reservation and expiring if not paid in time. */
    static final Set<OrderStatus> UNPAID = Set.of(CREATED, PAYMENT_PENDING);

    boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PAYMENT_PENDING || target == CANCELLED;
            case PAYMENT_PENDING -> target == PAID || target == CREATED || target == CANCELLED;
            case PAID -> target == SHIPPED || target == REFUNDED;
            case SHIPPED -> target == DELIVERED || target == REFUNDED;
            case DELIVERED -> target == REFUNDED;
            case CANCELLED, REFUNDED -> false;
        };
    }

}
