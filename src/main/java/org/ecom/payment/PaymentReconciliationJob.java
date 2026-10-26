package org.ecom.payment;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "ecom.payment.reconciliation.enabled", matchIfMissing = true)
class PaymentReconciliationJob {

    private static final int BATCH_SIZE = 50;

    private final PaymentService payments;
    private final Duration staleAfter;

    PaymentReconciliationJob(PaymentService payments,
                             @Value("${ecom.payment.reconciliation.stale-after:PT15M}") Duration staleAfter) {
        this.payments = payments;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${ecom.payment.reconciliation.interval:PT5M}", initialDelayString = "${ecom.payment.reconciliation.interval:PT5M}")
    void reconcile() {
        int examined;
        do {
            examined = payments.reconcile(staleAfter, BATCH_SIZE);
        } while (examined == BATCH_SIZE);
    }

}
