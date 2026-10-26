package org.ecom.payment;

import org.ecom.shared.domain.Money;

public record RefundResult(String refundId, Money amount) {
}
