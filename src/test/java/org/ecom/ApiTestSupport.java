package org.ecom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Helpers shared by API integration tests. Every test creates its own data with unique identifiers, so tests do
 * not depend on each other although they share the same database.
 */
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    /** A signed-in customer, sending the CSRF token like the SPA does. */
    protected static RequestPostProcessor customer(String subject) {
        return withCsrf(jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    protected static RequestPostProcessor admin() {
        return withCsrf(jwt().jwt(token -> token.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static RequestPostProcessor withCsrf(RequestPostProcessor authentication) {
        return request -> csrf().postProcessRequest(authentication.postProcessRequest(request));
    }

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** JSON request carrying the CSRF token, as anonymous visitors of the SPA send them too. */
    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    protected UUID createProduct(String price, int stock) throws Exception {
        var sku = unique("SKU");
        var result = mvc.perform(json(post("/api/admin/products").with(admin()), """
                        {"sku": "%s", "name": "Product %s", "price": %s, "initialStock": %d}
                        """.formatted(sku, sku, price, stock)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result, "$.id"));
    }

    protected UUID createAddress(String customer) throws Exception {
        var result = mvc.perform(json(post("/api/me/addresses").with(customer(customer)), """
                        {"fullName": "Jeanne Martin", "line1": "1 rue de la Paix", "postalCode": "75002",
                         "city": "Paris", "country": "FR", "phone": "+33 1 23 45 67 89"}
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result, "$.id"));
    }

    protected void addToCart(String customer, UUID productId, int quantity) throws Exception {
        mvc.perform(json(post("/api/cart/items").with(customer(customer)), """
                        {"productId": "%s", "quantity": %d}
                        """.formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

}
