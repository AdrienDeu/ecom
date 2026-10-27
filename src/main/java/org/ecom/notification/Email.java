package org.ecom.notification;

import java.util.List;

/**
 * A plain-text email. {@code replyTo}, when set, lets the recipient answer the person the email is about.
 */
public record Email(List<String> to, String subject, String body, String replyTo) {

    public Email {
        to = List.copyOf(to);
        // Subjects may carry visitor input: a line break would let it add headers
        subject = subject.replaceAll("\\R", " ").strip();
    }

}
