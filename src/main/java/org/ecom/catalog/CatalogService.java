package org.ecom.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ecom.catalog.CatalogDtos.AdminProductView;
import org.ecom.catalog.CatalogDtos.CategoryView;
import org.ecom.catalog.CatalogDtos.CreateCategoryRequest;
import org.ecom.catalog.CatalogDtos.CreateProductRequest;
import org.ecom.catalog.CatalogDtos.ProductView;
import org.ecom.catalog.CatalogDtos.UpdateProductRequest;
import org.ecom.inventory.InventoryService;
import org.ecom.media.ImageFormat;
import org.ecom.media.ImageStorage;
import org.ecom.media.InvalidImageException;
import org.ecom.shared.domain.Money;
import org.ecom.shared.domain.Slugs;
import org.ecom.shared.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryService inventory;
    private final ImageStorage images;

    CatalogService(ProductRepository products, CategoryRepository categories, InventoryService inventory,
                   ImageStorage images) {
        this.products = products;
        this.categories = categories;
        this.inventory = inventory;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public Page<ProductView> search(ProductKind kind, String categorySlug, String text, Pageable pageable) {
        var criteria = new ArrayList<Specification<Product>>();
        criteria.add(ProductRepository.isActive());
        if (kind != null) {
            criteria.add(ProductRepository.ofKind(kind));
        }
        if (StringUtils.hasText(categorySlug)) {
            criteria.add(ProductRepository.inCategory(categorySlug));
        }
        if (StringUtils.hasText(text)) {
            criteria.add(ProductRepository.nameContains(text.strip()));
        }
        var page = products.findAll(Specification.allOf(criteria), pageable);
        var available = inventory.available(page.map(Product::getId).getContent());
        return page.map(product -> ProductView.of(product, available.getOrDefault(product.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public ProductView getBySlug(String slug) {
        var product = products.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        return ProductView.of(product, inventory.level(product.getId()).available());
    }

    /** All categories, or only those with products of the given kind for sale. */
    @Transactional(readOnly = true)
    public List<CategoryView> categories(ProductKind kind) {
        var found = kind == null ? categories.findAllByOrderByNameAsc() : categories.findWithActiveProducts(kind);
        return found.stream().map(CategoryView::of).toList();
    }

    /** Products by id, including inactive ones so that callers can report them as unavailable. */
    @Transactional(readOnly = true)
    public Map<UUID, ProductSummary> summaries(Collection<UUID> ids) {
        return products.findByIdIn(ids).stream()
                .map(ProductSummary::of)
                .collect(Collectors.toMap(ProductSummary::id, Function.identity()));
    }

    // --- Back-office ---

    @Transactional
    public CategoryView createCategory(CreateCategoryRequest request) {
        var slug = StringUtils.hasText(request.slug()) ? request.slug() : Slugs.of(request.name());
        return CategoryView.of(categories.save(new Category(slug, request.name())));
    }

    @Transactional
    public AdminProductView createProduct(CreateProductRequest request) {
        var slug = StringUtils.hasText(request.slug()) ? request.slug() : Slugs.of(request.name());
        var kind = request.kind() == null ? ProductKind.PRODUCT : request.kind();
        var product = new Product(request.sku(), slug, request.name(), kind, new Money(request.price(), Money.EUR));
        product.update(request.name(), request.description(), product.getPrice(), request.imageUrl(),
                category(request.categoryId()), true);
        products.saveAndFlush(product);
        if (kind == ProductKind.SERVICE) {
            inventory.initializeUntracked(product.getId());
        } else {
            inventory.initialize(product.getId(), request.initialStock() == null ? 0 : request.initialStock());
        }
        return AdminProductView.of(product, inventory.level(product.getId()));
    }

    @Transactional
    public AdminProductView updateProduct(UUID id, UpdateProductRequest request) {
        var product = product(id);
        product.update(request.name(), request.description(), new Money(request.price(), Money.EUR),
                request.imageUrl(), category(request.categoryId()), request.active());
        return AdminProductView.of(product, inventory.level(id));
    }

    /**
     * Replaces the product's image with an uploaded file. The previous image is deleted if it was uploaded too; an
     * external URL entered by hand is left alone.
     */
    @Transactional
    public AdminProductView uploadImage(UUID id, byte[] content) {
        var product = product(id);
        var format = ImageFormat.detect(content).orElseThrow(InvalidImageException::new);
        // A new name for each upload: browsers and CloudFront may cache an image for good
        var url = images.store("products/%s-%s.%s".formatted(id, UUID.randomUUID(), format.extension()), content, format);
        var previous = product.getImageUrl();
        product.update(product.getName(), product.getDescription(), product.getPrice(), url, product.getCategory(),
                product.isActive());
        // Only once the product points to the new image; a failed transaction leaves at worst an unused file
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteImage(previous);
            }
        });
        return AdminProductView.of(product, inventory.level(id));
    }

    @Transactional
    public void deactivateProduct(UUID id) {
        product(id).deactivate();
    }

    @Transactional
    public AdminProductView updateStock(UUID id, int onHand) {
        var product = product(id);
        return AdminProductView.of(product, inventory.setOnHand(id, onHand));
    }

    /** All products, active or not, with their stock level. */
    @Transactional(readOnly = true)
    public Page<AdminProductView> adminSearch(String text, Pageable pageable) {
        Specification<Product> criteria = StringUtils.hasText(text)
                ? ProductRepository.nameContains(text.strip())
                : Specification.unrestricted();
        var page = products.findAll(criteria, pageable);
        var stock = inventory.levels(page.map(Product::getId).getContent());
        return page.map(product -> AdminProductView.of(product, stock.get(product.getId())));
    }

    @Transactional(readOnly = true)
    public AdminProductView adminProduct(UUID id) {
        return AdminProductView.of(product(id), inventory.level(id));
    }

    private void deleteImage(String url) {
        try {
            images.delete(url);
        } catch (RuntimeException e) {
            log.warn("Could not delete replaced image {}", url, e);
        }
    }

    private Product product(UUID id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category category(UUID id) {
        return id == null ? null : categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

}
