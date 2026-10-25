package org.ecom.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ecom.order.OrderStatus.CANCELLED;
import static org.ecom.order.OrderStatus.CREATED;
import static org.ecom.order.OrderStatus.DELIVERED;
import static org.ecom.order.OrderStatus.PAID;
import static org.ecom.order.OrderStatus.PAYMENT_PENDING;
import static org.ecom.order.OrderStatus.REFUNDED;
import static org.ecom.order.OrderStatus.SHIPPED;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "CREATED, PAYMENT_PENDING", "CREATED, CANCELLED",
            "PAYMENT_PENDING, PAID", "PAYMENT_PENDING, CREATED", "PAYMENT_PENDING, CANCELLED",
            "PAID, SHIPPED", "PAID, REFUNDED", "SHIPPED, DELIVERED", "SHIPPED, REFUNDED", "DELIVERED, REFUNDED"
    })
    void allowedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED, PAID", "CREATED, SHIPPED", "PAID, CANCELLED", "SHIPPED, CANCELLED",
            "DELIVERED, SHIPPED", "CANCELLED, CREATED", "REFUNDED, PAID"
    })
    void forbiddenTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void terminalStatusesAllowNothing() {
        for (var target : OrderStatus.values()) {
            assertThat(CANCELLED.canTransitionTo(target)).isFalse();
            assertThat(REFUNDED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void onlyUnpaidStatusesHoldReservations() {
        assertThat(OrderStatus.UNPAID).containsExactlyInAnyOrder(CREATED, PAYMENT_PENDING);
        assertThat(OrderStatus.UNPAID).doesNotContain(PAID, SHIPPED, DELIVERED);
    }

}
