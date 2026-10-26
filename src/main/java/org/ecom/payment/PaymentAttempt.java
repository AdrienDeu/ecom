package org.ecom.payment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** One call to a provider, kept for audits and support: what was asked, with which idempotency key, and the answer. */
@Entity
class PaymentAttempt {

    enum Operation { INITIATE, CAPTURE, RETRIEVE, REFUND }

    enum Outcome {
        SUCCEEDED,
        /** Refused by the provider. */
        REJECTED,
        /** Provider unreachable: the outcome is unknown. */
        ERROR
    }

    private static final int DETAIL_MAX_LENGTH = 200;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    private Operation operation;

    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private String detail;

    @CreationTimestamp
    private Instant createdAt;

    protected PaymentAttempt() {
    }

    PaymentAttempt(UUID paymentId, Operation operation, String idempotencyKey, Outcome outcome, String detail) {
        this.paymentId = paymentId;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.outcome = outcome;
        this.detail = detail == null || detail.length() <= DETAIL_MAX_LENGTH ? detail : detail.substring(0, DETAIL_MAX_LENGTH);
    }

    Operation getOperation() {
        return operation;
    }

    Outcome getOutcome() {
        return outcome;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

}
