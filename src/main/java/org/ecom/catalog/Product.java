package org.ecom.catalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

import org.ecom.shared.domain.Money;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
public class Product {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String sku;

    private String slug;

    private String name;

    @Enumerated(EnumType.STRING)
    private ProductKind kind;

    private String description;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price_amount"))
    @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    private Money price;

    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;

    private boolean active;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected Product() {
    }

    public Product(String sku, String slug, String name, ProductKind kind, Money price) {
        this.sku = sku;
        this.slug = slug;
        this.name = name;
        this.kind = kind;
        this.price = price;
        this.active = true;
    }

    public void update(String name, String description, Money price, String imageUrl, Category category, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.category = category;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public ProductKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
    }

}
