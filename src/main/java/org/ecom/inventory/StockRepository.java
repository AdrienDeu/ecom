package org.ecom.inventory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Reservations use single conditional UPDATE statements: the row lock taken by PostgreSQL makes concurrent
 * checkouts of the last units safe without application-level locking. Untracked stock always matches and its
 * quantities never change.
 */
interface StockRepository extends JpaRepository<Stock, UUID> {

    List<Stock> findByProductIdIn(Collection<UUID> productIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Stock s set s.reserved = s.reserved + case when s.tracked then :quantity else 0 end,
                               s.version = s.version + 1
            where s.productId = :productId and (s.tracked = false or s.onHand - s.reserved >= :quantity)""")
    int reserve(UUID productId, int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Stock s set s.reserved = s.reserved - case when s.tracked then :quantity else 0 end,
                               s.version = s.version + 1
            where s.productId = :productId and (s.tracked = false or s.reserved >= :quantity)""")
    int release(UUID productId, int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Stock s set s.onHand = s.onHand - case when s.tracked then :quantity else 0 end,
                               s.reserved = s.reserved - case when s.tracked then :quantity else 0 end,
                               s.version = s.version + 1
            where s.productId = :productId and (s.tracked = false or s.reserved >= :quantity)""")
    int commit(UUID productId, int quantity);

}
