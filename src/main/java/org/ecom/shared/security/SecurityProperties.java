package org.ecom.shared.security;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param issuerUri Cognito user pool issuer, e.g. {@code https://cognito-idp.eu-west-3.amazonaws.com/<pool-id>}
 * @param clientId  Cognito app client of the SPA; access tokens carry it in {@code client_id} (they have no {@code aud})
 */
@Validated
@ConfigurationProperties("ecom.security")
public record SecurityProperties(@NotBlank String issuerUri, @NotBlank String clientId) {
}
