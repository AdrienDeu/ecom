package org.ecom.payment.paypal;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** PayPal is only offered when enabled: credentials are then mandatory, and checked at startup. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("ecom.payment.providers.paypal.enabled")
@EnableConfigurationProperties(PaypalProperties.class)
class PaypalConfiguration {

    @Bean
    PaypalPaymentProvider paypalPaymentProvider(RestClient.Builder restClient, PaypalProperties properties, Clock clock, JsonMapper json) {
        return new PaypalPaymentProvider(new PaypalClient(restClient, properties, clock), properties, json);
    }

}
