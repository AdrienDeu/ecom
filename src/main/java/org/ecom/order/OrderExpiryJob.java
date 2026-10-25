package org.ecom.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "ecom.order.expiry-job.enabled", matchIfMissing = true)
class OrderExpiryJob {

    private static final int BATCH_SIZE = 100;

    private final OrderService orders;

    OrderExpiryJob(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(fixedDelayString = "${ecom.order.expiry-job.interval:PT1M}", initialDelayString = "${ecom.order.expiry-job.interval:PT1M}")
    void expireStaleOrders() {
        // Each batch commits on its own so that a large backlog does not hold locks for long
        int expired;
        do {
            expired = orders.expireStaleOrders(BATCH_SIZE);
        } while (expired == BATCH_SIZE);
    }

}
