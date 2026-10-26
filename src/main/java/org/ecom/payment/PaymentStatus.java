package org.ecom.payment;

import java.util.Set;

/**
 * <pre>
 * INITIATED ──▶ PENDING_APPROVAL ──▶ CAPTURED ──▶ PARTIALLY_REFUNDED ──▶ REFUNDED
 *     │                │                 └────────────────────────────────▲
 *     └────────────────┴──▶ FAILED, CANCELLED
 * </pre>
 */
public enum PaymentStatus {

    INITIATED, PENDING_APPROVAL, CAPTURED, PARTIALLY_REFUNDED, REFUNDED, FAILED, CANCELLED;

    /** The customer has not paid yet: the payment can still be captured, or abandoned. */
    static final Set<PaymentStatus> OPEN = Set.of(INITIATED, PENDING_APPROVAL);

    /** Money was collected and can be (further) refunded. */
    static final Set<PaymentStatus> REFUNDABLE = Set.of(CAPTURED, PARTIALLY_REFUNDED);

    boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case INITIATED -> target == PENDING_APPROVAL || target == FAILED || target == CANCELLED;
            case PENDING_APPROVAL -> target == CAPTURED || target == FAILED || target == CANCELLED;
            case CAPTURED, PARTIALLY_REFUNDED -> target == PARTIALLY_REFUNDED || target == REFUNDED;
            case REFUNDED, FAILED, CANCELLED -> false;
        };
    }

}
