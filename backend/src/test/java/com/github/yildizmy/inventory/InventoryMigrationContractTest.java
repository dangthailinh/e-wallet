package com.github.yildizmy.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMigrationContractTest {

    @Test
    void migrationStoresOnlyAnAuthenticatedEncryptedEnvelope() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V8__add_inventory.sql"
        )).toLowerCase(Locale.ROOT);

        assertThat(migration).contains(
                "ciphertext      bytea",
                "nonce           bytea",
                "auth_tag        bytea",
                "key_version     integer",
                "secret_checksum bytea",
                "idx_inventory_product_status",
                "status = 'available'"
        );
        assertThat(migration).doesNotContain("plaintext_secret", "secret_value");
    }
}
