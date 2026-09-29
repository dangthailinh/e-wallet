package com.github.yildizmy.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(CatalogQueryService.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CatalogQueryServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CatalogQueryService catalogQueryService;

    @BeforeEach
    void seedCatalog() {
        Category streaming = categoryRepository.save(new Category(
                "Streaming",
                "streaming",
                10,
                CatalogStatus.ACTIVE
        ));
        Category artificialIntelligence = categoryRepository.save(new Category(
                "Artificial intelligence",
                "ai",
                20,
                CatalogStatus.ACTIVE
        ));
        Category hidden = categoryRepository.save(new Category(
                "Hidden",
                "hidden",
                30,
                CatalogStatus.INACTIVE
        ));

        productRepository.saveAllAndFlush(List.of(
                product(streaming, "Netflix Premium", "netflix-premium", 35_000L, CatalogStatus.ACTIVE),
                product(artificialIntelligence, "API token", "api-token", 50_000L, CatalogStatus.ACTIVE),
                product(artificialIntelligence, "ChatGPT Plus", "chatgpt-plus", 150_000L, CatalogStatus.ACTIVE),
                product(artificialIntelligence, "Retired AI", "retired-ai", 10_000L, CatalogStatus.INACTIVE),
                product(hidden, "Hidden product", "hidden-product", 20_000L, CatalogStatus.ACTIVE)
        ));
    }

    @Test
    void searchesFiltersSortsAndPaginatesOnlyPublicProducts() {
        Page<ProductView> search = catalogQueryService.search(new CatalogQuery(
                "CHATGPT",
                null,
                CatalogAvailability.ANY,
                CatalogSort.RELEVANCE,
                0,
                20
        ));
        Page<ProductView> category = catalogQueryService.search(new CatalogQuery(
                null,
                "ai",
                CatalogAvailability.ANY,
                CatalogSort.PRICE_DESC,
                0,
                2
        ));

        assertThat(search.getContent()).extracting(ProductView::slug).containsExactly("chatgpt-plus");
        assertThat(category.getTotalElements()).isEqualTo(2);
        assertThat(category.getContent()).extracting(ProductView::priceVnd)
                .containsExactly(150_000L, 50_000L);
        assertThat(category.getContent()).allSatisfy(view -> {
            assertThat(view.categorySlug()).isEqualTo("ai");
            assertThat(view).isNotInstanceOf(Product.class);
        });
    }

    @Test
    void availabilityUsesTheInventoryBoundaryWithoutInventingStock() {
        Page<ProductView> all = catalogQueryService.search(CatalogQuery.defaults());
        Page<ProductView> inStock = catalogQueryService.search(new CatalogQuery(
                null,
                null,
                CatalogAvailability.IN_STOCK,
                CatalogSort.RELEVANCE,
                0,
                20
        ));
        Page<ProductView> outOfStock = catalogQueryService.search(new CatalogQuery(
                null,
                null,
                CatalogAvailability.OUT_OF_STOCK,
                CatalogSort.RELEVANCE,
                0,
                20
        ));

        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(all.getContent()).allSatisfy(view -> assertThat(view.availableCount()).isZero());
        assertThat(inStock).isEmpty();
        assertThat(outOfStock.getTotalElements()).isEqualTo(3);
    }

    @Test
    void resolvesOnlyPublicProductDetailsBySlugAsAView() {
        assertThat(catalogQueryService.findBySlug("chatgpt-plus"))
                .get()
                .extracting(ProductView::name, ProductView::availableCount)
                .containsExactly("ChatGPT Plus", 0L);
        assertThat(catalogQueryService.findBySlug("retired-ai")).isEmpty();
        assertThat(catalogQueryService.findBySlug("hidden-product")).isEmpty();
    }

    private Product product(
            Category category,
            String name,
            String slug,
            long priceVnd,
            CatalogStatus status
    ) {
        return new Product(
                category,
                name,
                slug,
                "Digital delivery for " + name + ".",
                priceVnd,
                status,
                null
        );
    }
}
