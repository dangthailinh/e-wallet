package com.github.yildizmy.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogQueryServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CatalogQueryService catalogQueryService = new CatalogQueryService(productRepository);

    @Test
    void normalizesAndEscapesSearchBeforeCallingTheRepository() {
        CatalogQuery query = new CatalogQuery(
                " 50%_\\OFF ",
                "ai",
                CatalogAvailability.IN_STOCK,
                CatalogSort.PRICE_ASC,
                2,
                12
        );
        when(productRepository.searchPublic(
                "50%_\\off",
                "50\\%\\_\\\\off%",
                "%50\\%\\_\\\\off%",
                "ai",
                "IN_STOCK",
                "PRICE_ASC",
                PageRequest.of(2, 12)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 12), 0));

        catalogQueryService.search(query);

        verify(productRepository).searchPublic(
                "50%_\\off",
                "50\\%\\_\\\\off%",
                "%50\\%\\_\\\\off%",
                "ai",
                "IN_STOCK",
                "PRICE_ASC",
                PageRequest.of(2, 12)
        );
    }

    @Test
    void mapsRepositoryProjectionToAStableView() {
        ProductRepository.ProductRow row = productRow();
        when(productRepository.findPublicBySlug("chatgpt-plus")).thenReturn(Optional.of(row));

        Optional<ProductView> result = catalogQueryService.findBySlug(" chatgpt-plus ");

        assertThat(result).get().satisfies(view -> {
            assertThat(view.id()).isEqualTo(row.getId());
            assertThat(view.categorySlug()).isEqualTo("ai");
            assertThat(view.priceVnd()).isEqualTo(150_000L);
            assertThat(view.availableCount()).isEqualTo(4L);
        });
    }

    @Test
    void rejectsUnboundedOrInvalidQueries() {
        assertThatThrownBy(() -> new CatalogQuery(
                null,
                null,
                CatalogAvailability.ANY,
                CatalogSort.RELEVANCE,
                -1,
                20
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
        assertThatThrownBy(() -> new CatalogQuery(
                null,
                null,
                CatalogAvailability.ANY,
                CatalogSort.RELEVANCE,
                0,
                101
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
        assertThatThrownBy(() -> new CatalogQuery(
                null,
                "Not Valid",
                CatalogAvailability.ANY,
                CatalogSort.RELEVANCE,
                0,
                20
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("categorySlug");
    }

    private ProductRepository.ProductRow productRow() {
        ProductRepository.ProductRow row = mock(ProductRepository.ProductRow.class);
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Instant now = Instant.now();
        when(row.getId()).thenReturn(productId);
        when(row.getCategoryId()).thenReturn(categoryId);
        when(row.getCategoryName()).thenReturn("Artificial intelligence");
        when(row.getCategorySlug()).thenReturn("ai");
        when(row.getName()).thenReturn("ChatGPT Plus");
        when(row.getSlug()).thenReturn("chatgpt-plus");
        when(row.getDescription()).thenReturn("Digital delivery.");
        when(row.getPriceVnd()).thenReturn(150_000L);
        when(row.getImageUrl()).thenReturn(null);
        when(row.getAvailableCount()).thenReturn(4L);
        when(row.getCreatedAt()).thenReturn(now);
        when(row.getUpdatedAt()).thenReturn(now);
        return row;
    }
}
