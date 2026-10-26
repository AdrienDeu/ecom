package org.ecom.payment;

/**
 * The provider could not be reached or answered with an unexpected error: the outcome of the operation is unknown.
 * It may be retried with the same idempotency key, and the reconciliation job eventually settles the payment.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }

}
