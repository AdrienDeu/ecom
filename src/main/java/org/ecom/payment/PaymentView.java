package org.ecom.payment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import org.ecom.shared.domain.Money;

public record PaymentView(UUID id, UUID orderId, String provider, PaymentStatus status, Money amount,
                          Money refundedAmount,
                          @Schema(nullable = true, description = "Provider-side id, e.g. the PayPal order id") String providerReference,
                          @Schema(nullable = true) String approvalUrl,
                          @Schema(nullable = true, description = "Why the payment failed or was cancelled") String failureReason,
                          List<Refund> refunds, Instant createdAt) {

    static PaymentView of(Payment payment) {
        var refunds = payment.getRefunds().stream()
                .map(refund -> new Refund(refund.providerRefundId(), refund.amount(), refund.createdAt()))
                .toList();
        return new PaymentView(payment.getId(), payment.getOrderId(), payment.getProvider(), payment.getStatus(),
                payment.getAmount(), payment.getAmount().minus(payment.refundable()), payment.getProviderReference(),
                payment.getApprovalUrl(), payment.getFailureReason(), refunds, payment.getCreatedAt());
    }

    public record Refund(String providerRefundId, Money amount, Instant createdAt) {
    }

}
