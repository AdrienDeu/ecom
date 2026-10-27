package org.ecom.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

class SesEmailSenderTest {

    private final SesV2Client ses = mock(SesV2Client.class);

    @Test
    void sendsAPlainTextEmailThroughSes() {
        new SesEmailSender(ses, "no-reply@shop.example")
                .send(new Email(List.of("team@shop.example"), "Nouvelle demande", "Bonjour", "client@example.test"));

        var request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(request.capture());
        var sent = request.getValue();
        assertThat(sent.fromEmailAddress()).isEqualTo("no-reply@shop.example");
        assertThat(sent.destination().toAddresses()).containsExactly("team@shop.example");
        assertThat(sent.replyToAddresses()).containsExactly("client@example.test");
        assertThat(sent.content().simple().subject().data()).isEqualTo("Nouvelle demande");
        assertThat(sent.content().simple().body().text().data()).isEqualTo("Bonjour");
        assertThat(sent.content().simple().body().text().charset()).isEqualTo("UTF-8");
    }

    @Test
    void omitsReplyToWhenThereIsNone() {
        new SesEmailSender(ses, "no-reply@shop.example").send(new Email(List.of("team@shop.example"), "Objet", "Texte", null));

        var request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(request.capture());
        assertThat(request.getValue().hasReplyToAddresses()).isFalse();
    }

    @Test
    void closesTheClient() {
        new SesEmailSender(ses, "no-reply@shop.example").close();

        verify(ses).close();
        verify(ses, org.mockito.Mockito.never()).sendEmail(any(SendEmailRequest.class));
    }

}
