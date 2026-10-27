package org.ecom.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.ecom.notification.Email;
import org.ecom.notification.NotificationProperties;
import org.ecom.notification.NotificationProperties.Transport;
import org.junit.jupiter.api.Test;

class QuoteNotifierTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<Email> sent = new ArrayList<>();

    @Test
    void emailsTheTeamWithEverythingNeededToAnswer() {
        notifier(List.of("devis@shop.example")).onQuoteRequested(quote("Audit de sécurité", "Acme", null));

        assertThat(sent).singleElement().satisfies(email -> {
            assertThat(email.to()).containsExactly("devis@shop.example");
            assertThat(email.subject()).isEqualTo("Nouvelle demande de devis : Audit de sécurité");
            assertThat(email.replyTo()).isEqualTo("jeanne@example.test");
            assertThat(email.body())
                    .contains("Nom : Jeanne Martin", "Entreprise : Acme", "E-mail : jeanne@example.test",
                            "Sujet : Audit de sécurité", "Auditer 40 postes")
                    .doesNotContain("Téléphone");
        });
        assertThat(count("sent")).isEqualTo(1);
    }

    @Test
    void keepsTheSubjectShortWithoutATopic() {
        notifier(List.of("devis@shop.example")).onQuoteRequested(quote(null, null, "+33 1 23 45 67 89"));

        assertThat(sent).singleElement().satisfies(email -> {
            assertThat(email.subject()).isEqualTo("Nouvelle demande de devis");
            assertThat(email.body()).contains("Téléphone : +33 1 23 45 67 89").doesNotContain("Entreprise", "Sujet");
        });
    }

    @Test
    void abbreviatesLongTopics() {
        notifier(List.of("devis@shop.example")).onQuoteRequested(quote("x".repeat(200), null, null));

        assertThat(sent.getFirst().subject()).hasSize("Nouvelle demande de devis : ".length() + 80).endsWith("…");
    }

    @Test
    void sendsNothingWithoutRecipients() {
        notifier(List.of()).onQuoteRequested(quote("Audit", null, null));

        assertThat(sent).isEmpty();
    }

    @Test
    void aFailedEmailIsCountedNotThrown() {
        var notifier = new QuoteNotifier(email -> {
            throw new IllegalStateException("SES unavailable");
        }, properties(List.of("devis@shop.example")), registry);

        notifier.onQuoteRequested(quote("Audit", null, null));

        assertThat(count("failed")).isEqualTo(1);
    }

    private QuoteNotifier notifier(List<String> recipients) {
        return new QuoteNotifier(sent::add, properties(recipients), registry);
    }

    private static NotificationProperties properties(List<String> recipients) {
        return new NotificationProperties(Transport.NONE, "no-reply@shop.example", recipients);
    }

    private static QuoteRequested quote(String topic, String company, String phone) {
        return new QuoteRequested(UUID.randomUUID(), "Jeanne Martin", "jeanne@example.test", company, phone, topic,
                "Auditer 40 postes avant la fin du trimestre.");
    }

    private double count(String outcome) {
        return registry.get("ecom.notifications").tag("outcome", outcome).counter().count();
    }

}
