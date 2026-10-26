package org.ecom.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.ecom.shared.domain.Money;
import org.ecom.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "INITIATED, PENDING_APPROVAL", "INITIATED, FAILED", "INITIATED, CANCELLED",
            "PENDING_APPROVAL, CAPTURED", "PENDING_APPROVAL, FAILED", "PENDING_APPROVAL, CANCELLED",
            "CAPTURED, PARTIALLY_REFUNDED", "CAPTURED, REFUNDED", "PARTIALLY_REFUNDED, PARTIALLY_REFUNDED",
            "PARTIALLY_REFUNDED, REFUNDED"
    })
    void allowedTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "INITIATED, CAPTURED", "PENDING_APPROVAL, REFUNDED", "CAPTURED, FAILED", "CAPTURED, CANCELLED",
            "FAILED, PENDING_APPROVAL", "CANCELLED, CAPTURED", "REFUNDED, PARTIALLY_REFUNDED"
    })
    void forbiddenTransitions(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void refundsAreTrackedUntilNothingRemains() {
        var payment = capturedPayment("60");

        assertThat(payment.recordRefund("R1", Money.eur("10"), NOW)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refundable()).isEqualTo(Money.eur("50"));

        // The webhook of a refund already recorded changes nothing
        assertThat(payment.recordRefund("R1", Money.eur("10"), NOW)).isFalse();
        assertThat(payment.getRefunds()).hasSize(1);

        assertThat(payment.recordRefund("R2", Money.eur("50"), NOW)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundsCannotExceedTheCapturedAmount() {
        var payment = capturedPayment("20");

        assertThatIllegalStateException().isThrownBy(() -> payment.recordRefund("R1", Money.eur("20.01"), NOW));
    }

    @Test
    void openPaymentsCannotBeRefunded() {
        var payment = new Payment(UUID.randomUUID(), "alice", "paypal", Money.eur("20"));

        assertThatThrownBy(() -> payment.recordRefund("R1", Money.eur("5"), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("INITIATED");
    }

    private static Payment capturedPayment(String amount) {
        var payment = new Payment(UUID.randomUUID(), "alice", "paypal", Money.eur(amount));
        payment.awaitApproval("PP-1", null);
        payment.captured("CAPTURE-1");
        return payment;
    }

}
