package org.ecom.payment;

import org.ecom.shared.domain.Money;

/**
 * A webhook translated by the provider adapter. Webhooks are the asynchronous source of truth: they complete
 * payments whose synchronous capture was interrupted (closed browser, timeout...).
 *
 * @param eventId           unique per event, used to ignore redeliveries
 * @param providerReference set for {@link Type#APPROVED}, {@link Type#CAPTURE_COMPLETED} and {@link Type#CAPTURE_DENIED}
 * @param captureId         set for every capture or refund event
 * @param refundId          set for {@link Type#REFUNDED}
 * @param amount            refunded amount for {@link Type#REFUNDED}
 */
public record PaymentEvent(String eventId, String eventType, Type type, String providerReference, String captureId,
                           String refundId, Money amount) {

    public enum Type {
        APPROVED, CAPTURE_COMPLETED, CAPTURE_DENIED, REFUNDED,
        /** An event the application does not act upon. */
        IGNORED
    }

}
