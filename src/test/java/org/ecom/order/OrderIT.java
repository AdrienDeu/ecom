package org.ecom.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import io.micrometer.core.instrument.MeterRegistry;
import org.ecom.ApiTestSupport;
import org.ecom.IntegrationTest;
import org.ecom.inventory.InventoryService;
import org.ecom.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class OrderIT extends ApiTestSupport {

    @Autowired
    OrderService orders;

    @Autowired
    InventoryService inventory;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry registry;

    @Test
    void checkoutFreezesPricesReservesStockAndEmptiesCart() throws Exception {
        var productId = createProduct("19.99", 5);
        var customer = unique("alice");
        var address = createAddress(customer);
        addToCart(customer, productId, 2);
        var placed = registry.get("ecom.orders.placed").counter().count();

        var result = mvc.perform(json(post("/api/orders").with(customer(customer)), """
                        {"addressId": "%s"}""".formatted(address)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("ORD-[2-9A-HJKMNP-Z]{10}")))
                .andExpect(jsonPath("$.total.amount").value(39.98))
                .andExpect(jsonPath("$.shippingAddress.city").value("Paris"))
                .andExpect(jsonPath("$.lines[0].unitPrice.amount").value(19.99))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        String orderId = read(result, "$.id");

        assertThat(inventory.level(productId).reserved()).isEqualTo(2);
        assertThat(registry.get("ecom.orders.placed").counter().count()).isEqualTo(placed + 1);
        mvc.perform(get("/api/cart").with(customer(customer))).andExpect(jsonPath("$.lines").isEmpty());
        mvc.perform(get("/api/orders").with(customer(customer)))
                .andExpect(jsonPath("$.content[0].id").value(orderId));
    }

    @Test
    void failedCheckoutReservesNothing() throws Exception {
        var plenty = createProduct("1", 10);
        var scarce = createProduct("1", 1);
        var customer = unique("bob");
        var address = createAddress(customer);
        addToCart(customer, plenty, 3);
        addToCart(customer, scarce, 2);

        mvc.perform(json(post("/api/orders").with(customer(customer)), """
                        {"addressId": "%s"}""".formatted(address)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(inventory.level(plenty).reserved()).isZero();
        mvc.perform(get("/api/cart").with(customer(customer))).andExpect(jsonPath("$.lines.length()").value(2));
    }

    @Test
    void lastUnitsCannotBeOversoldUnderConcurrentCheckouts() throws Exception {
        var productId = createProduct("10", 5);
        var tasks = new ArrayList<Callable<OrderView>>();
        for (int i = 0; i < 12; i++) {
            var customer = unique("buyer");
            var address = createAddress(customer);
            addToCart(customer, productId, 1);
            tasks.add(() -> orders.checkout(customer, address));
        }

        int succeeded = 0;
        int rejected = 0;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var future : executor.invokeAll(tasks)) {
                try {
                    future.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                    rejected++;
                }
            }
        }

        assertThat(succeeded).isEqualTo(5);
        assertThat(rejected).isEqualTo(7);
        assertThat(inventory.level(productId).available()).isZero();
    }

    @Test
    void paidOrderConsumesStockAndCanBeShipped() throws Exception {
        var productId = createProduct("50", 4);
        var order = placeOrder(unique("carol"), productId, 3);

        orders.markPaymentPending(order);
        orders.markPaid(order);

        var stock = inventory.level(productId);
        assertThat(stock.onHand()).isEqualTo(1);
        assertThat(stock.reserved()).isZero();

        mvc.perform(post("/api/admin/orders/{id}/ship", order).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.expiresAt").isEmpty());
        mvc.perform(get("/api/admin/orders").param("status", "SHIPPED").with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void customerCancellationReleasesStock() throws Exception {
        var productId = createProduct("8", 2);
        var customer = unique("dave");
        var order = placeOrder(customer, productId, 2);

        mvc.perform(post("/api/orders/{id}/cancel", order).with(customer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(inventory.level(productId).available()).isEqualTo(2);
        mvc.perform(post("/api/orders/{id}/cancel", order).with(customer(customer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void expiredUnpaidOrdersAreCancelled() throws Exception {
        var productId = createProduct("3", 1);
        var order = placeOrder(unique("erin"), productId, 1);
        jdbc.update("update customer_order set expires_at = now() - interval '1 minute' where id = ?", order);
        var expired = registry.get("ecom.orders.expired").counter().count();

        assertThat(orders.expireStaleOrders(100)).isPositive();
        assertThat(registry.get("ecom.orders.expired").counter().count()).isGreaterThan(expired);

        assertThat(orders.get(order).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventory.level(productId).available()).isEqualTo(1);
    }

    @Test
    void customersCannotAccessEachOthersOrders() throws Exception {
        var productId = createProduct("1", 1);
        var order = placeOrder(unique("frank"), productId, 1);

        mvc.perform(get("/api/orders/{id}", order).with(customer("mallory"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/{id}/cancel", order).with(customer("mallory"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/{id}", order)).andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutWithEmptyCartOrForeignAddressFails() throws Exception {
        var customer = unique("gina");
        var foreignAddress = createAddress(unique("henry"));

        mvc.perform(json(post("/api/orders").with(customer(customer)), """
                        {"addressId": "%s"}""".formatted(foreignAddress)))
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));

        addToCart(customer, createProduct("1", 1), 1);
        mvc.perform(json(post("/api/orders").with(customer(customer)), """
                        {"addressId": "%s"}""".formatted(foreignAddress)))
                .andExpect(status().isNotFound());
    }

    private UUID placeOrder(String customer, UUID productId, int quantity) throws Exception {
        var address = createAddress(customer);
        addToCart(customer, productId, quantity);
        var result = mvc.perform(json(post("/api/orders").with(customer(customer)), """
                        {"addressId": "%s"}""".formatted(address)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result, "$.id"));
    }

}
