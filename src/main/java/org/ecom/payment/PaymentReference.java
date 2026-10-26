package org.ecom.payment;

/**
 * @param providerReference identifier of the payment on the provider side (PayPal order id)
 * @param captureId         identifier of the captured funds, null until captured
 */
public record PaymentReference(String providerReference, String captureId) {
}
