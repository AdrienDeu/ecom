package org.ecom.payment;

import org.springframework.http.HttpHeaders;

/**
 * @param body raw request body, as signatures are computed on the exact bytes sent by the provider
 */
public record WebhookPayload(HttpHeaders headers, String body) {
}
