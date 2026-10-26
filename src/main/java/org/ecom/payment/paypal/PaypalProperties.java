package org.ecom.payment.paypal;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl   {@code https://api-m.sandbox.paypal.com}, or {@code https://api-m.paypal.com} for live payments
 * @param webhookId id of the webhook registered in the PayPal app, required to verify notifications
 * @param brandName shown to the customer on PayPal's pages
 */
@Validated
@ConfigurationProperties("ecom.payment.providers.paypal")
record PaypalProperties(boolean enabled,
                        @NotNull URI baseUrl,
                        @NotBlank String clientId,
                        @NotBlank String clientSecret,
                        @NotBlank String webhookId,
                        @DefaultValue("EUR") String currency,
                        @DefaultValue("ecom") String brandName,
                        @DefaultValue("2s") Duration connectTimeout,
                        @DefaultValue("10s") Duration readTimeout) {
}
