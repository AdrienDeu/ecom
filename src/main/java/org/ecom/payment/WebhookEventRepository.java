package org.ecom.payment;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    boolean existsByProviderAndEventId(String provider, String eventId);

}
