package org.ecom.payment;

import java.util.Map;

import org.ecom.shared.domain.Money;

/**
 * Port implemented by each payment method (PayPal, Stripe...). Adapters are Spring beans discovered by the
 * {@link PaymentProviderRegistry}; the payment domain never depends on a provider's API.
 * <p>
 * Calls may be retried: every operation that moves money carries an idempotency key, so that a retry after a
 * timeout never charges or refunds twice. Adapters throw {@link PaymentProviderException} when the provider cannot
 * be reached (the outcome is then unknown) and {@link PaymentRejectedException} when it refuses the operation.
 */
public interface PaymentProvider {

    /** Stable identifier: "paypal", "stripe"... */
    String id();

    /** Public settings the SPA needs to display this method (e.g. PayPal client id). Never secrets. */
    Map<String, String> clientSettings();

    /** Creates the payment on the provider side, to be approved by the customer. */
    PaymentInitiation initiate(PaymentRequest request);

    /** Collects the money once the customer approved the payment. */
    PaymentResult capture(PaymentReference reference, String idempotencyKey);

    /** Current state of the payment on the provider side, used by the reconciliation job. */
    PaymentResult retrieve(PaymentReference reference);

    RefundResult refund(PaymentReference reference, Money amount, String idempotencyKey);

    /**
     * Verifies the authenticity of a webhook and translates it into a provider-independent event.
     *
     * @throws InvalidWebhookException if the signature cannot be verified
     */
    PaymentEvent parseWebhook(WebhookPayload payload);

}
