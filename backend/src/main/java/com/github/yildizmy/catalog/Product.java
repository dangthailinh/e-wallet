package com.github.yildizmy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CatalogStatus status;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Product(
            Category category,
            String name,
            String slug,
            String description,
            long priceVnd,
            CatalogStatus status,
            String imageUrl
    ) {
        this.id = UUID.randomUUID();
        this.category = Objects.requireNonNull(category, "category is required");
        this.name = requireText(name, "name", 160);
        this.slug = requireSlug(slug, 180);
        this.description = requireText(description, "description", 10_000);
        if (priceVnd <= 0) {
            throw new IllegalArgumentException("priceVnd must be positive");
        }
        this.priceVnd = priceVnd;
        this.status = Objects.requireNonNull(status, "status is required");
        this.imageUrl = optionalText(imageUrl, "imageUrl", 2048);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateDetails(
            Category category,
            String name,
            String slug,
            String description,
            long priceVnd,
            String imageUrl
    ) {
        this.category = Objects.requireNonNull(category, "category is required");
        this.name = requireText(name, "name", 160);
        this.slug = requireSlug(slug, 180);
        this.description = requireText(description, "description", 10_000);
        if (priceVnd <= 0) {
            throw new IllegalArgumentException("priceVnd must be positive");
        }
        this.priceVnd = priceVnd;
        this.imageUrl = optionalText(imageUrl, "imageUrl", 2048);
    }

    public void changeStatus(CatalogStatus status) {
        this.status = Objects.requireNonNull(status, "status is required");
    }

    @PreUpdate
    private void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        String text = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must contain 1 to " + maxLength + " characters");
        }
        return text;
    }

    private static String requireSlug(String value, int maxLength) {
        String slug = requireText(value, "slug", maxLength);
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug must use lowercase letters, digits and single hyphens");
        }
        return slug;
    }

    private static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        return requireText(value, fieldName, maxLength);
    }
}
