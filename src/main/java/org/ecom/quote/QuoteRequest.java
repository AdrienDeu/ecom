package org.ecom.quote;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
class QuoteRequest {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String fullName;

    private String email;

    private String company;

    private String phone;

    private String topic;

    private String message;

    @Enumerated(EnumType.STRING)
    private QuoteStatus status;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected QuoteRequest() {
    }

    QuoteRequest(String fullName, String email, String company, String phone, String topic, String message) {
        this.fullName = fullName;
        this.email = email;
        this.company = company;
        this.phone = phone;
        this.topic = topic;
        this.message = message;
        this.status = QuoteStatus.NEW;
    }

    void changeStatus(QuoteStatus status) {
        this.status = status;
    }

    UUID getId() {
        return id;
    }

    String getFullName() {
        return fullName;
    }

    String getEmail() {
        return email;
    }

    String getCompany() {
        return company;
    }

    String getPhone() {
        return phone;
    }

    String getTopic() {
        return topic;
    }

    String getMessage() {
        return message;
    }

    QuoteStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

}
