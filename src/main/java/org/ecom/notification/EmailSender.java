package org.ecom.notification;

/**
 * Sends emails to the shop's own team. Implementations throw on failure; callers decide whether it matters.
 */
public interface EmailSender {

    void send(Email email);

}
