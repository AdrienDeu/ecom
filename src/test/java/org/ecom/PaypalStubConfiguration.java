package org.ecom;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Stands in for the PayPal REST API in integration tests; each test registers the responses it needs.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PaypalStubConfiguration {

    @Bean(destroyMethod = "stop")
    WireMockServer paypalServer() {
        var server = new WireMockServer(options().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo("/v1/oauth2/token")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"access_token": "test-token", "token_type": "Bearer", "expires_in": 32400}""")));
        return server;
    }

    @Bean
    DynamicPropertyRegistrar paypalProperties(WireMockServer paypalServer) {
        return registry -> registry.add("ecom.payment.providers.paypal.base-url", paypalServer::baseUrl);
    }

}
