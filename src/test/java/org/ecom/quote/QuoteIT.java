package org.ecom.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import org.awaitility.Awaitility;
import org.ecom.ApiTestSupport;
import org.ecom.IntegrationTest;
import org.ecom.RecordingEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class QuoteIT extends ApiTestSupport {

    @Autowired
    MeterRegistry registry;

    @Autowired
    RecordingEmailSender emails;

    @Test
    void visitorAsksForAQuoteThatTheAdminFollowsUp() throws Exception {
        var company = unique("Acme");
        var received = registry.get("ecom.quote.requests").tag("outcome", "received").counter().count();

        mvc.perform(json(post("/api/quote-requests"), """
                        {"fullName": "Jeanne Martin", "email": "jeanne@example.com", "company": "%s",
                         "topic": "Audit de sécurité", "message": "Nous souhaitons auditer notre SI de 40 postes."}
                        """.formatted(company)))
                .andExpect(status().isAccepted());

        assertThat(registry.get("ecom.quote.requests").tag("outcome", "received").counter().count())
                .isEqualTo(received + 1);
        // The team is emailed after the request is saved, on another thread
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(emails.sent())
                .anySatisfy(email -> {
                    assertThat(email.to()).containsExactly("devis@example.test");
                    assertThat(email.replyTo()).isEqualTo("jeanne@example.com");
                    assertThat(email.body()).contains(company);
                }));
        var list = mvc.perform(get("/api/admin/quote-requests").param("status", "NEW").with(admin()))
                .andExpect(status().isOk())
                .andReturn();
        String id = read(list, "$.content[?(@.company == '%s')].id".formatted(company)).toString()
                .replaceAll("[\\[\\]\"]", "");
        mvc.perform(json(post("/api/admin/quote-requests/{id}/status", id).with(admin()), """
                        {"status": "IN_PROGRESS"}"""))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.email").value("jeanne@example.com"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(json(post("/api/quote-requests"), """
                        {"fullName": "", "email": "not-an-email", "message": "Trop court"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void honeypotRequestsAreAcceptedButNotKept() throws Exception {
        var company = unique("Bot");
        var spam = registry.get("ecom.quote.requests").tag("outcome", "spam").counter().count();

        mvc.perform(json(post("/api/quote-requests"), """
                        {"fullName": "Bot", "email": "bot@example.com", "company": "%s",
                         "message": "Buy cheap watches, best prices online!", "website": "https://spam.example"}
                        """.formatted(company)))
                .andExpect(status().isAccepted());

        assertThat(registry.get("ecom.quote.requests").tag("outcome", "spam").counter().count()).isEqualTo(spam + 1);
        assertThat(emails.sent()).noneSatisfy(email -> assertThat(email.body()).contains(company));
        mvc.perform(get("/api/admin/quote-requests").param("size", "1000").with(admin()))
                .andExpect(jsonPath("$.content[?(@.company == '%s')]".formatted(company)).isEmpty());
    }

    @Test
    void onlyAdminsSeeQuoteRequests() throws Exception {
        mvc.perform(get("/api/admin/quote-requests").with(customer("eve"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/quote-requests")).andExpect(status().isUnauthorized());
    }

}
