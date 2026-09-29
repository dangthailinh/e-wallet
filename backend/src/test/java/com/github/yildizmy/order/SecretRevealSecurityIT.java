package com.github.yildizmy.order;

import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.inventory.EncryptedPayload;
import com.github.yildizmy.inventory.InventoryCryptoProperties;
import com.github.yildizmy.inventory.InventoryCryptoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({SecretRevealService.class, SecretRevealSecurityIT.CryptoConfiguration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SecretRevealSecurityIT {

    private static final String SECRET = "OWNER_ONLY_SECRET_91f2b7";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SecretRevealService service;

    @Autowired
    private InventoryCryptoService crypto;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onlyOwnerCanRevealAndSecretNeverAppearsInLogs(CapturedOutput output) {
        long ownerId = insertUserAndWallet("owner");
        long strangerId = insertUserAndWallet("stranger");
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        EncryptedPayload encrypted = crypto.encrypt(inventoryId, productId, SECRET);
        insertPurchasedItem(ownerId, categoryId, productId, inventoryId, orderId, itemId, encrypted);

        SecretRevealResponse response = service.reveal(
                ownerId, orderId, itemId, "request-reveal-owner-001"
        );
        assertThat(response.secret()).isEqualTo(SECRET);
        assertThatThrownBy(() -> service.reveal(
                strangerId, orderId, itemId, "request-reveal-stranger-001"
        ))
                .isInstanceOf(NoSuchElementFoundException.class)
                .hasMessage("Order item not found");
        assertThatThrownBy(() -> service.reveal(
                strangerId, UUID.randomUUID(), UUID.randomUUID(), "request-reveal-missing-001"
        ))
                .isInstanceOf(NoSuchElementFoundException.class)
                .hasMessage("Order item not found");

        assertThat(jdbc.queryForMap(
                "select actor_user_id, action, resource_type, resource_id, parent_resource_id, request_id "
                        + "from audit_logs"
        )).containsEntry("actor_user_id", ownerId)
                .containsEntry("action", "SECRET_REVEALED")
                .containsEntry("resource_type", "ORDER_ITEM")
                .containsEntry("resource_id", itemId)
                .containsEntry("parent_resource_id", orderId)
                .containsEntry("request_id", "request-reveal-owner-001");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs a where row_to_json(a)::text like ?",
                Long.class, "%" + SECRET + "%"
        )).isZero();
        assertThat(output).doesNotContain(SECRET);
    }

    private long insertUserAndWallet(String label) {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Reveal', 'Test', ?, ?, 'unused')",
                userId, label + userId, label + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', 0, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), userId
        );
        return userId;
    }

    private void insertPurchasedItem(
            long ownerId,
            UUID categoryId,
            UUID productId,
            UUID inventoryId,
            UUID orderId,
            UUID itemId,
            EncryptedPayload encrypted
    ) {
        long walletId = jdbc.queryForObject(
                "select id from wallet where user_id = ?", Long.class, ownerId
        );
        jdbc.update(
                "insert into categories (id, name, slug, status) values (?, 'Reveal', ?, 'ACTIVE')",
                categoryId, "reveal-" + categoryId
        );
        jdbc.update(
                "insert into products (id, category_id, name, slug, description, price_vnd, status) "
                        + "values (?, ?, 'Reveal product', ?, 'Encrypted delivery', 1000, 'ACTIVE')",
                productId, categoryId, "reveal-" + productId
        );
        jdbc.update(
                "insert into inventory_items (id, product_id, ciphertext, nonce, auth_tag, "
                        + "key_version, secret_checksum, status) values (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE')",
                inventoryId, productId, encrypted.ciphertext(), encrypted.nonce(), encrypted.authTag(),
                encrypted.keyVersion(), encrypted.secretChecksum()
        );
        jdbc.update(
                "insert into orders (id, user_id, wallet_id, idempotency_key, status, total_vnd) "
                        + "values (?, ?, ?, ?, 'COMPLETED', 1000)",
                orderId, ownerId, walletId, "reveal-key-" + orderId
        );
        jdbc.update(
                "insert into order_items (id, order_id, product_id, inventory_item_id, product_name, unit_price_vnd) "
                        + "values (?, ?, ?, ?, 'Reveal product', 1000)",
                itemId, orderId, productId, inventoryId
        );
        jdbc.update(
                "update inventory_items set status = 'SOLD', order_item_id = ? where id = ?",
                itemId, inventoryId
        );
    }

    @TestConfiguration
    static class CryptoConfiguration {
        @Bean
        InventoryCryptoService inventoryCryptoService() {
            return new InventoryCryptoService(new InventoryCryptoProperties(
                    1,
                    "1:WwbszMX8gi/4G820UlcrpKMm3XNiOwfEJ4wnjg798ik=",
                    "D6RhTtwfDR8AttrHb4Tny+mwBxC91zkFrFGnDgnBpL8="
            ));
        }
    }
}
