package org.ecom.payment;

/**
 * @param providerReference identifier of the payment on the provider side (PayPal order id)
 * @param approvalUrl       page where the customer approves the payment, for redirect-based flows; may be null
 */
public record PaymentInitiation(String providerReference, String approvalUrl) {
}
