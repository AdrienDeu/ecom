package org.ecom.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ecom")
public record EcomProperties(@DefaultValue Cors cors) {

    /**
     * @param allowedOrigins origins allowed to call {@code /api/**} (the SPA in local dev).
     *                       Empty in staging/production where SPA and API share the same domain.
     */
    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

}
