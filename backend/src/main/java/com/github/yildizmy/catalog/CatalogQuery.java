package com.github.yildizmy.catalog;

import java.util.Objects;
import java.util.regex.Pattern;

public record CatalogQuery(
        String query,
        String categorySlug,
        CatalogAvailability availability,
        CatalogSort sort,
        int page,
        int size
) {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_PAGE_NUMBER = 10_000;
    private static final int MAX_PAGE_SIZE = 100;

    public CatalogQuery {
        query = normalizeOptional(query);
        categorySlug = normalizeOptional(categorySlug);
        availability = Objects.requireNonNullElse(availability, CatalogAvailability.ANY);
        sort = Objects.requireNonNullElse(sort, CatalogSort.RELEVANCE);

        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must not exceed " + MAX_QUERY_LENGTH + " characters");
        }
        if (categorySlug != null
                && (categorySlug.length() > 120 || !SLUG_PATTERN.matcher(categorySlug).matches())) {
            throw new IllegalArgumentException("categorySlug must be a valid lowercase slug");
        }
        if (page < 0 || page > MAX_PAGE_NUMBER) {
            throw new IllegalArgumentException("page must be between 0 and " + MAX_PAGE_NUMBER);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static CatalogQuery defaults() {
        return new CatalogQuery(null, null, CatalogAvailability.ANY, CatalogSort.RELEVANCE, 0, 20);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
