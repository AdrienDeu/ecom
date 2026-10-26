package org.ecom.payment;

import java.util.UUID;

import org.ecom.shared.domain.Money;

/**
 * @param amount         always computed by the server from the order, never sent by the client
 * @param idempotencyKey identical for every retry of the same initiation
 */
public record PaymentRequest(UUID paymentId, String orderReference, Money amount, String idempotencyKey) {
}
