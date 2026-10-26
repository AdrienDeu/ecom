package org.ecom.payment;

import java.time.Instant;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import org.ecom.shared.domain.Money;

/** A refund known to the provider, whether issued from the back-office or directly on the provider's dashboard. */
@Embeddable
record PaymentRefund(String providerRefundId, @Embedded Money amount, Instant createdAt) {
}
