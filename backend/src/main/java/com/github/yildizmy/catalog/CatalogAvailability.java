package com.github.yildizmy.catalog;

import java.util.Arrays;

public enum CatalogAvailability {
    ANY("any"),
    IN_STOCK("in-stock"),
    OUT_OF_STOCK("out-of-stock");

    private final String queryValue;

    CatalogAvailability(String queryValue) {
        this.queryValue = queryValue;
    }

    public static CatalogAvailability fromQueryValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.queryValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported availability"));
    }

    public String queryValue() {
        return queryValue;
    }
}
