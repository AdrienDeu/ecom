package org.ecom.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    /**
     * Actuator endpoints (health, prometheus) are served on the management port, which is only reachable from
     * inside the VPC (load balancer health checks, metrics collector), never through the public load balancer.
     */
    @Bean
    @Order(1)
    SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        // Guest carts: a token is optional, identified by the X-Cart-Id header otherwise
                        .requestMatchers("/api/cart/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/telemetry/**").permitAll()
                        // Consulting quote form, open to visitors without an account
                        .requestMatchers(HttpMethod.POST, "/api/quote-requests").permitAll()
                        .requestMatchers("/api/openapi/**", "/api/swagger-ui/**", "/api/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Payment provider notifications, authenticated by their signature
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole(Roles.ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .cors(Customizer.withDefaults())
                // Double-submit cookie: the token is written in the XSRF-TOKEN cookie and must come back in the
                // X-XSRF-TOKEN header, which Angular's HttpClient does automatically for same-origin requests.
                // Defense in depth on top of bearer tokens and CORS; stateless, no session needed.
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/webhooks/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * Lazily resolves the Cognito JWKS so the application starts even if Cognito is unreachable.
     */
    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        return new SupplierJwtDecoder(() -> {
            var decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuerUri());
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
                    new CognitoAccessTokenValidator(properties.clientId())));
            return decoder;
        });
    }

    /**
     * Maps Cognito groups ({@code cognito:groups}) to Spring Security roles: group {@code ADMIN} → {@code ROLE_ADMIN}.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var groups = new JwtGrantedAuthoritiesConverter();
        groups.setAuthoritiesClaimName("cognito:groups");
        groups.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(groups);
        return converter;
    }

}
