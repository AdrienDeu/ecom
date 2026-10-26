package org.ecom.catalog;

import java.util.UUID;

import jakarta.validation.Valid;

import org.ecom.catalog.CatalogDtos.AdminProductView;
import org.ecom.catalog.CatalogDtos.CategoryView;
import org.ecom.catalog.CatalogDtos.CreateCategoryRequest;
import org.ecom.catalog.CatalogDtos.CreateProductRequest;
import org.ecom.catalog.CatalogDtos.UpdateProductRequest;
import org.ecom.catalog.CatalogDtos.UpdateStockRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office catalog management, restricted to the {@code ADMIN} role by the security configuration.
 */
@RestController
@RequestMapping("/api/admin")
class AdminCatalogController {

    private final CatalogService catalog;

    AdminCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return catalog.createCategory(request);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductView createProduct(@Valid @RequestBody CreateProductRequest request) {
        return catalog.createProduct(request);
    }

    @GetMapping("/products")
    PagedModel<AdminProductView> products(@RequestParam(required = false) String q,
                                          @ParameterObject @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(catalog.adminSearch(q, pageable));
    }

    @GetMapping("/products/{id}")
    AdminProductView product(@PathVariable UUID id) {
        return catalog.adminProduct(id);
    }

    @PutMapping("/products/{id}")
    AdminProductView updateProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return catalog.updateProduct(id, request);
    }

    /** Products are never physically deleted: past orders reference them. */
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivateProduct(@PathVariable UUID id) {
        catalog.deactivateProduct(id);
    }

    @PutMapping("/products/{id}/stock")
    AdminProductView updateStock(@PathVariable UUID id, @Valid @RequestBody UpdateStockRequest request) {
        return catalog.updateStock(id, request.onHand());
    }

}
