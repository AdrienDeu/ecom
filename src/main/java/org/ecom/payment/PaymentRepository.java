package org.ecom.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndCustomerId(UUID id, String customerId);

    List<Payment> findByOrderIdOrderByCreatedAt(UUID orderId);

    /** The payment of the order that is neither failed nor cancelled; there is at most one (unique index). */
    @Query("select p from Payment p where p.orderId = :orderId and p.status not in ('FAILED', 'CANCELLED')")
    Optional<Payment> findLive(UUID orderId);

    Optional<Payment> findByProviderAndProviderReference(String provider, String providerReference);

    Optional<Payment> findByProviderAndCaptureId(String provider, String captureId);

    /**
     * Claims a batch of open payments that have not moved for a while. {@code SKIP LOCKED} lets several instances
     * run the reconciliation job concurrently; the caller marks the claimed payments as checked.
     */
    @Query(value = """
            select id from payment
            where status in ('INITIATED', 'PENDING_APPROVAL') and updated_at < :before
              and (checked_at is null or checked_at < :before)
            order by updated_at
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<UUID> lockStale(Instant before, int limit);

}
