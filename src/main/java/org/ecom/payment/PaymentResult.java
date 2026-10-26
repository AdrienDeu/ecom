package org.ecom.payment;

/**
 * State of a payment as reported by the provider.
 *
 * @param captureId set when {@code outcome} is {@link Outcome#CAPTURED}
 * @param reason    provider-specific detail on a failure (e.g. {@code INSTRUMENT_DECLINED}), for logs and audits
 */
public record PaymentResult(Outcome outcome, String captureId, String reason) {

    public enum Outcome {
        /** Waiting for the customer: not approved yet, or capture still being processed by the provider. */
        PENDING,
        /** Approved by the customer, the money can be captured. */
        APPROVED,
        CAPTURED,
        /** Declined by the provider: the customer may retry, possibly with another method. */
        FAILED,
        /** Voided or expired on the provider side. */
        CANCELLED
    }

    public static PaymentResult of(Outcome outcome) {
        return new PaymentResult(outcome, null, null);
    }

    public static PaymentResult captured(String captureId) {
        return new PaymentResult(Outcome.CAPTURED, captureId, null);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(Outcome.FAILED, null, reason);
    }

}
