package org.ecom.notification;

import java.nio.charset.StandardCharsets;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Through the Amazon SES API, authenticated by the ECS task role: no SMTP password or access key to store.
 */
class SesEmailSender implements EmailSender, AutoCloseable {

    private final SesV2Client ses;
    private final String from;

    SesEmailSender(SesV2Client ses, String from) {
        this.ses = ses;
        this.from = from;
    }

    @Override
    public void send(Email email) {
        var request = SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(email.to()).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(text(email.subject()))
                        .body(Body.builder().text(text(email.body())).build())
                        .build()).build());
        if (email.replyTo() != null) {
            request.replyToAddresses(email.replyTo());
        }
        ses.sendEmail(request.build());
    }

    /** Called by Spring on shutdown. */
    @Override
    public void close() {
        ses.close();
    }

    private static Content text(String value) {
        return Content.builder().data(value).charset(StandardCharsets.UTF_8.name()).build();
    }

}
