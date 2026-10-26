package org.ecom.payment.paypal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ecom.payment.InvalidWebhookException;
import org.ecom.payment.PaymentEvent;
import org.ecom.payment.PaymentInitiation;
import org.ecom.payment.PaymentProvider;
import org.ecom.payment.PaymentReference;
import org.ecom.payment.PaymentRejectedException;
import org.ecom.payment.PaymentRequest;
import org.ecom.payment.PaymentResult;
import org.ecom.payment.PaymentResult.Outcome;
import org.ecom.payment.RefundResult;
import org.ecom.payment.WebhookPayload;
import org.ecom.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.RawValue;

/**
 * PayPal Orders v2: the SPA's PayPal buttons approve the PayPal order created by {@link #initiate}, then the
 * backend captures it. See https://developer.paypal.com/docs/api/orders/v2/
 */
class PaypalPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PaypalPaymentProvider.class);

    static final String ID = "paypal";

    private static final String REQUEST_ID = "PayPal-Request-Id";
    private static final String PREFER = "Prefer";
    private static final String RETURN_REPRESENTATION = "return=representation";

    private static final Set<String> AWAITING_CUSTOMER = Set.of("CREATED", "SAVED", "PAYER_ACTION_REQUIRED");
    private static final List<String> SIGNATURE_HEADERS = List.of("paypal-auth-algo", "paypal-cert-url",
            "paypal-transmission-id", "paypal-transmission-sig", "paypal-transmission-time");

    private final PaypalClient client;
    private final PaypalProperties properties;
    private final JsonMapper json;

    PaypalPaymentProvider(PaypalClient client, PaypalProperties properties, JsonMapper json) {
        this.client = client;
        this.properties = properties;
        this.json = json;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Map<String, String> clientSettings() {
        return Map.of("clientId", properties.clientId(), "currency", properties.currency());
    }

    @Override
    public PaymentInitiation initiate(PaymentRequest request) {
        var body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "custom_id", request.paymentId().toString(),
                        "description", "Commande " + request.orderReference(),
                        "amount", amount(request.amount()))),
                "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                        "brand_name", properties.brandName(),
                        // The shipping address was chosen during checkout and is frozen on the order
                        "shipping_preference", "NO_SHIPPING",
                        "user_action", "PAY_NOW"))));
        try {
            var order = client.call("create order", rest -> rest.post().uri("/v2/checkout/orders")
                    .header(REQUEST_ID, request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class));
            return new PaymentInitiation(order.path("id").asString(), link(order, "payer-action", "approve"));
        } catch (HttpClientErrorException e) {
            throw rejected("create order", e);
        }
    }

    @Override
    public PaymentResult capture(PaymentReference reference, String idempotencyKey) {
        try {
            var order = client.call("capture order", rest -> rest.post().uri("/v2/checkout/orders/{id}/capture", reference.providerReference())
                    .header(REQUEST_ID, idempotencyKey)
                    .header(PREFER, RETURN_REPRESENTATION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(JsonNode.class));
            return captureResult(order);
        } catch (HttpClientErrorException e) {
            if (e instanceof HttpClientErrorException.NotFound) {
                return new PaymentResult(Outcome.CANCELLED, null, "ORDER_NOT_FOUND");
            }
            var issue = issue(e);
            return switch (issue) {
                case "ORDER_NOT_APPROVED" -> PaymentResult.of(Outcome.PENDING);
                // A previous attempt succeeded but its response was lost
                case "ORDER_ALREADY_CAPTURED", "DUPLICATE_REQUEST_ID" -> retrieve(reference);
                default -> {
                    if (!(e instanceof HttpClientErrorException.UnprocessableContent)) {
                        throw rejected("capture order", e);
                    }
                    // INSTRUMENT_DECLINED, PAYER_CANNOT_PAY, TRANSACTION_REFUSED...
                    yield PaymentResult.failed(issue);
                }
            };
        }
    }

    @Override
    public PaymentResult retrieve(PaymentReference reference) {
        try {
            var order = client.call("get order", rest -> rest.get().uri("/v2/checkout/orders/{id}", reference.providerReference())
                    .retrieve()
                    .body(JsonNode.class));
            var status = order.path("status").asString();
            if (AWAITING_CUSTOMER.contains(status)) {
                return PaymentResult.of(Outcome.PENDING);
            }
            return switch (status) {
                case "APPROVED" -> PaymentResult.of(Outcome.APPROVED);
                case "COMPLETED" -> captureResult(order);
                case "VOIDED" -> new PaymentResult(Outcome.CANCELLED, null, "ORDER_VOIDED");
                default -> throw new PaymentRejectedException("UNKNOWN_STATUS", "Unexpected PayPal order status " + status);
            };
        } catch (HttpClientErrorException e) {
            if (e instanceof HttpClientErrorException.NotFound) {
                // PayPal deletes orders that were never approved
                return new PaymentResult(Outcome.CANCELLED, null, "ORDER_NOT_FOUND");
            }
            throw rejected("get order", e);
        }
    }

    @Override
    public RefundResult refund(PaymentReference reference, Money amount, String idempotencyKey) {
        try {
            var refund = client.call("refund capture", rest -> rest.post().uri("/v2/payments/captures/{id}/refund", reference.captureId())
                    .header(REQUEST_ID, idempotencyKey)
                    .header(PREFER, RETURN_REPRESENTATION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amount(amount)))
                    .retrieve()
                    .body(JsonNode.class));
            var status = refund.path("status").asString();
            if (status.equals("CANCELLED") || status.equals("FAILED")) {
                throw new PaymentRejectedException("REFUND_" + status, "PayPal refund " + status);
            }
            return new RefundResult(refund.path("id").asString(), money(refund.path("amount")));
        } catch (HttpClientErrorException e) {
            throw rejected("refund capture", e);
        }
    }

    /**
     * Verifies the notification with PayPal (https://developer.paypal.com/api/rest/webhooks/rest/#link-verifysignature)
     * before trusting anything in it.
     */
    @Override
    public PaymentEvent parseWebhook(WebhookPayload payload) {
        var verification = json.createObjectNode();
        for (var header : SIGNATURE_HEADERS) {
            var value = payload.headers().getFirst(header);
            if (value == null) {
                throw new InvalidWebhookException("Missing header " + header);
            }
            verification.put(header.substring("paypal-".length()).replace('-', '_'), value);
        }
        verification.put("webhook_id", properties.webhookId());
        JsonNode event;
        try {
            event = json.readTree(payload.body());
        } catch (JacksonException e) {
            throw new InvalidWebhookException("Malformed webhook body");
        }
        // The event is embedded as received: re-serializing it could alter the signed content
        verification.putRawValue("webhook_event", new RawValue(payload.body()));

        String status;
        try {
            status = client.call("verify webhook", rest -> rest.post().uri("/v1/notifications/verify-webhook-signature")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(verification))
                    .retrieve()
                    .body(JsonNode.class)).path("verification_status").asString();
        } catch (HttpClientErrorException e) {
            throw new InvalidWebhookException("Signature verification refused: " + e.getStatusCode());
        }
        if (!"SUCCESS".equals(status)) {
            throw new InvalidWebhookException("Invalid signature for event " + event.path("id").asString());
        }
        return event(event);
    }

    private static PaymentEvent event(JsonNode event) {
        var id = event.path("id").asString();
        var type = event.path("event_type").asString();
        var resource = event.path("resource");
        var orderId = resource.path("supplementary_data").path("related_ids").path("order_id").asString(null);
        return switch (type) {
            case "CHECKOUT.ORDER.APPROVED" -> new PaymentEvent(id, type, PaymentEvent.Type.APPROVED, resource.path("id").asString(), null, null, null);
            case "PAYMENT.CAPTURE.COMPLETED" -> new PaymentEvent(id, type, PaymentEvent.Type.CAPTURE_COMPLETED, orderId, resource.path("id").asString(), null, null);
            case "PAYMENT.CAPTURE.DENIED" -> new PaymentEvent(id, type, PaymentEvent.Type.CAPTURE_DENIED, orderId, resource.path("id").asString(), null, null);
            // The resource is the refund; its "up" link points to the refunded capture
            case "PAYMENT.CAPTURE.REFUNDED" -> new PaymentEvent(id, type, PaymentEvent.Type.REFUNDED, null,
                    lastPathSegment(link(resource, "up")), resource.path("id").asString(), money(resource.path("amount")));
            default -> new PaymentEvent(id, type, PaymentEvent.Type.IGNORED, null, null, null, null);
        };
    }

    private static PaymentResult captureResult(JsonNode order) {
        var capture = order.path("purchase_units").path(0).path("payments").path("captures").path(0);
        return switch (capture.path("status").asString()) {
            // Refunds are recorded from their own webhook
            case "COMPLETED", "PARTIALLY_REFUNDED", "REFUNDED" -> PaymentResult.captured(capture.path("id").asString());
            // e.g. eCheck: the PAYMENT.CAPTURE.COMPLETED or DENIED webhook settles it later
            case "PENDING" -> PaymentResult.of(Outcome.PENDING);
            case "DECLINED", "FAILED" -> PaymentResult.failed("CAPTURE_" + capture.path("status").asString());
            default -> throw new PaymentRejectedException("UNKNOWN_STATUS", "Unexpected PayPal capture " + capture);
        };
    }

    private static Map<String, String> amount(Money money) {
        return Map.of("currency_code", money.currency(), "value", money.amount().toPlainString());
    }

    private static Money money(JsonNode amount) {
        return new Money(new BigDecimal(amount.path("value").asString()), amount.path("currency_code").asString());
    }

    private static String link(JsonNode resource, String... relations) {
        for (var relation : relations) {
            for (var link : resource.path("links")) {
                if (relation.equals(link.path("rel").asString())) {
                    return link.path("href").asString();
                }
            }
        }
        return null;
    }

    private static String lastPathSegment(String url) {
        return url == null ? null : url.substring(url.lastIndexOf('/') + 1);
    }

    /** PayPal errors: {@code {"name": "UNPROCESSABLE_ENTITY", "details": [{"issue": "INSTRUMENT_DECLINED"}]}}. */
    private String issue(HttpClientErrorException e) {
        try {
            var error = json.readTree(e.getResponseBodyAsString());
            var issue = error.path("details").path(0).path("issue").asString(null);
            return issue != null ? issue : error.path("name").asString("HTTP_" + e.getStatusCode().value());
        } catch (JacksonException notJson) {
            return "HTTP_" + e.getStatusCode().value();
        }
    }

    private PaymentRejectedException rejected(String operation, HttpClientErrorException e) {
        var issue = issue(e);
        log.warn("PayPal refused {}: {} {}", operation, e.getStatusCode(), issue);
        return new PaymentRejectedException(issue, "PayPal refused %s: %s".formatted(operation, issue));
    }

}
