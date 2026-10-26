package org.ecom.shared.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration(proxyBeanMethods = false)
class ObservabilityConfig {

    /**
     * Health checks and Prometheus scrapes would otherwise produce a trace every few seconds. On the management port
     * the Spring Security observations are not wrapped in an HTTP one: without a parent, they are such scrapes.
     */
    @Bean
    ObservationPredicate ignoreInfrastructureRequests() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext server) {
                return !server.getCarrier().getRequestURI().startsWith("/actuator");
            }
            return !(name.startsWith("spring.security.") && context.getParentObservation() == null);
        };
    }

}
