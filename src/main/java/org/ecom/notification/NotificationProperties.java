package org.ecom.notification;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param transport       {@code none} (default: nothing is sent), {@code smtp} (local Mailpit, {@code spring.mail.*})
 *                        or {@code ses} (Amazon SES API, with the credentials and region of the ECS task)
 * @param from            sender address, in a domain verified in SES
 * @param quoteRecipients who is told about new quote requests; none means no email
 */
@ConfigurationProperties("ecom.notifications")
public record NotificationProperties(@DefaultValue("none") Transport transport,
                                     @DefaultValue("no-reply@localhost") String from,
                                     @DefaultValue List<String> quoteRecipients) {

    public enum Transport {
        NONE, SMTP, SES
    }

}
