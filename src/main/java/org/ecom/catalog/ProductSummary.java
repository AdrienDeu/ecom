package org.ecom.catalog;

import java.util.UUID;

import org.ecom.shared.domain.Money;

/**
 * Read model of a product exposed to the other modules (cart, order).
 */
public record ProductSummary(UUID id, String sku, String slug, String name, Money price, String imageUrl, boolean active) {

    static ProductSummary of(Product product) {
        return new ProductSummary(product.getId(), product.getSku(), product.getSlug(), product.getName(),
                product.getPrice(), product.getImageUrl(), product.isActive());
    }

}
