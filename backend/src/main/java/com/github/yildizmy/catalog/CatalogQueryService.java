package com.github.yildizmy.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private final ProductRepository productRepository;

    public CatalogQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<ProductView> search(CatalogQuery query) {
        CatalogQuery criteria = Objects.requireNonNull(query, "query is required");
        String searchTerm = normalizeSearchTerm(criteria.query());
        String escapedSearchTerm = searchTerm == null ? null : escapeLike(searchTerm);
        String searchPrefix = escapedSearchTerm == null ? null : escapedSearchTerm + "%";
        String searchPattern = escapedSearchTerm == null ? null : "%" + escapedSearchTerm + "%";

        return productRepository.searchPublic(
                searchTerm,
                searchPrefix,
                searchPattern,
                criteria.categorySlug(),
                criteria.availability().name(),
                criteria.sort().name(),
                PageRequest.of(criteria.page(), criteria.size())
        ).map(CatalogQueryService::toView);
    }

    public Optional<ProductView> findBySlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        if (normalizedSlug == null) {
            return Optional.empty();
        }
        return productRepository.findPublicBySlug(normalizedSlug).map(CatalogQueryService::toView);
    }

    private static ProductView toView(ProductRepository.ProductRow row) {
        return new ProductView(
                row.getId(),
                row.getCategoryId(),
                row.getCategoryName(),
                row.getCategorySlug(),
                row.getName(),
                row.getSlug(),
                row.getDescription(),
                row.getPriceVnd(),
                row.getImageUrl(),
                row.getAvailableCount(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private static String normalizeSearchTerm(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String normalizeSlug(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 180) {
            return null;
        }
        return normalized;
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
