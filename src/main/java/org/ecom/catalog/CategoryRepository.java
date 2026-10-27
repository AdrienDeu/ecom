package org.ecom.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlug(String slug);

    List<Category> findAllByOrderByNameAsc();

    @Query("""
            select c from Category c
            where exists (select 1 from Product p where p.category = c and p.kind = :kind and p.active)
            order by c.name""")
    List<Category> findWithActiveProducts(ProductKind kind);

}
