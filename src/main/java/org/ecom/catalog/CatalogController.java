package org.ecom.catalog;

import java.util.List;

import org.ecom.catalog.CatalogDtos.CategoryView;
import org.ecom.catalog.CatalogDtos.ProductView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    PagedModel<ProductView> products(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String q,
                                     @PageableDefault(size = 24, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(catalog.search(category, q, pageable));
    }

    @GetMapping("/products/{slug}")
    ProductView product(@PathVariable String slug) {
        return catalog.getBySlug(slug);
    }

    @GetMapping("/categories")
    List<CategoryView> categories() {
        return catalog.categories();
    }

}
