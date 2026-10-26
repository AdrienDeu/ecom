package org.ecom.payment.paypal;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.ecom.payment.PaymentProviderException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * HTTP access to the PayPal REST API: OAuth2 client credentials token cached until shortly before it expires,
 * explicit timeouts, retries on network errors and 5xx only, and a circuit breaker so that an unavailable PayPal
 * fails fast instead of piling up waiting requests.
 * <p>
 * Retrying is safe because every call that changes state carries a {@code PayPal-Request-Id}: PayPal returns the
 * result of the first call instead of executing it again.
 */
class PaypalClient {

    /** Refresh the token a bit before PayPal considers it expired. */
    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final RestClient rest;
    private final PaypalProperties properties;
    private final Clock clock;
    private final RetryTemplate retry;
    private final CircuitBreaker circuitBreaker;

    private volatile AccessToken token;

    PaypalClient(RestClient.Builder builder, PaypalProperties properties, Clock clock) {
        // HTTP/1.1: the JDK client would otherwise attempt an h2c upgrade on plain HTTP (local stubs)
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.connectTimeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.rest = builder.clone().baseUrl(properties.baseUrl().toString()).requestFactory(requestFactory).build();
        this.properties = properties;
        this.clock = clock;
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(2)
                .delay(Duration.ofMillis(200))
                .multiplier(2)
                .predicate(PaypalClient::isTransient)
                .build());
        this.circuitBreaker = CircuitBreaker.of("paypal", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // PayPal refusing a request (4xx) says nothing about its availability
                .recordException(PaypalClient::isTransient)
                .build());
    }

    /**
     * Runs an authenticated call. {@link HttpClientErrorException}s (4xx) are left to the caller, which knows what
     * they mean for its operation; anything else becomes a {@link PaymentProviderException}.
     */
    <T> T call(String operation, Function<RestClient, T> request) {
        try {
            return circuitBreaker.executeSupplier(() -> retry.invoke(() -> {
                try {
                    return request.apply(rest.mutate().defaultHeaders(headers -> headers.setBearerAuth(accessToken())).build());
                } catch (HttpClientErrorException.Unauthorized e) {
                    token = null; // revoked or expired early: the retry fetches a new one
                    throw e;
                }
            }));
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new PaymentProviderException("PayPal circuit breaker is open, %s not attempted".formatted(operation), e);
        } catch (RestClientException e) {
            throw new PaymentProviderException("PayPal %s failed: %s".formatted(operation, e.getMessage()), e);
        }
    }

    private String accessToken() {
        var current = token;
        if (current == null || current.expiresAt().isBefore(clock.instant())) {
            synchronized (this) {
                current = token;
                if (current == null || current.expiresAt().isBefore(clock.instant())) {
                    current = fetchToken();
                    token = current;
                }
            }
        }
        return current.value();
    }

    private AccessToken fetchToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        JsonNode response;
        try {
            response = rest.post().uri("/v1/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            // Wrong credentials: not worth retrying, and not the caller's operation being refused
            throw new PaymentProviderException("PayPal authentication failed with " + e.getStatusCode(), e);
        }
        var expiresIn = Duration.ofSeconds(response.path("expires_in").asLong());
        return new AccessToken(response.path("access_token").asString(), clock.instant().plus(expiresIn).minus(TOKEN_EXPIRY_MARGIN));
    }

    /** 401 included: the token is renewed before the retry. */
    private static boolean isTransient(Throwable e) {
        return e instanceof ResourceAccessException || e instanceof HttpServerErrorException
               || e instanceof HttpClientErrorException.Unauthorized || e instanceof HttpClientErrorException.TooManyRequests;
    }

    private record AccessToken(String value, Instant expiresAt) {
    }

}
