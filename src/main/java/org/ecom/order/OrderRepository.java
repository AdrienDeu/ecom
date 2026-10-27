package org.ecom.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByIdAndCustomerId(UUID id, String customerId);

    Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(String customerId, UUID idempotencyKey);

    Page<CustomerOrder> findByCustomerId(String customerId, Pageable pageable);

    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Locks a batch of expired unpaid orders. {@code SKIP LOCKED} lets several application instances run the
     * expiry job concurrently without processing the same order twice.
     */
    @Query(value = """
            select id from customer_order
            where status in ('CREATED', 'PAYMENT_PENDING') and expires_at < :now
            order by expires_at
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<UUID> lockExpired(Instant now, int limit);

}
