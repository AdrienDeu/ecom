package org.ecom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, PaypalStubConfiguration.class})
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class EcomApplicationIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Test
    void readinessProbeIsUp() throws Exception {
        var response = get(managementPort, "/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void exposesPrometheusMetrics() throws Exception {
        var response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void actuatorIsNotExposedOnApplicationPort() throws Exception {
        assertThat(get(port, "/actuator/health").statusCode()).isIn(401, 404);
        assertThat(get(port, "/actuator/prometheus").statusCode()).isIn(401, 404);
    }

    @Test
    void servesOpenApiDocument() throws Exception {
        var response = get(port, "/api/openapi");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"openapi\"");
    }

    /** The Angular client is generated from this document: ids and required properties are part of the contract. */
    @Test
    void openApiDocumentDescribesTheContractOfTheGeneratedClient() throws Exception {
        var document = JsonPath.parse(get(port, "/api/openapi").body());

        List<String> operationIds = document.read("$.paths.*.*.operationId");
        assertThat(operationIds).contains("catalogProducts", "adminCatalogProducts", "orderCheckout",
                "paymentMethods", "paymentStart", "paymentCapture", "adminPaymentList", "adminPaymentRefund").doesNotHaveDuplicates();
        List<String> required = document.read("$.components.schemas.ProductView.required");
        assertThat(required).contains("id", "name", "price", "inStock").doesNotContain("category", "description");
        assertThat(document.<Boolean>read("$.components.schemas.ProductView.properties.description.nullable")).isTrue();
        // Webhooks are for PayPal, not for the generated client
        Map<String, Object> paths = document.read("$.paths");
        assertThat(paths).doesNotContainKey("/api/webhooks/{provider}");
        Map<String, Object> categoryView = document.read("$.components.schemas.CategoryView");
        assertThat(categoryView).doesNotContainKey("nullable");
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
