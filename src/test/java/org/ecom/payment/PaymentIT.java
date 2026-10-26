package org.ecom.payment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.ecom.ApiTestSupport;
import org.ecom.IntegrationTest;
import org.ecom.inventory.InventoryService;
import org.ecom.order.OrderService;
import org.ecom.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class PaymentIT extends ApiTestSupport {

    @Autowired
    WireMockServer paypal;

    @Autowired
    PaymentService payments;

    @Autowired
    OrderService orders;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void listsEnabledMethodsWithTheirPublicSettings() throws Exception {
        mvc.perform(get("/api/payments/methods").with(customer("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("paypal"))
                .andExpect(jsonPath("$[0].settings.clientId").value("test-client"))
                .andExpect(jsonPath("$[0].settings.clientSecret").doesNotExist());
    }

    @Test
    void approvedPaymentIsCapturedAndPaysTheOrder() throws Exception {
        var productId = createProduct("19.99", 5);
        var customer = unique("alice");
        var order = placeOrder(customer, productId, 2);
        var paypalOrder = stubCreateOrder(order);

        var paymentId = startPayment(customer, order.id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.providerReference").value(paypalOrder))
                .andExpect(jsonPath("$.approvalUrl").value("https://www.sandbox.paypal.com/checkoutnow?token=" + paypalOrder))
                .andExpect(jsonPath("$.amount.amount").value(39.98))
                .andReturn();
        var payment = UUID.fromString(read(paymentId, "$.id"));
        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        paypal.verify(postRequestedFor(urlEqualTo("/v2/checkout/orders"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withHeader("PayPal-Request-Id", equalTo("initiate-" + payment))
                .withRequestBody(matchingJsonPath("$.purchase_units[0].amount", equalToJson("""
                        {"currency_code": "EUR", "value": "39.98"}"""))));

        stubCapture(paypalOrder, captured(paypalOrder, "CAPTURE-" + paypalOrder));
        capture(customer, payment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));

        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(inventory.level(productId).onHand()).isEqualTo(3);
        paypal.verify(postRequestedFor(urlEqualTo("/v2/checkout/orders/%s/capture".formatted(paypalOrder)))
                .withHeader("PayPal-Request-Id", equalTo("capture-" + payment)));

        // A second click on "pay" changes nothing
        capture(customer, payment).andExpect(jsonPath("$.status").value("CAPTURED"));
        startPayment(customer, order.id()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"));
        paypal.verify(1, postRequestedFor(urlEqualTo("/v2/checkout/orders/%s/capture".formatted(paypalOrder))));
    }

    @Test
    void reopeningThePaymentResumesThePendingOne() throws Exception {
        var customer = unique("bob");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        stubCreateOrder(order);

        String first = read(startPayment(customer, order.id()).andReturn(), "$.id");
        String second = read(startPayment(customer, order.id()).andExpect(status().isCreated()).andReturn(), "$.id");

        assertThat(second).isEqualTo(first);
        paypal.verify(1, postRequestedFor(urlEqualTo("/v2/checkout/orders"))
                .withRequestBody(containing(order.reference())));
    }

    @Test
    void declinedPaymentLetsTheCustomerRetry() throws Exception {
        var productId = createProduct("10", 1);
        var customer = unique("carol");
        var order = placeOrder(customer, productId, 1);
        var paypalOrder = stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));
        paypal.stubFor(WireMock.post(urlEqualTo("/v2/checkout/orders/%s/capture".formatted(paypalOrder))).willReturn(aResponse()
                .withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"name": "UNPROCESSABLE_ENTITY", "details": [{"issue": "INSTRUMENT_DECLINED"}]}""")));

        capture(customer, payment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("INSTRUMENT_DECLINED"));

        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.CREATED);
        assertThat(inventory.level(productId).reserved()).isEqualTo(1);
        paypal.stubFor(createOrderRequest(order).willReturn(createdOrder(paypalOrder + "-2")));
        String retry = read(startPayment(customer, order.id()).andExpect(status().isCreated()).andReturn(), "$.id");
        assertThat(retry).isNotEqualTo(payment.toString());
    }

    @Test
    void transientPaypalErrorsAreRetriedWithTheSameRequestId() throws Exception {
        var customer = unique("dave");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        paypal.stubFor(createOrderRequest(order).inScenario(order.reference()).whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        paypal.stubFor(createOrderRequest(order).inScenario(order.reference()).whenScenarioStateIs("recovered")
                .willReturn(createdOrder("PP-" + order.reference())));

        var payment = paymentId(startPayment(customer, order.id()).andExpect(status().isCreated()));

        paypal.verify(2, postRequestedFor(urlEqualTo("/v2/checkout/orders"))
                .withHeader("PayPal-Request-Id", equalTo("initiate-" + payment)));
    }

    @Test
    void unavailablePaypalKeepsThePaymentResumable() throws Exception {
        var customer = unique("erin");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        paypal.stubFor(createOrderRequest(order).willReturn(aResponse().withStatus(500)));

        startPayment(customer, order.id())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));
        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.CREATED);

        paypal.stubFor(createOrderRequest(order).willReturn(createdOrder("PP-" + order.reference())));
        startPayment(customer, order.id()).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void paymentOfACancelledOrderIsNotCaptured() throws Exception {
        var customer = unique("frank");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        var paypalOrder = stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));
        mvc.perform(post("/api/orders/{id}/cancel", order.id()).with(customer(customer))).andExpect(status().isOk());

        capture(customer, payment)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.failureReason").value("ORDER_NOT_PAYABLE"));
        paypal.verify(0, postRequestedFor(urlEqualTo("/v2/checkout/orders/%s/capture".formatted(paypalOrder))));
    }

    @Test
    void customersCannotCaptureEachOthersPayments() throws Exception {
        var customer = unique("gina");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));

        capture("mallory", payment).andExpect(status().isNotFound());
        startPayment("mallory", order.id()).andExpect(status().isNotFound());
    }

    @Test
    void approvalWebhookCapturesThePaymentOnce() throws Exception {
        var customer = unique("henry");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        var paypalOrder = stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));
        stubCapture(paypalOrder, captured(paypalOrder, "CAPTURE-" + paypalOrder));
        stubSignatureVerification("SUCCESS");
        var event = """
                {"id": "WH-%s", "event_type": "CHECKOUT.ORDER.APPROVED", "resource": {"id": "%s", "status": "APPROVED"}}
                """.formatted(paypalOrder, paypalOrder);

        webhook(event).andExpect(status().isOk());
        webhook(event).andExpect(status().isOk());

        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.PAID);
        paypal.verify(1, postRequestedFor(urlEqualTo("/v2/checkout/orders/%s/capture".formatted(paypalOrder))));
        paypal.verify(postRequestedFor(urlEqualTo("/v1/notifications/verify-webhook-signature"))
                .withRequestBody(matchingJsonPath("$.webhook_id", equalTo("test-webhook")))
                .withRequestBody(matchingJsonPath("$.transmission_id", equalTo("transmission-1")))
                .withRequestBody(matchingJsonPath("$.webhook_event.resource.id", equalTo(paypalOrder))));
        capture(customer, payment).andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void webhooksWithAnInvalidSignatureAreRejected() throws Exception {
        stubSignatureVerification("FAILURE");

        webhook("""
                {"id": "WH-forged", "event_type": "PAYMENT.CAPTURE.COMPLETED", "resource": {"id": "X"}}""")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void captureCompletedForACancelledOrderIsRefunded() throws Exception {
        var customer = unique("ivy");
        var order = placeOrder(customer, createProduct("25", 1), 1);
        var paypalOrder = stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));
        mvc.perform(post("/api/orders/{id}/cancel", order.id()).with(customer(customer))).andExpect(status().isOk());
        var capture = "CAPTURE-" + paypalOrder;
        stubRefund(capture, "REFUND-" + paypalOrder, "25.00");
        stubSignatureVerification("SUCCESS");

        webhook("""
                {"id": "WH-%s", "event_type": "PAYMENT.CAPTURE.COMPLETED",
                 "resource": {"id": "%s", "status": "COMPLETED", "supplementary_data": {"related_ids": {"order_id": "%s"}}}}
                """.formatted(capture, capture, paypalOrder))
                .andExpect(status().isOk());

        assertThat(payments.forOrder(order.id())).singleElement()
                .satisfies(view -> assertThat(view.status()).isEqualTo(PaymentStatus.REFUNDED));
        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.CANCELLED);
        paypal.verify(postRequestedFor(urlEqualTo("/v2/payments/captures/%s/refund".formatted(capture)))
                .withHeader("PayPal-Request-Id", equalTo("compensation-" + payment)));
    }

    @Test
    void backOfficeRefundsPartiallyThenFully() throws Exception {
        var customer = unique("jack");
        var order = placeOrder(customer, createProduct("30", 2), 2);
        var paypalOrder = stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));
        var capture = "CAPTURE-" + paypalOrder;
        stubCapture(paypalOrder, captured(paypalOrder, capture));
        capture(customer, payment).andExpect(jsonPath("$.status").value("CAPTURED"));

        stubRefund(capture, "REFUND-1-" + paypalOrder, "10.00");
        mvc.perform(json(post("/api/admin/payments/{id}/refund", payment).with(admin()), """
                        {"amount": 10}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.refundedAmount.amount").value(10.0));
        mvc.perform(json(post("/api/admin/payments/{id}/refund", payment).with(admin()), """
                        {"amount": 50.01}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_EXCEEDS_PAYMENT"));
        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.PAID);

        stubRefund(capture, "REFUND-2-" + paypalOrder, "50.00");
        mvc.perform(json(post("/api/admin/payments/{id}/refund", payment).with(admin()), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refunds.length()").value(2));
        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.REFUNDED);

        // PayPal then notifies the refund we issued: it is not counted twice
        stubSignatureVerification("SUCCESS");
        webhook("""
                {"id": "WH-REFUND-2-%s", "event_type": "PAYMENT.CAPTURE.REFUNDED",
                 "resource": {"id": "REFUND-2-%s", "status": "COMPLETED", "amount": {"value": "50.00", "currency_code": "EUR"},
                              "links": [{"rel": "up", "href": "https://api-m.sandbox.paypal.com/v2/payments/captures/%s"}]}}
                """.formatted(paypalOrder, paypalOrder, capture))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/payments").param("orderId", order.id().toString()).with(admin()))
                .andExpect(jsonPath("$[0].refunds.length()").value(2));
        mvc.perform(json(post("/api/admin/payments/{id}/refund", payment).with(customer(customer)), "{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconciliationCapturesApprovedPaymentsLeftBehind() throws Exception {
        var customer = unique("kate");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        var paypalOrder = stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));
        jdbc.update("update payment set updated_at = now() - interval '1 hour' where id = ?", payment);
        paypal.stubFor(WireMock.get(urlEqualTo("/v2/checkout/orders/" + paypalOrder)).willReturn(okJson("""
                {"id": "%s", "status": "APPROVED"}""".formatted(paypalOrder))));
        stubCapture(paypalOrder, captured(paypalOrder, "CAPTURE-" + paypalOrder));

        assertThat(payments.reconcile(Duration.ofMinutes(15), 100)).isPositive();

        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(jdbc.queryForObject("select checked_at is not null from payment where id = ?", Boolean.class, payment)).isTrue();
    }

    @Test
    void reconciliationCancelsPaymentsOfExpiredOrders() throws Exception {
        var customer = unique("liam");
        var order = placeOrder(customer, createProduct("10", 1), 1);
        var paypalOrder = stubCreateOrder(order);
        var payment = paymentId(startPayment(customer, order.id()));
        jdbc.update("update customer_order set expires_at = now() - interval '1 minute' where id = ?", order.id());
        orders.expireStaleOrders(100);
        jdbc.update("update payment set updated_at = now() - interval '1 hour' where id = ?", payment);
        paypal.stubFor(WireMock.get(urlEqualTo("/v2/checkout/orders/" + paypalOrder)).willReturn(okJson("""
                {"id": "%s", "status": "PAYER_ACTION_REQUIRED"}""".formatted(paypalOrder))));

        payments.reconcile(Duration.ofMinutes(15), 100);

        assertThat(payments.forOrder(order.id())).singleElement()
                .satisfies(view -> assertThat(view.status()).isEqualTo(PaymentStatus.CANCELLED));
    }

    // --- Helpers ---

    private record PlacedOrder(UUID id, String reference) {
    }

    private PlacedOrder placeOrder(String customer, UUID productId, int quantity) throws Exception {
        var address = createAddress(customer);
        addToCart(customer, productId, quantity);
        var result = mvc.perform(json(post("/api/orders").with(customer(customer)), """
                        {"addressId": "%s"}""".formatted(address)))
                .andExpect(status().isCreated())
                .andReturn();
        return new PlacedOrder(UUID.fromString(read(result, "$.id")), read(result, "$.reference"));
    }

    private ResultActions startPayment(String customer, UUID orderId) throws Exception {
        return mvc.perform(json(post("/api/payments").with(customer(customer)), """
                {"orderId": "%s", "provider": "paypal"}""".formatted(orderId)));
    }

    private ResultActions capture(String customer, UUID paymentId) throws Exception {
        return mvc.perform(post("/api/payments/{id}/capture", paymentId).with(customer(customer)));
    }

    private ResultActions webhook(String event) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/webhooks/paypal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(event)
                .header("PAYPAL-AUTH-ALGO", "SHA256withRSA")
                .header("PAYPAL-CERT-URL", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT")
                .header("PAYPAL-TRANSMISSION-ID", "transmission-1")
                .header("PAYPAL-TRANSMISSION-SIG", "signature")
                .header("PAYPAL-TRANSMISSION-TIME", "2026-10-26T10:00:00Z");
        // Sent by PayPal: neither a JWT nor a CSRF token
        return mvc.perform(request);
    }

    private UUID paymentId(ResultActions result) throws Exception {
        return UUID.fromString(read(result.andReturn(), "$.id"));
    }

    /** PayPal order whose id derives from the order reference, so that tests do not interfere. */
    private String stubCreateOrder(PlacedOrder order) {
        var paypalOrder = "PP-" + order.reference();
        paypal.stubFor(createOrderRequest(order).willReturn(createdOrder(paypalOrder)));
        return paypalOrder;
    }

    private static MappingBuilder createOrderRequest(PlacedOrder order) {
        return WireMock.post(urlEqualTo("/v2/checkout/orders")).withRequestBody(containing(order.reference()));
    }

    private static ResponseDefinitionBuilder createdOrder(String paypalOrder) {
        return okJson("""
                {"id": "%s", "status": "PAYER_ACTION_REQUIRED", "links": [
                  {"href": "https://api-m.sandbox.paypal.com/v2/checkout/orders/%s", "rel": "self", "method": "GET"},
                  {"href": "https://www.sandbox.paypal.com/checkoutnow?token=%s", "rel": "payer-action", "method": "GET"}]}
                """.formatted(paypalOrder, paypalOrder, paypalOrder));
    }

    private static String captured(String paypalOrder, String captureId) {
        return """
                {"id": "%s", "status": "COMPLETED", "purchase_units": [{"payments": {"captures": [
                  {"id": "%s", "status": "COMPLETED", "amount": {"currency_code": "EUR", "value": "10.00"}}]}}]}
                """.formatted(paypalOrder, captureId);
    }

    private void stubCapture(String paypalOrder, String response) {
        paypal.stubFor(WireMock.post(urlEqualTo("/v2/checkout/orders/%s/capture".formatted(paypalOrder))).willReturn(okJson(response)));
    }

    private void stubRefund(String captureId, String refundId, String amount) {
        paypal.stubFor(WireMock.post(urlEqualTo("/v2/payments/captures/%s/refund".formatted(captureId)))
                .withRequestBody(matchingJsonPath("$.amount.value", equalTo(amount)))
                .willReturn(okJson("""
                        {"id": "%s", "status": "COMPLETED", "amount": {"value": "%s", "currency_code": "EUR"}}
                        """.formatted(refundId, amount))));
    }

    private void stubSignatureVerification(String status) {
        paypal.stubFor(WireMock.post(urlEqualTo("/v1/notifications/verify-webhook-signature")).willReturn(okJson("""
                {"verification_status": "%s"}""".formatted(status))));
    }

}
