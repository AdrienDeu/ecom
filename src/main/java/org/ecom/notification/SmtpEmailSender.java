package org.ecom.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Through an SMTP server: Mailpit in local development, which only displays the emails. */
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(Email email) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.to().toArray(String[]::new));
        message.setSubject(email.subject());
        message.setText(email.body());
        if (email.replyTo() != null) {
            message.setReplyTo(email.replyTo());
        }
        mailSender.send(message);
    }

}
