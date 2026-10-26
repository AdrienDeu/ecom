package org.ecom.telemetry;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The SPA routes on the client side, so the server never sees page changes: the SPA reports them here. Anonymous,
 * like browsing the catalog; nothing about the visitor is recorded.
 */
@RestController
@RequestMapping("/api/telemetry")
class TelemetryController {

    private final Map<PageType, Counter> pageViews = new EnumMap<>(PageType.class);

    TelemetryController(MeterRegistry registry) {
        for (var page : PageType.values()) {
            pageViews.put(page, Counter.builder("ecom.page.views")
                    .description("Pages displayed by the SPA")
                    .tag("page", page.name().toLowerCase(Locale.ROOT))
                    .register(registry));
        }
    }

    @PostMapping("/page-views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pageView(@Valid @RequestBody PageViewRequest request) {
        pageViews.get(request.page()).increment();
    }

    record PageViewRequest(@NotNull PageType page) {
    }

}
