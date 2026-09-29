package com.github.yildizmy.inventory;

import com.github.yildizmy.catalog.CatalogStatus;
import com.github.yildizmy.catalog.Category;
import com.github.yildizmy.catalog.Product;
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
class InventoryMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mapsEncryptedInventoryAndFeedsCatalogAvailability() {
        Product product = persistProduct();
        InventoryItem item = new InventoryItem(
                UUID.randomUUID(),
                product,
                new byte[]{1, 2, 3},
                bytes(12, (byte) 4),
                bytes(16, (byte) 5),
                1,
                bytes(32, (byte) 6)
        );

        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        InventoryItem stored = entityManager.find(InventoryItem.class, item.getId());
        Long available = jdbcTemplate.queryForObject(
                "select catalog_available_count(?)",
                Long.class,
                product.getId()
        );

        assertThat(stored.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
        assertThat(stored.getCiphertext()).containsExactly(1, 2, 3);
        assertThat(stored.getNonce()).hasSize(12);
        assertThat(stored.getAuthTag()).hasSize(16);
        assertThat(stored.getSecretChecksum()).hasSize(32);
        assertThat(available).isEqualTo(1L);
    }

    @Test
    void migrationDefinesEncryptedColumnsConstraintsAndIndexes() {
        Boolean migrationApplied = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '8'",
                Boolean.class
        );
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'inventory_items'",
                String.class
        );
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint where conrelid = 'inventory_items'::regclass",
                String.class
        );
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public' and tablename = 'inventory_items'",
                String.class
        );

        assertThat(migrationApplied).isTrue();
        assertThat(columns).contains("ciphertext", "nonce", "auth_tag", "key_version", "secret_checksum");
        assertThat(columns).doesNotContain("secret", "plaintext", "payload");
        assertThat(constraints).contains(
                "ck_inventory_status",
                "ck_inventory_nonce_length",
                "ck_inventory_auth_tag_length",
                "ck_inventory_checksum_length",
                "fk_inventory_product",
                "uc_inventory_order_item"
        );
        assertThat(indexes).contains("idx_inventory_product_status");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsInvalidCryptoEnvelopeStatusAndOrphanSaleLink() {
        UUID productId = insertProductWithCategory();
        UUID orderItemId = UUID.randomUUID();

        assertThatThrownBy(() -> insertInventory(
                UUID.randomUUID(), productId, "SOLD", bytes(12, (byte) 1), orderItemId
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertInventory(
                UUID.randomUUID(), productId, "UNKNOWN", bytes(12, (byte) 1), null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertInventory(
                UUID.randomUUID(), productId, "AVAILABLE", bytes(11, (byte) 1), null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Product persistProduct() {
        Category category = new Category("Inventory", "inventory", 1, CatalogStatus.ACTIVE);
        Product product = new Product(
                category,
                "Encrypted item",
                "encrypted-item",
                "Encrypted delivery.",
                10_000L,
                CatalogStatus.ACTIVE,
                null
        );
        entityManager.persist(category);
        entityManager.persist(product);
        return product;
    }

    private UUID insertProductWithCategory() {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into categories "
                        + "(id, name, slug, display_order, status, created_at, updated_at, version) "
                        + "values (?, ?, ?, 0, 'ACTIVE', now(), now(), 0)",
                categoryId,
                "Inventory " + categoryId,
                "inventory-" + categoryId
        );
        jdbcTemplate.update(
                "insert into products "
                        + "(id, category_id, name, slug, description, price_vnd, status, created_at, updated_at, version) "
                        + "values (?, ?, ?, ?, 'Encrypted delivery.', 10000, 'ACTIVE', now(), now(), 0)",
                productId,
                categoryId,
                "Product " + productId,
                "product-" + productId
        );
        return productId;
    }

    private void insertInventory(UUID id, UUID productId, String status, byte[] nonce, UUID orderItemId) {
        jdbcTemplate.update(
                "insert into inventory_items "
                        + "(id, product_id, ciphertext, nonce, auth_tag, key_version, secret_checksum, status, "
                        + "order_item_id, created_at, updated_at, version) "
                        + "values (?, ?, ?, ?, ?, 1, ?, ?, ?, now(), now(), 0)",
                id,
                productId,
                new byte[]{1},
                nonce,
                bytes(16, (byte) 2),
                bytes(32, (byte) 3),
                status,
                orderItemId
        );
    }

    private static byte[] bytes(int size, byte value) {
        byte[] result = new byte[size];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
