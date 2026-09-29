package com.github.yildizmy.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);

    @Query(value = """
            SELECT p.id AS "id",
                   p.category_id AS "categoryId",
                   c.name AS "categoryName",
                   c.slug AS "categorySlug",
                   p.name AS "name",
                   p.slug AS "slug",
                   p.description AS "description",
                   p.price_vnd AS "priceVnd",
                   p.image_url AS "imageUrl",
                   catalog_available_count(p.id) AS "availableCount",
                   p.created_at AS "createdAt",
                   p.updated_at AS "updatedAt"
            FROM products p
            JOIN categories c ON c.id = p.category_id
            WHERE p.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
              AND (CAST(:searchPattern AS TEXT) IS NULL
                   OR lower(p.name) LIKE :searchPattern ESCAPE '\\'
                   OR lower(p.description) LIKE :searchPattern ESCAPE '\\')
              AND (CAST(:categorySlug AS TEXT) IS NULL OR c.slug = :categorySlug)
              AND (:availability = 'ANY'
                   OR (:availability = 'IN_STOCK' AND catalog_available_count(p.id) > 0)
                   OR (:availability = 'OUT_OF_STOCK' AND catalog_available_count(p.id) = 0))
            ORDER BY
              CASE WHEN :sort = 'RELEVANCE' AND CAST(:searchTerm AS TEXT) IS NOT NULL THEN
                   CASE
                     WHEN lower(p.name) = :searchTerm THEN 0
                     WHEN lower(p.name) LIKE :searchPrefix ESCAPE '\\' THEN 1
                     ELSE 2
                   END
              END ASC,
              CASE WHEN :sort = 'NEWEST' THEN p.created_at END DESC,
              CASE WHEN :sort = 'PRICE_ASC' THEN p.price_vnd END ASC,
              CASE WHEN :sort = 'PRICE_DESC' THEN p.price_vnd END DESC,
              CASE WHEN :sort = 'NAME_ASC' THEN lower(p.name) END ASC,
              c.display_order ASC,
              lower(p.name) ASC,
              p.id ASC
            """, countQuery = """
            SELECT count(*)
            FROM products p
            JOIN categories c ON c.id = p.category_id
            WHERE p.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
              AND (CAST(:searchPattern AS TEXT) IS NULL
                   OR lower(p.name) LIKE :searchPattern ESCAPE '\\'
                   OR lower(p.description) LIKE :searchPattern ESCAPE '\\')
              AND (CAST(:categorySlug AS TEXT) IS NULL OR c.slug = :categorySlug)
              AND (:availability = 'ANY'
                   OR (:availability = 'IN_STOCK' AND catalog_available_count(p.id) > 0)
                   OR (:availability = 'OUT_OF_STOCK' AND catalog_available_count(p.id) = 0))
            """, nativeQuery = true)
    Page<ProductRow> searchPublic(
            @Param("searchTerm") String searchTerm,
            @Param("searchPrefix") String searchPrefix,
            @Param("searchPattern") String searchPattern,
            @Param("categorySlug") String categorySlug,
            @Param("availability") String availability,
            @Param("sort") String sort,
            Pageable pageable
    );

    @Query(value = """
            SELECT p.id AS "id",
                   p.category_id AS "categoryId",
                   c.name AS "categoryName",
                   c.slug AS "categorySlug",
                   p.name AS "name",
                   p.slug AS "slug",
                   p.description AS "description",
                   p.price_vnd AS "priceVnd",
                   p.image_url AS "imageUrl",
                   catalog_available_count(p.id) AS "availableCount",
                   p.created_at AS "createdAt",
                   p.updated_at AS "updatedAt"
            FROM products p
            JOIN categories c ON c.id = p.category_id
            WHERE p.slug = :slug
              AND p.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
            """, nativeQuery = true)
    Optional<ProductRow> findPublicBySlug(@Param("slug") String slug);

    @Query(value = """
            SELECT count(*)
            FROM products p
            WHERE p.status = 'ACTIVE'
              AND catalog_available_count(p.id) <= :threshold
            """, nativeQuery = true)
    long countLowStockProducts(@Param("threshold") int threshold);

    interface ProductRow {

        UUID getId();

        UUID getCategoryId();

        String getCategoryName();

        String getCategorySlug();

        String getName();

        String getSlug();

        String getDescription();

        long getPriceVnd();

        String getImageUrl();

        long getAvailableCount();

        Instant getCreatedAt();

        Instant getUpdatedAt();
    }
}
