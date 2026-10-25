package org.ecom.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.ecom.ApiTestSupport;
import org.ecom.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@IntegrationTest
class CatalogIT extends ApiTestSupport {

    @Test
    void adminCreatesProductThatIsPubliclyVisible() throws Exception {
        var category = unique("shoes");
        var created = mvc.perform(json(post("/api/admin/categories").with(admin()), """
                        {"name": "Chaussures d'Été", "slug": "%s"}
                        """.formatted(category)))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = read(created, "$.id");
        var sku = unique("SKU");

        mvc.perform(json(post("/api/admin/products").with(admin()), """
                        {"sku": "%s", "name": "Basket Été %s", "price": 59.90, "categoryId": "%s", "initialStock": 3}
                        """.formatted(sku, sku, categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(("basket-ete-" + sku).toLowerCase()))
                .andExpect(jsonPath("$.stock.available").value(3));

        mvc.perform(get("/api/catalog/products").param("category", category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value(sku))
                .andExpect(jsonPath("$.content[0].price.amount").value(59.90))
                .andExpect(jsonPath("$.content[0].price.currency").value("EUR"))
                .andExpect(jsonPath("$.content[0].inStock").value(true));

        mvc.perform(get("/api/catalog/products/{slug}", ("basket-ete-" + sku).toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.slug").value(category));
    }

    @Test
    void searchMatchesNameCaseInsensitivelyAndTreatsWildcardsLiterally() throws Exception {
        var marker = unique("Zebra");
        mvc.perform(json(post("/api/admin/products").with(admin()), """
                        {"sku": "%s", "name": "Pull %s", "price": 30, "initialStock": 1}
                        """.formatted(marker, marker)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/catalog/products").param("q", marker.toUpperCase()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/catalog/products").param("q", "%"))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void deactivatedProductDisappearsFromCatalog() throws Exception {
        var id = createProduct("10", 1);
        var slug = mvc.perform(get("/api/admin/products/{id}", id).with(admin())).andReturn();
        String productSlug = read(slug, "$.slug");

        mvc.perform(delete("/api/admin/products/{id}", id).with(admin())).andExpect(status().isNoContent());

        mvc.perform(get("/api/catalog/products/{slug}", productSlug)).andExpect(status().isNotFound());
    }

    @Test
    void backOfficeListsInactiveProductsWithStock() throws Exception {
        var id = createProduct("10", 4);
        mvc.perform(delete("/api/admin/products/{id}", id).with(admin()));
        var created = mvc.perform(get("/api/admin/products/{id}", id).with(admin())).andReturn();
        String name = read(created, "$.name");

        mvc.perform(get("/api/admin/products").param("q", name).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].active").value(false))
                .andExpect(jsonPath("$.content[0].stock.onHand").value(4));
    }

    @Test
    void stockCannotGoBelowReservedQuantity() throws Exception {
        var productId = createProduct("10", 5);
        var customer = unique("alice");
        var address = createAddress(customer);
        addToCart(customer, productId, 3);
        mvc.perform(json(post("/api/orders").with(customer(customer)), """
                {"addressId": "%s"}""".formatted(address))).andExpect(status().isCreated());

        mvc.perform(json(put("/api/admin/products/{id}/stock", productId).with(admin()), """
                        {"onHand": 2}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_BELOW_RESERVED"));
        mvc.perform(json(put("/api/admin/products/{id}/stock", productId).with(admin()), """
                        {"onHand": 10}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock.available").value(7));
    }

    @Test
    void duplicateSkuIsRejected() throws Exception {
        var sku = unique("SKU");
        var body = """
                {"sku": "%s", "name": "%s", "price": 1, "initialStock": 0}""".formatted(sku, sku);
        mvc.perform(json(post("/api/admin/products").with(admin()), body)).andExpect(status().isCreated());

        mvc.perform(json(post("/api/admin/products").with(admin()), body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(json(post("/api/admin/products").with(admin()), """
                        {"sku": "", "name": "x", "price": -1, "initialStock": 0}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/products").param("sort", "password"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Cookie-less bearer requests are exempted by Spring Security; anonymous ones (guest carts) are not.
     */
    @Test
    void anonymousStateChangingRequestWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": "%s", "quantity": 1}""".formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void backOfficeRequiresAdminRole() throws Exception {
        var body = """
                {"name": "Hacked"}""";
        mvc.perform(json(post("/api/admin/categories"), body)).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/admin/categories").with(customer("mallory")), body)).andExpect(status().isForbidden());
    }

}
