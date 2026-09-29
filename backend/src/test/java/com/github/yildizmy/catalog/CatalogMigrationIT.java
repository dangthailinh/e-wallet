package com.github.yildizmy.catalog;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CatalogMigrationIT {

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
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mapsCatalogEntitiesAfterFlywayBuildsAnEmptyDatabase() {
        Category category = new Category("AI accounts", "ai-accounts", 10, CatalogStatus.ACTIVE);
        Product product = new Product(
                category,
                "ChatGPT Plus - 1 month",
                "chatgpt-plus-1-month",
                "A digital account delivered after payment.",
                150_000L,
                CatalogStatus.ACTIVE,
                "https://cdn.example.test/products/chatgpt-plus.png"
        );

        entityManager.persist(category);
        entityManager.persist(product);
        entityManager.flush();
        entityManager.clear();

        Category storedCategory = entityManager.find(Category.class, category.getId());
        Product storedProduct = entityManager.find(Product.class, product.getId());

        assertThat(storedCategory.getSlug()).isEqualTo("ai-accounts");
        assertThat(storedProduct.getCategory().getId()).isEqualTo(category.getId());
        assertThat(storedProduct.getPriceVnd()).isEqualTo(150_000L);
        assertThat(storedProduct.getCreatedAt()).isNotNull();
        assertThat(storedProduct.getUpdatedAt()).isNotNull();
    }

    @Test
    void migrationDefinesCatalogIntegrityConstraints() {
        Boolean migrationApplied = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '7'",
                Boolean.class
        );
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint where conrelid in ('categories'::regclass, 'products'::regclass)",
                String.class
        );
        String priceType = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'products' and column_name = 'price_vnd'",
                String.class
        );

        assertThat(migrationApplied).isTrue();
        assertThat(constraints).contains(
                "uc_category_slug",
                "uc_product_slug",
                "ck_product_price_vnd_positive",
                "ck_category_slug_format",
                "ck_product_slug_format",
                "fk_product_category"
        );
        assertThat(priceType).isEqualTo("bigint");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsDuplicateSlugsAndNonPositivePrices() {
        UUID categoryId = UUID.randomUUID();
        insertCategory(categoryId, "accounts");

        assertThatThrownBy(() -> insertCategory(UUID.randomUUID(), "accounts"))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertProduct(UUID.randomUUID(), categoryId, "monthly-account", 100_000L);
        assertThatThrownBy(() -> insertProduct(UUID.randomUUID(), categoryId, "monthly-account", 120_000L))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertProduct(UUID.randomUUID(), categoryId, "invalid-price", 0L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertProduct(UUID id, UUID categoryId, String slug, long priceVnd) {
        jdbcTemplate.update(
                "insert into products "
                        + "(id, category_id, name, slug, description, price_vnd, status, created_at, updated_at, version) "
                        + "values (?, ?, ?, ?, ?, ?, ?, now(), now(), 0)",
                id,
                categoryId,
                "Product " + id,
                slug,
                "Digital delivery.",
                priceVnd,
                "ACTIVE"
        );
    }

    private void insertCategory(UUID id, String slug) {
        jdbcTemplate.update(
                "insert into categories "
                        + "(id, name, slug, display_order, status, created_at, updated_at, version) "
                        + "values (?, ?, ?, 0, 'ACTIVE', now(), now(), 0)",
                id,
                "Category " + id,
                slug
        );
    }
}
