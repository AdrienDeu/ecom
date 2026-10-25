package org.ecom.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoAccessTokenValidatorTest {

    private final CognitoAccessTokenValidator validator = new CognitoAccessTokenValidator("spa-client");

    @Test
    void acceptsAccessTokenOfOurClient() {
        assertThat(validator.validate(token(Map.of("token_use", "access", "client_id", "spa-client"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsIdToken() {
        assertThat(validator.validate(token(Map.of("token_use", "id", "client_id", "spa-client"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenOfAnotherClient() {
        assertThat(validator.validate(token(Map.of("token_use", "access", "client_id", "other"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutClientId() {
        assertThat(validator.validate(token(Map.of("token_use", "access"))).hasErrors()).isTrue();
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(claims)).build();
    }

}
