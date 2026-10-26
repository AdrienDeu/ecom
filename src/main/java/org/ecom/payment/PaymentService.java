package org.ecom.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.ecom.order.OrderService;
import org.ecom.order.OrderStatus;
import org.ecom.payment.PaymentAttempt.Operation;
import org.ecom.payment.PaymentAttempt.Outcome;
import org.ecom.shared.domain.Money;
import org.ecom.shared.web.BusinessException;
import org.ecom.shared.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates payments between the order module and the providers. Provider calls are made outside of any database
 * transaction: a slow provider never holds row locks or connections, and each step commits on its own.
 * <p>
 * A payment can be settled by three concurrent paths: the customer's capture, the provider's webhook and the
 * reconciliation job. They share the same idempotency key and each state change re-reads the payment, so whichever
 * comes first wins and the others become no-ops.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final Set<OrderStatus> REFUNDABLE_ORDER = Set.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final WebhookEventRepository webhookEvents;
    private final PaymentProviderRegistry providers;
    private final OrderService orders;
    private final TransactionTemplate tx;
    private final Clock clock;

    PaymentService(PaymentRepository payments, PaymentAttemptRepository attempts, WebhookEventRepository webhookEvents,
                   PaymentProviderRegistry providers, OrderService orders, TransactionTemplate tx, Clock clock) {
        this.payments = payments;
        this.attempts = attempts;
        this.webhookEvents = webhookEvents;
        this.providers = providers;
        this.orders = orders;
        this.tx = tx;
        this.clock = clock;
    }

    public List<PaymentMethodView> methods() {
        return providers.all().stream().map(provider -> new PaymentMethodView(provider.id(), provider.clientSettings())).toList();
    }

    /**
     * Starts the payment of an unpaid order, or resumes the one in progress (page reloaded, PayPal popup closed...).
     * The amount is always the order total.
     */
    public PaymentView start(String customerId, UUID orderId, String providerId) {
        var provider = providers.get(providerId);
        var payment = inTransaction(() -> {
            var order = orders.customerOrder(customerId, orderId);
            var live = payments.findLive(orderId);
            if (live.isPresent() && !PaymentStatus.OPEN.contains(live.get().getStatus())) {
                throw new BusinessException("ORDER_ALREADY_PAID", "Order %s is already paid".formatted(order.reference()));
            }
            if (live.isPresent() && live.get().getProvider().equals(providerId)) {
                return live.get();
            }
            live.ifPresent(other -> {
                // The customer switched to another method: the previous one can no longer be captured
                other.cancel("METHOD_SWITCHED");
                releaseOrder(other);
                payments.flush();
            });
            // Re-read: releasing the previous payment may have changed it
            if (orders.get(orderId).status() != OrderStatus.CREATED) {
                throw new BusinessException("ORDER_NOT_PAYABLE", "Order %s cannot be paid".formatted(order.reference()));
            }
            return payments.save(new Payment(orderId, customerId, providerId, order.total()));
        });
        if (payment.getStatus() == PaymentStatus.PENDING_APPROVAL) {
            return PaymentView.of(payment);
        }

        PaymentInitiation initiation;
        try {
            var request = new PaymentRequest(payment.getId(), orders.get(orderId).reference(), payment.getAmount(), payment.initiationKey());
            initiation = call(payment, Operation.INITIATE, payment.initiationKey(), () -> provider.initiate(request));
        } catch (PaymentRejectedException e) {
            update(payment.getId(), p -> p.fail(e.reason()));
            throw new BusinessException("PAYMENT_REJECTED", "The payment was refused by the provider");
        }
        // If the order expired in the meantime this rolls back, and the reconciliation job cancels the payment
        return PaymentView.of(inTransaction(() -> {
            var p = load(payment.getId());
            p.awaitApproval(initiation.providerReference(), initiation.approvalUrl());
            orders.markPaymentPending(p.getOrderId());
            return p;
        }));
    }

    /**
     * Captures a payment the customer approved. Calling it again once settled returns the payment unchanged; a
     * declined payment comes back {@code FAILED} and the customer can start a new one.
     */
    public PaymentView capture(String customerId, UUID paymentId) {
        var payment = inTransaction(() -> payments.findByIdAndCustomerId(paymentId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId)));
        return switch (payment.getStatus()) {
            case PENDING_APPROVAL -> PaymentView.of(completeCapture(payment));
            case CAPTURED, PARTIALLY_REFUNDED, REFUNDED -> PaymentView.of(payment);
            case INITIATED, FAILED, CANCELLED -> throw new BusinessException("INVALID_PAYMENT_STATE",
                    "Payment cannot be captured from %s".formatted(payment.getStatus()));
        };
    }

    // --- Back-office ---

    public List<PaymentView> forOrder(UUID orderId) {
        return inTransaction(() -> payments.findByOrderIdOrderByCreatedAt(orderId).stream().map(PaymentView::of).toList());
    }

    /**
     * Refunds part or all of a captured payment. The order becomes {@code REFUNDED} once nothing remains to refund.
     *
     * @param amount null for whatever remains
     */
    public PaymentView refund(UUID paymentId, BigDecimal amount) {
        var payment = inTransaction(() -> load(paymentId));
        if (!PaymentStatus.REFUNDABLE.contains(payment.getStatus())) {
            throw new BusinessException("INVALID_PAYMENT_STATE", "Payment cannot be refunded from %s".formatted(payment.getStatus()));
        }
        var remaining = payment.refundable();
        var refund = amount == null ? remaining : new Money(amount, remaining.currency());
        if (refund.amount().signum() == 0 || refund.isGreaterThan(remaining)) {
            throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "At most %s %s can be refunded".formatted(remaining.amount(), remaining.currency()));
        }
        try {
            return PaymentView.of(issueRefund(payment, refund, "refund-" + UUID.randomUUID()));
        } catch (PaymentRejectedException e) {
            throw new BusinessException("REFUND_REJECTED", "The refund was refused by the provider");
        }
    }

    // --- Webhooks ---

    public void handleWebhook(String providerId, WebhookPayload payload) {
        var event = providers.get(providerId).parseWebhook(payload);
        if (webhookEvents.existsByProviderAndEventId(providerId, event.eventId())) {
            log.debug("Webhook {} already processed", event.eventId());
            return;
        }
        log.info("Webhook {} {} for {}", event.eventType(), event.eventId(),
                event.providerReference() != null ? event.providerReference() : event.captureId());
        switch (event.type()) {
            case APPROVED -> byReference(providerId, event.providerReference())
                    .filter(p -> p.getStatus() == PaymentStatus.PENDING_APPROVAL)
                    .ifPresent(this::completeCapture);
            case CAPTURE_COMPLETED -> byReference(providerId, event.providerReference())
                    .ifPresent(p -> apply(p.getId(), PaymentResult.captured(event.captureId())));
            case CAPTURE_DENIED -> byReference(providerId, event.providerReference())
                    .ifPresent(p -> apply(p.getId(), PaymentResult.failed("CAPTURE_DENIED")));
            // Also covers refunds issued from the provider's dashboard
            case REFUNDED -> inTransaction(() -> payments.findByProviderAndCaptureId(providerId, event.captureId()))
                    .filter(p -> PaymentStatus.REFUNDABLE.contains(p.getStatus()))
                    .ifPresent(p -> recordRefund(p.getId(), event.refundId(), event.amount()));
            case IGNORED -> {
                // nothing to do
            }
        }
        try {
            webhookEvents.save(new WebhookEvent(providerId, event.eventId(), event.eventType()));
        } catch (DataIntegrityViolationException e) {
            log.debug("Webhook {} processed concurrently", event.eventId());
        }
    }

    // --- Reconciliation ---

    /**
     * Settles a batch of payments left open for longer than {@code staleAfter}: the customer closed the browser
     * after approving, a capture timed out, a webhook was lost...
     *
     * @return the number of payments examined
     */
    public int reconcile(Duration staleAfter, int batchSize) {
        var now = clock.instant();
        var ids = inTransaction(() -> {
            var claimed = payments.lockStale(now.minus(staleAfter), batchSize);
            payments.findAllById(claimed).forEach(p -> p.checked(now));
            return claimed;
        });
        for (var id : ids) {
            try {
                reconcile(inTransaction(() -> load(id)));
            } catch (RuntimeException e) {
                log.warn("Reconciliation of payment {} failed, will retry", id, e);
            }
        }
        return ids.size();
    }

    private void reconcile(Payment payment) {
        if (payment.getStatus() == PaymentStatus.INITIATED) {
            // The provider reference was never stored, so the customer could not approve anything
            update(payment.getId(), p -> {
                p.cancel("INITIATION_INCOMPLETE");
                releaseOrder(p);
            });
            return;
        }
        var provider = providers.get(payment.getProvider());
        var result = call(payment, Operation.RETRIEVE, null, () -> provider.retrieve(payment.reference()));
        switch (result.outcome()) {
            case APPROVED -> completeCapture(payment);
            case PENDING -> {
                if (orderStatus(payment) != OrderStatus.PAYMENT_PENDING) {
                    apply(payment.getId(), new PaymentResult(PaymentResult.Outcome.CANCELLED, null, "ORDER_NOT_PAYABLE"));
                }
            }
            case CAPTURED, FAILED, CANCELLED -> apply(payment.getId(), result);
        }
        log.info("Payment {} reconciled: provider reports {}", payment.getId(), result.outcome());
    }

    // --- Settlement ---

    private Payment completeCapture(Payment payment) {
        if (orderStatus(payment) != OrderStatus.PAYMENT_PENDING) {
            // Cancelled or expired while the customer was approving: do not collect money for it
            return apply(payment.getId(), new PaymentResult(PaymentResult.Outcome.CANCELLED, null, "ORDER_NOT_PAYABLE"));
        }
        var provider = providers.get(payment.getProvider());
        var result = call(payment, Operation.CAPTURE, payment.captureKey(),
                () -> provider.capture(payment.reference(), payment.captureKey()));
        return apply(payment.getId(), result);
    }

    /** Applies the provider's view of an open payment to the payment and its order. */
    private Payment apply(UUID paymentId, PaymentResult result) {
        var refundNeeded = new AtomicBoolean();
        var payment = retryOnConflict(() -> inTransaction(() -> {
            var p = load(paymentId);
            if (!PaymentStatus.OPEN.contains(p.getStatus())) {
                return p; // already settled by a concurrent path
            }
            switch (result.outcome()) {
                case CAPTURED -> {
                    p.captured(result.captureId());
                    if (orderStatus(p) == OrderStatus.PAYMENT_PENDING) {
                        orders.markPaid(p.getOrderId());
                    } else {
                        refundNeeded.set(true);
                    }
                }
                case FAILED -> {
                    p.fail(result.reason());
                    releaseOrder(p);
                }
                case CANCELLED -> {
                    p.cancel(result.reason() != null ? result.reason() : "CANCELLED_BY_PROVIDER");
                    releaseOrder(p);
                }
                case PENDING, APPROVED -> {
                    // still waiting for the customer or the provider
                }
            }
            return p;
        }));
        if (refundNeeded.get()) {
            return compensate(payment);
        }
        return payment;
    }

    /** Money was captured for an order that was cancelled in the meantime: give it back. */
    private Payment compensate(Payment payment) {
        log.warn("Payment {} captured for order {} which is no longer payable, refunding it", payment.getId(), payment.getOrderId());
        try {
            return issueRefund(payment, payment.refundable(), "compensation-" + payment.getId());
        } catch (RuntimeException e) {
            log.error("Automatic refund of payment {} failed: it must be refunded manually", payment.getId(), e);
            return payment;
        }
    }

    private Payment issueRefund(Payment payment, Money amount, String idempotencyKey) {
        var provider = providers.get(payment.getProvider());
        var refund = call(payment, Operation.REFUND, idempotencyKey, () -> provider.refund(payment.reference(), amount, idempotencyKey));
        return recordRefund(payment.getId(), refund.refundId(), refund.amount());
    }

    private Payment recordRefund(UUID paymentId, String refundId, Money amount) {
        return retryOnConflict(() -> inTransaction(() -> {
            var p = load(paymentId);
            if (p.recordRefund(refundId, amount, clock.instant()) && REFUNDABLE_ORDER.contains(orderStatus(p))) {
                orders.markRefunded(p.getOrderId());
            }
            return p;
        }));
    }

    // --- Helpers ---

    /** Calls the provider and keeps track of the attempt, whatever its outcome. */
    private <T> T call(Payment payment, Operation operation, String idempotencyKey, Supplier<T> action) {
        try {
            var result = action.get();
            attempts.save(new PaymentAttempt(payment.getId(), operation, idempotencyKey, Outcome.SUCCEEDED, String.valueOf(result)));
            return result;
        } catch (PaymentRejectedException e) {
            attempts.save(new PaymentAttempt(payment.getId(), operation, idempotencyKey, Outcome.REJECTED, e.reason()));
            throw e;
        } catch (PaymentProviderException e) {
            attempts.save(new PaymentAttempt(payment.getId(), operation, idempotencyKey, Outcome.ERROR, e.getMessage()));
            throw e;
        }
    }

    /** Puts the order back to {@code CREATED} so that the customer can pay again. */
    private void releaseOrder(Payment payment) {
        if (orderStatus(payment) == OrderStatus.PAYMENT_PENDING) {
            orders.markPaymentFailed(payment.getOrderId());
        }
    }

    private OrderStatus orderStatus(Payment payment) {
        return orders.get(payment.getOrderId()).status();
    }

    private Optional<Payment> byReference(String providerId, String providerReference) {
        var payment = inTransaction(() -> payments.findByProviderAndProviderReference(providerId, providerReference));
        if (payment.isEmpty()) {
            // The reference comes from the webhook payload: strip line breaks to prevent log forging.
            log.warn("No {} payment for provider reference {}", providerId, providerReference.replaceAll("[\\r\\n]", "_"));
        }
        return payment;
    }

    private Payment load(UUID paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    private void update(UUID paymentId, Consumer<Payment> change) {
        inTransaction(() -> {
            var payment = load(paymentId);
            change.accept(payment);
            return payment;
        });
    }

    private <T> T inTransaction(Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    /** Two settlement paths raced: the second one re-reads the payment, now settled, and does nothing. */
    private static <T> T retryOnConflict(Supplier<T> action) {
        try {
            return action.get();
        } catch (OptimisticLockingFailureException e) {
            return action.get();
        }
    }

}
