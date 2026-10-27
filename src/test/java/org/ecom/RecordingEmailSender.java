package org.ecom;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.ecom.notification.Email;
import org.ecom.notification.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Captures the emails the application sends during integration tests instead of sending them.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingEmailSender implements EmailSender {

    private final List<Email> sent = new CopyOnWriteArrayList<>();

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return this;
    }

    @Override
    public void send(Email email) {
        sent.add(email);
    }

    public List<Email> sent() {
        return List.copyOf(sent);
    }

}
