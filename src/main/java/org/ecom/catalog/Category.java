package org.ecom.catalog;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.UuidGenerator;

@Entity
public class Category {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String slug;

    private String name;

    protected Category() {
    }

    public Category(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

}
