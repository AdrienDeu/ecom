package org.ecom.shared.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * The OpenAPI document drives the generated Angular client, so operation ids must be unique and readable:
 * {@code AdminCatalogController#products} becomes {@code adminCatalogProducts}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearer";
    private static final String REF_PREFIX = "#/components/schemas/";

    @Bean
    OpenAPI ecomOpenApi() {
        return new OpenAPI()
                .info(new Info().title("ecom API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * Response properties are always present unless declared {@code @Schema(nullable = true)}, so the generated
     * TypeScript models do not mark everything optional. Request schemas keep the bean validation semantics.
     */
    @Bean
    @SuppressWarnings("rawtypes") // swagger-core models use raw Schema maps
    OpenApiCustomizer requiredResponseProperties() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            // OpenAPI 3.0 cannot mark a $ref as nullable: swagger-core moves `nullable` from the property onto the
            // referenced schema. Undo that and treat such references as optional properties instead.
            var nullableObjects = new HashSet<String>();
            schemas.forEach((name, schema) -> {
                if (Boolean.TRUE.equals(schema.getNullable()) && schema.getProperties() != null) {
                    schema.setNullable(null);
                    nullableObjects.add(REF_PREFIX + name);
                }
            });
            schemas.forEach((name, schema) -> requireNonNullableProperties(name, schema, nullableObjects));
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void requireNonNullableProperties(String name, Schema schema, Set<String> nullableObjects) {
        Map<String, Schema> properties = schema.getProperties();
        if (name.endsWith("Request") || name.equals("PostalAddress") || name.equals("Pageable") || properties == null) {
            return;
        }
        properties.forEach((property, propertySchema) -> {
            var nullable = Boolean.TRUE.equals(propertySchema.getNullable()) || nullableObjects.contains(propertySchema.get$ref());
            if (!nullable) {
                schema.addRequiredItem(property);
            }
        });
    }

    @Bean
    OperationCustomizer operationIds() {
        return (operation, handlerMethod) -> {
            var controller = handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
            operation.setOperationId(StringUtils.uncapitalize(controller) + StringUtils.capitalize(handlerMethod.getMethod().getName()));
            return operation;
        };
    }

}
