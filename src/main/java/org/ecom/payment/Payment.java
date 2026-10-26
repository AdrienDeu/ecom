package org.ecom.payment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Version;

import org.ecom.shared.domain.Money;
import org.ecom.shared.web.BusinessException;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Payment of an order through one provider, independent of that provider's API. A declined payment is kept for
 * the record and the customer starts a new one.
 */
@Entity
class Payment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID orderId;

    private String customerId;

    private String provider;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Embedded
    private Money amount;

    private String providerReference;

    private String captureId;

    private String approvalUrl;

    private String failureReason;

    // A handful of rows at most, read outside of transactions once the payment is settled
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "payment_refund", joinColumns = @JoinColumn(name = "payment_id"))
    private List<PaymentRefund> refunds = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant checkedAt;

    @Version
    private long version;

    protected Payment() {
    }

    Payment(UUID orderId, String customerId, String provider, Money amount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.provider = provider;
        this.amount = amount;
        this.status = PaymentStatus.INITIATED;
    }

    /** Sent with every attempt to create this payment: a retry returns the provider payment created the first time. */
    String initiationKey() {
        return "initiate-" + id;
    }

    /** Shared by the customer, the webhook and the reconciliation job, so that funds are never captured twice. */
    String captureKey() {
        return "capture-" + id;
    }

    PaymentReference reference() {
        return new PaymentReference(providerReference, captureId);
    }

    void awaitApproval(String providerReference, String approvalUrl) {
        transitionTo(PaymentStatus.PENDING_APPROVAL);
        this.providerReference = providerReference;
        this.approvalUrl = approvalUrl;
    }

    void captured(String captureId) {
        transitionTo(PaymentStatus.CAPTURED);
        this.captureId = captureId;
    }

    void fail(String reason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
    }

    void cancel(String reason) {
        transitionTo(PaymentStatus.CANCELLED);
        this.failureReason = reason;
    }

    Money refundable() {
        return refunds.stream().map(PaymentRefund::amount).reduce(amount, Money::minus);
    }

    /**
     * Records a refund, ignoring one already known (the back-office call and its webhook report the same refund).
     *
     * @return whether the payment is now fully refunded
     */
    boolean recordRefund(String providerRefundId, Money refunded, Instant now) {
        if (refunds.stream().noneMatch(refund -> refund.providerRefundId().equals(providerRefundId))) {
            if (refunded.isGreaterThan(refundable())) {
                throw new IllegalStateException("Refund %s exceeds what remains of payment %s".formatted(providerRefundId, id));
            }
            refunds.add(new PaymentRefund(providerRefundId, refunded, now));
            transitionTo(refundable().amount().signum() == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        }
        return status == PaymentStatus.REFUNDED;
    }

    void checked(Instant now) {
        this.checkedAt = now;
    }

    private void transitionTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessException("INVALID_PAYMENT_STATE", "Payment cannot go from %s to %s".formatted(status, target));
        }
        status = target;
    }

    UUID getId() {
        return id;
    }

    UUID getOrderId() {
        return orderId;
    }

    String getCustomerId() {
        return customerId;
    }

    String getProvider() {
        return provider;
    }

    PaymentStatus getStatus() {
        return status;
    }

    Money getAmount() {
        return amount;
    }

    String getProviderReference() {
        return providerReference;
    }

    String getCaptureId() {
        return captureId;
    }

    String getApprovalUrl() {
        return approvalUrl;
    }

    String getFailureReason() {
        return failureReason;
    }

    List<PaymentRefund> getRefunds() {
        return Collections.unmodifiableList(refunds);
    }

    Instant getCreatedAt() {
        return createdAt;
    }

}
