package org.ecom.payment;

/**
 * The provider refused the operation (invalid request, refund above the captured amount...). Retrying it is useless.
 */
public class PaymentRejectedException extends RuntimeException {

    private final String reason;

    public PaymentRejectedException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }

}
