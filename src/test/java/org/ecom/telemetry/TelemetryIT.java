package org.ecom.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import org.ecom.ApiTestSupport;
import org.ecom.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TelemetryIT extends ApiTestSupport {

    @Autowired
    MeterRegistry registry;

    @Test
    void anonymousPageViewsAreCountedByPageType() throws Exception {
        var before = productViews();

        mvc.perform(json(post("/api/telemetry/page-views"), """
                        {"page": "PRODUCT"}"""))
                .andExpect(status().isNoContent());

        assertThat(productViews()).isEqualTo(before + 1);
    }

    @Test
    void unknownPagesAreRejectedToKeepCardinalityBounded() throws Exception {
        mvc.perform(json(post("/api/telemetry/page-views"), """
                        {"page": "/products/some-slug?utm=1"}"""))
                .andExpect(status().isBadRequest());
        assertThat(registry.find("ecom.page.views").counters()).hasSize(PageType.values().length);
    }

    private double productViews() {
        return registry.get("ecom.page.views").tag("page", "product").counter().count();
    }

}
