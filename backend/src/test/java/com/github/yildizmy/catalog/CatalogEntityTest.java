package com.github.yildizmy.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogEntityTest {

    @Test
    void assignsStableIdsAndStoresVndAsAnInteger() {
        Category category = new Category(" AI accounts ", "ai-accounts", 10, CatalogStatus.ACTIVE);
        Product product = new Product(
                category,
                " ChatGPT Plus ",
                "chatgpt-plus",
                " Digital delivery. ",
                150_000L,
                CatalogStatus.ACTIVE,
                null
        );

        assertThat(category.getId()).isNotNull();
        assertThat(category.getName()).isEqualTo("AI accounts");
        assertThat(product.getId()).isNotNull().isNotEqualTo(category.getId());
        assertThat(product.getPriceVnd()).isEqualTo(150_000L);
        assertThat(product.getDescription()).isEqualTo("Digital delivery.");
        assertThat(product.getCreatedAt()).isEqualTo(product.getUpdatedAt());
    }

    @Test
    void rejectsInvalidCatalogValuesBeforePersistence() {
        Category category = new Category("AI accounts", "ai-accounts", 0, CatalogStatus.ACTIVE);

        assertThatThrownBy(() -> new Category("AI", "Not A Slug", 0, CatalogStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
        assertThatThrownBy(() -> new Category("AI", "ai", -1, CatalogStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayOrder");
        assertThatThrownBy(() -> new Product(
                category,
                "Invalid",
                "invalid-price",
                "Description",
                0,
                CatalogStatus.ACTIVE,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priceVnd");
    }
}
