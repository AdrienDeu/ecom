package org.ecom.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no transport is configured (tests, environments without email). */
class DisabledEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(DisabledEmailSender.class);

    @Override
    public void send(Email email) {
        log.debug("Email notifications are disabled, not sending \"{}\"", email.subject());
    }

}
