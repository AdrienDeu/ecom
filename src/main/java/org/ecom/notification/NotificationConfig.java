package org.ecom.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Notifications are sent after the transaction that triggered them, on another thread: a mail outage never fails
 * nor slows down the request.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(NotificationProperties.class)
class NotificationConfig {

    @Bean
    EmailSender emailSender(NotificationProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        return switch (properties.transport()) {
            case NONE -> new DisabledEmailSender();
            case SMTP -> new SmtpEmailSender(mailSender.getObject(), properties.from());
            // Region and credentials from the environment: the ECS task role in AWS
            case SES -> new SesEmailSender(SesV2Client.create(), properties.from());
        };
    }

}
