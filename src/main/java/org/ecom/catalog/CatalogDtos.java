package org.ecom.catalog;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import org.ecom.inventory.InventoryService.StockLevel;
import org.ecom.shared.domain.Money;

public final class CatalogDtos {

    private static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private CatalogDtos() {
    }

    public record CategoryView(UUID id, String slug, String name) {

        static CategoryView of(Category category) {
            return category == null ? null : new CategoryView(category.getId(), category.getSlug(), category.getName());
        }

    }

    public record ProductView(UUID id, String sku, String slug, String name, ProductKind kind,
                              @Schema(nullable = true) String description, Money price,
                              @Schema(nullable = true) String imageUrl, @Schema(nullable = true) CategoryView category,
                              boolean inStock) {

        static ProductView of(Product product, int available) {
            return new ProductView(product.getId(), product.getSku(), product.getSlug(), product.getName(),
                    product.getKind(), product.getDescription(), product.getPrice(), product.getImageUrl(),
                    CategoryView.of(product.getCategory()), available > 0);
        }

    }

    public record AdminProductView(UUID id, String sku, String slug, String name, ProductKind kind,
                                   @Schema(nullable = true) String description, Money price,
                                   @Schema(nullable = true) String imageUrl, @Schema(nullable = true) CategoryView category,
                                   boolean active, StockLevel stock) {

        static AdminProductView of(Product product, StockLevel stock) {
            return new AdminProductView(product.getId(), product.getSku(), product.getSlug(), product.getName(),
                    product.getKind(), product.getDescription(), product.getPrice(), product.getImageUrl(),
                    CategoryView.of(product.getCategory()), product.isActive(), stock);
        }

    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 200) String name,
                                        @Size(max = 100) @Pattern(regexp = SLUG) String slug) {
    }

    /**
     * {@code kind} defaults to {@link ProductKind#PRODUCT} and {@code initialStock} to 0; a service ignores
     * {@code initialStock}.
     */
    public record CreateProductRequest(@NotBlank @Size(max = 64) String sku,
                                       @NotBlank @Size(max = 200) String name,
                                       @Schema(nullable = true) ProductKind kind,
                                       @Size(max = 200) @Pattern(regexp = SLUG) String slug,
                                       @Size(max = 10_000) String description,
                                       @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal price,
                                       @Size(max = 1000) String imageUrl,
                                       UUID categoryId,
                                       @Schema(nullable = true) @Min(0) Integer initialStock) {
    }

    public record UpdateProductRequest(@NotBlank @Size(max = 200) String name,
                                       @Size(max = 10_000) String description,
                                       @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal price,
                                       @Size(max = 1000) String imageUrl,
                                       UUID categoryId,
                                       boolean active) {
    }

    public record UpdateStockRequest(@Min(0) int onHand) {
    }

}
