package com.github.yildizmy.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CatalogStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Category(String name, String slug, int displayOrder, CatalogStatus status) {
        this.id = UUID.randomUUID();
        this.name = requireText(name, "name", 100);
        this.slug = requireSlug(slug, 120);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
        this.displayOrder = displayOrder;
        this.status = Objects.requireNonNull(status, "status is required");
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateDetails(String name, String slug, int displayOrder) {
        this.name = requireText(name, "name", 100);
        this.slug = requireSlug(slug, 120);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must not be negative");
        }
        this.displayOrder = displayOrder;
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
}
