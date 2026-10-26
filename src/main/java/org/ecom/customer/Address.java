package org.ecom.customer;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
class Address {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String customerId;

    @Embedded
    private PostalAddress details;

    @CreationTimestamp
    private Instant createdAt;

    protected Address() {
    }

    Address(String customerId, PostalAddress details) {
        this.customerId = customerId;
        this.details = details;
    }

    UUID getId() {
        return id;
    }

    String getCustomerId() {
        return customerId;
    }

    PostalAddress getDetails() {
        return details;
    }

    void setDetails(PostalAddress details) {
        this.details = details;
    }

}
