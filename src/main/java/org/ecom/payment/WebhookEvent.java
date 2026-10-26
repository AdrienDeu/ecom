package org.ecom.payment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A processed webhook, so that redeliveries of the same event are ignored. */
@Entity
class WebhookEvent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String provider;

    private String eventId;

    private String eventType;

    @CreationTimestamp
    private Instant receivedAt;

    protected WebhookEvent() {
    }

    WebhookEvent(String provider, String eventId, String eventType) {
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
    }

}
