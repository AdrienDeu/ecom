package org.ecom.payment;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.ecom.payment.PaymentAttempt.Operation;
import org.springframework.stereotype.Component;

/**
 * Payment metrics. Tags only carry bounded values (provider, outcome, provider error code): never a payment,
 * order or customer identifier, which would explode the number of time series.
 */
@Component
class PaymentMetrics {

    /** Provider error codes look like {@code INSTRUMENT_DECLINED}; anything else is collapsed. */
    private static final Pattern REASON = Pattern.compile("^[A-Z0-9_]{1,40}$");

    private final MeterRegistry registry;

    PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** A payment reached a final outcome for the customer: paid, refused or abandoned. */
    void settled(Payment payment) {
        var outcome = switch (payment.getStatus()) {
            case CAPTURED -> "success";
            case FAILED -> "failed";
            case CANCELLED -> "cancelled";
            default -> null;
        };
        if (outcome != null) {
            registry.counter("ecom.payment.attempts",
                    "provider", payment.getProvider(),
                    "outcome", outcome,
                    "reason", outcome.equals("success") ? "none" : reason(payment.getFailureReason()))
                    .increment();
        }
    }

    /** Latency of a call to the provider, by operation and outcome (succeeded, rejected, error). */
    void call(String provider, Operation operation, PaymentAttempt.Outcome outcome, Duration duration) {
        Timer.builder("ecom.payment.duration")
                .description("Duration of calls to payment providers")
                .tag("provider", provider)
                .tag("operation", operation.name().toLowerCase(Locale.ROOT))
                .tag("outcome", outcome.name().toLowerCase(Locale.ROOT))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    /** @param result processed, duplicate or rejected (invalid signature) */
    void webhook(String provider, String type, String result) {
        registry.counter("ecom.payment.webhooks", "provider", provider, "type", type, "result", result).increment();
    }

    void refunded(String provider) {
        registry.counter("ecom.payment.refunds", "provider", provider).increment();
    }

    private static String reason(String reason) {
        return reason != null && REASON.matcher(reason).matches() ? reason : "OTHER";
    }

}
