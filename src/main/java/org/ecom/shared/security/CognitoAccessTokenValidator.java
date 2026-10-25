package org.ecom.shared.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Accepts only Cognito <em>access</em> tokens issued to our SPA client. ID tokens and tokens issued to other
 * app clients of the same user pool are rejected.
 */
class CognitoAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final String clientId;

    CognitoAccessTokenValidator(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("token_use"))) {
            return failure("Not an access token");
        }
        if (!clientId.equals(jwt.getClaimAsString("client_id"))) {
            return failure("Token was not issued to this client");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }

}
