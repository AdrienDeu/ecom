package org.ecom.cart;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.ecom.ApiTestSupport;
import org.ecom.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
class CartIT extends ApiTestSupport {

    @Test
    void guestCartIsIdentifiedByReturnedCartId() throws Exception {
        var productId = createProduct("12.50", 10);

        var result = mvc.perform(json(post("/api/cart/items"), """
                        {"productId": "%s", "quantity": 2}""".formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cart-Id", notNullValue()))
                .andExpect(jsonPath("$.total.amount").value(25.00))
                .andReturn();
        var cartId = result.getResponse().getHeader("X-Cart-Id");

        mvc.perform(json(post("/api/cart/items").header("X-Cart-Id", cartId), """
                        {"productId": "%s", "quantity": 1}""".formatted(productId)))
                .andExpect(jsonPath("$.lines[0].quantity").value(3));

        mvc.perform(json(put("/api/cart/items/{id}", productId).header("X-Cart-Id", cartId), """
                        {"quantity": 1}"""))
                .andExpect(jsonPath("$.total.amount").value(12.50));

        mvc.perform(delete("/api/cart/items/{id}", productId).header("X-Cart-Id", cartId))
                .andExpect(jsonPath("$.lines").isEmpty());

        // Another visitor cannot see the cart without its id
        mvc.perform(get("/api/cart")).andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void lineBecomesUnavailableWhenStockIsInsufficient() throws Exception {
        var productId = createProduct("5", 1);
        var customer = unique("bob");

        addToCart(customer, productId, 2);

        mvc.perform(get("/api/cart").with(customer(customer)))
                .andExpect(jsonPath("$.lines[0].available").value(false))
                .andExpect(jsonPath("$.checkoutAllowed").value(false))
                .andExpect(jsonPath("$.total.amount").value(0));
    }

    @Test
    void guestCartIsMergedIntoCustomerCartAtSignIn() throws Exception {
        var shirt = createProduct("20", 10);
        var socks = createProduct("5", 10);
        var customer = unique("carol");
        addToCart(customer, shirt, 1);

        var guest = mvc.perform(json(post("/api/cart/items"), """
                        {"productId": "%s", "quantity": 2}""".formatted(shirt)))
                .andReturn().getResponse().getHeader("X-Cart-Id");
        mvc.perform(json(post("/api/cart/items").header("X-Cart-Id", guest), """
                {"productId": "%s", "quantity": 3}""".formatted(socks)));

        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", guest).with(customer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.total.amount").value(75.00));

        mvc.perform(get("/api/cart").header("X-Cart-Id", guest)).andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    void rejectsUnknownProductsAndExcessiveQuantities() throws Exception {
        mvc.perform(json(post("/api/cart/items"), """
                        {"productId": "%s", "quantity": 1}""".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        var productId = createProduct("1", 500);
        var customer = unique("dave");
        addToCart(customer, productId, 99);
        mvc.perform(json(post("/api/cart/items").with(customer(customer)), """
                        {"productId": "%s", "quantity": 1}""".formatted(productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUANTITY_TOO_HIGH"));
    }

    @Test
    void mergeRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/cart/merge").header("X-Cart-Id", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

}
