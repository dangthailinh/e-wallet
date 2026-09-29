package com.github.yildizmy.catalog;

import java.util.Arrays;

public enum CatalogSort {
    RELEVANCE("relevance"),
    NEWEST("newest"),
    PRICE_ASC("price-asc"),
    PRICE_DESC("price-desc"),
    NAME_ASC("name-asc");

    private final String queryValue;

    CatalogSort(String queryValue) {
        this.queryValue = queryValue;
    }

    public static CatalogSort fromQueryValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.queryValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported catalog sort"));
    }

    public String queryValue() {
        return queryValue;
    }
}
