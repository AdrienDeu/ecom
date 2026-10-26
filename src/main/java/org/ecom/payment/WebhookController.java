package org.ecom.payment;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint for provider notifications: no JWT nor CSRF token, authenticity is established by the provider
 * adapter verifying the signature. Any non-2xx response makes the provider redeliver the event later.
 */
@Hidden
@RestController
class WebhookController {

    private final PaymentService payments;

    WebhookController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/api/webhooks/{provider}")
    void receive(@PathVariable String provider, @RequestHeader HttpHeaders headers, @RequestBody String body) {
        payments.handleWebhook(provider, new WebhookPayload(headers, body));
    }

}
