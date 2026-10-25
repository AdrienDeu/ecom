package org.ecom.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findByIdIn(Collection<UUID> ids);

    static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    static Specification<Product> inCategory(String categorySlug) {
        return (root, query, cb) -> cb.equal(root.get("category").get("slug"), categorySlug);
    }

    static Specification<Product> nameContains(String text) {
        var pattern = "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

}
