package com.github.yildizmy.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
@Testcontainers
class OrderMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void migrationCreatesOrderOwnershipIdempotencyAndUniqueInventoryLink() {
        assertThat(jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '13'", Boolean.class
        )).isTrue();
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint where conrelid in "
                        + "('orders'::regclass, 'order_items'::regclass, 'inventory_items'::regclass)",
                String.class
        );
        assertThat(constraints).contains(
                "uc_order_user_idempotency", "fk_order_owned_wallet",
                "uc_order_item_inventory", "fk_inventory_sold_order_item",
                "fk_order_item_sold_stock", "fk_order_item_matching_product",
                "ck_order_total_positive", "ck_order_item_price_positive"
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicateCheckoutKeyAndInventoryResaleAreRejected() {
        long ownerId = jdbcTemplate.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbcTemplate.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbcTemplate.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Order', 'Test', ?, ?, 'unused')",
                ownerId, "order" + ownerId, "order" + ownerId + "@example.test"
        );
        jdbcTemplate.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', 0, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), ownerId
        );

        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into categories (id, name, slug, status) values (?, 'Order Test', ?, 'ACTIVE')",
                categoryId, "order-test-" + categoryId
        );
        jdbcTemplate.update(
                "insert into products (id, category_id, name, slug, description, price_vnd, status) "
                        + "values (?, ?, 'Test product', ?, 'Test description', 1000, 'ACTIVE')",
                productId, categoryId, "order-test-" + productId
        );
        jdbcTemplate.update(
                "insert into inventory_items (id, product_id, ciphertext, nonce, auth_tag, "
                        + "key_version, secret_checksum, status) values "
                        + "(?, ?, decode('ab', 'hex'), decode(repeat('ab', 12), 'hex'), "
                        + "decode(repeat('ab', 16), 'hex'), 1, decode(repeat('ab', 32), 'hex'), 'AVAILABLE')",
                inventoryId, productId
        );

        UUID orderId = UUID.randomUUID();
        insertOrder(orderId, ownerId, walletId, "checkout-key-001");
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), ownerId, walletId, "checkout-key-001"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrder(UUID.randomUUID(), ownerId, walletId, "another-key", 0))
                .isInstanceOf(DataAccessException.class);

        UUID itemId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            insertOrderItem(itemId, orderId, productId, inventoryId);
            jdbcTemplate.update(
                    "update inventory_items set status = 'SOLD', order_item_id = ? where id = ?",
                    itemId, inventoryId
            );
        });
        jdbcTemplate.update("update products set name = 'Renamed product', price_vnd = 2000 where id = ?", productId);
        assertThat(jdbcTemplate.queryForObject(
                "select product_name from order_items where id = ?", String.class, itemId
        )).isEqualTo("Test product");
        assertThat(jdbcTemplate.queryForObject(
                "select unit_price_vnd from order_items where id = ?", Long.class, itemId
        )).isEqualTo(1000L);
        assertThatThrownBy(() -> insertOrderItem(UUID.randomUUID(), orderId, productId, inventoryId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update inventory_items set order_item_id = ? where id = ?", UUID.randomUUID(), inventoryId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update order_items set product_name = 'Tampered' where id = ?", itemId
        )).isInstanceOf(DataAccessException.class);

        UUID secondProductId = UUID.randomUUID();
        UUID secondInventoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into products (id, category_id, name, slug, description, price_vnd, status) "
                        + "values (?, ?, 'Other product', ?, 'Other description', 1000, 'ACTIVE')",
                secondProductId, categoryId, "other-test-" + secondProductId
        );
        jdbcTemplate.update(
                "insert into inventory_items (id, product_id, ciphertext, nonce, auth_tag, "
                        + "key_version, secret_checksum, status) values "
                        + "(?, ?, decode('cd', 'hex'), decode(repeat('cd', 12), 'hex'), "
                        + "decode(repeat('cd', 16), 'hex'), 1, decode(repeat('cd', 32), 'hex'), 'AVAILABLE')",
                secondInventoryId, productId
        );
        assertThatThrownBy(() -> insertOrderItem(
                UUID.randomUUID(), orderId, secondProductId, secondInventoryId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertOrderItem(
                UUID.randomUUID(), orderId, productId, secondInventoryId
        )).isInstanceOf(DataAccessException.class);

        UUID thirdInventoryId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into inventory_items (id, product_id, ciphertext, nonce, auth_tag, "
                        + "key_version, secret_checksum, status) values "
                        + "(?, ?, decode('ef', 'hex'), decode(repeat('ef', 12), 'hex'), "
                        + "decode(repeat('ef', 16), 'hex'), 1, decode(repeat('ef', 32), 'hex'), 'AVAILABLE')",
                thirdInventoryId, productId
        );
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID mismatchedItemId = UUID.randomUUID();
            insertOrderItem(mismatchedItemId, orderId, productId, secondInventoryId);
            jdbcTemplate.update(
                    "update inventory_items set status = 'SOLD', order_item_id = ? where id = ?",
                    mismatchedItemId, thirdInventoryId
            );
        })).isInstanceOf(DataAccessException.class);
    }

    private void insertOrder(UUID id, long userId, long walletId, String key) {
        insertOrder(id, userId, walletId, key, 1000);
    }

    private void insertOrder(UUID id, long userId, long walletId, String key, long total) {
        jdbcTemplate.update(
                "insert into orders (id, user_id, wallet_id, idempotency_key, status, total_vnd) "
                        + "values (?, ?, ?, ?, 'COMPLETED', ?)", id, userId, walletId, key, total
        );
    }

    private void insertOrderItem(UUID id, UUID orderId, UUID productId, UUID inventoryId) {
        jdbcTemplate.update(
                "insert into order_items (id, order_id, product_id, inventory_item_id, "
                        + "product_name, unit_price_vnd) values (?, ?, ?, ?, 'Test product', 1000)",
                id, orderId, productId, inventoryId
        );
    }
}
