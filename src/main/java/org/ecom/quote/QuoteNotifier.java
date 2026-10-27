package org.ecom.quote;

import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.ecom.notification.Email;
import org.ecom.notification.EmailSender;
import org.ecom.notification.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails the shop about each new quote request, after it is saved. Replying to the email answers the customer.
 */
@Component
class QuoteNotifier {

    private static final Logger log = LoggerFactory.getLogger(QuoteNotifier.class);

    private final EmailSender emails;
    private final List<String> recipients;
    private final Counter sent;
    private final Counter failed;

    QuoteNotifier(EmailSender emails, NotificationProperties properties, MeterRegistry registry) {
        this.emails = emails;
        this.recipients = properties.quoteRecipients();
        this.sent = counter(registry, "sent");
        this.failed = counter(registry, "failed");
    }

    @Async
    @TransactionalEventListener
    void onQuoteRequested(QuoteRequested quote) {
        if (recipients.isEmpty()) {
            return;
        }
        try {
            emails.send(email(quote));
            sent.increment();
        } catch (RuntimeException e) {
            failed.increment();
            // The request is saved and listed in the back-office: a lost email only delays the answer
            log.warn("Could not send the notification for quote request {}", quote.id(), e);
        }
    }

    Email email(QuoteRequested quote) {
        var subject = "Nouvelle demande de devis" + (quote.topic() != null ? " : " + abbreviate(quote.topic()) : "");
        var body = new StringBuilder()
                .append("Nouvelle demande de devis reçue sur le site.\n\n")
                .append("Nom : ").append(quote.fullName()).append('\n');
        appendIfPresent(body, "Entreprise", quote.company());
        body.append("E-mail : ").append(quote.email()).append('\n');
        appendIfPresent(body, "Téléphone", quote.phone());
        appendIfPresent(body, "Sujet", quote.topic());
        body.append("\nBesoin :\n").append(quote.message()).append("\n\n")
                .append("Répondez à cet e-mail pour écrire directement au demandeur. ")
                .append("La demande est aussi dans le back-office, onglet Devis.\n");
        return new Email(recipients, subject, body.toString(), quote.email());
    }

    private static void appendIfPresent(StringBuilder body, String label, String value) {
        if (value != null) {
            body.append(label).append(" : ").append(value).append('\n');
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 79) + "…";
    }

    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("ecom.notifications").description("Emails sent to the shop")
                .tag("type", "quote_request").tag("outcome", outcome).register(registry);
    }

}
