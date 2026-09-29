package com.github.yildizmy.inventory;

import com.github.yildizmy.catalog.CatalogStatus;
import com.github.yildizmy.catalog.Category;
import com.github.yildizmy.catalog.Product;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void ownsDefensiveCopiesOfTheEncryptedEnvelope() {
        byte[] ciphertext = bytes(3, (byte) 1);
        byte[] nonce = bytes(12, (byte) 2);
        byte[] authTag = bytes(16, (byte) 3);
        byte[] checksum = bytes(32, (byte) 4);

        InventoryItem item = new InventoryItem(UUID.randomUUID(), product(), ciphertext, nonce, authTag, 1, checksum);
        ciphertext[0] = 9;
        item.getNonce()[0] = 9;

        assertThat(item.getCiphertext()).containsExactly(1, 1, 1);
        assertThat(item.getNonce()).containsOnly(2);
        assertThat(item.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
    }

    @Test
    void enforcesApprovedLifecycleTransitionsAndOneSaleLink() {
        InventoryItem item = item();
        UUID orderItemId = UUID.randomUUID();

        item.reserve();
        item.sell(orderItemId);

        assertThat(item.getStatus()).isEqualTo(InventoryStatus.SOLD);
        assertThat(item.getOrderItemId()).isEqualTo(orderItemId);
        assertThatThrownBy(item::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> item.sell(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedCryptoFields() {
        assertThatThrownBy(() -> new InventoryItem(
                UUID.randomUUID(),
                product(),
                bytes(1, (byte) 1),
                bytes(11, (byte) 2),
                bytes(16, (byte) 3),
                1,
                bytes(32, (byte) 4)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateTimestampNeverMovesBeforeDatabaseCreatedAt() {
        InventoryItem item = item();
        Instant databaseTimestamp = Instant.parse("2100-01-01T00:00:00Z");
        ReflectionTestUtils.setField(item, "createdAt", databaseTimestamp);
        ReflectionTestUtils.setField(item, "updatedAt", Instant.EPOCH);

        ReflectionTestUtils.invokeMethod(item, "touchUpdatedAt");

        assertThat(item.getUpdatedAt()).isEqualTo(databaseTimestamp);
    }

    @Test
    void updateTimestampNeverMovesBeforeItsPriorValue() {
        InventoryItem item = item();
        Instant databaseTimestamp = Instant.parse("2100-01-01T00:00:00Z");
        ReflectionTestUtils.setField(item, "createdAt", Instant.EPOCH);
        ReflectionTestUtils.setField(item, "updatedAt", databaseTimestamp);

        ReflectionTestUtils.invokeMethod(item, "touchUpdatedAt");

        assertThat(item.getUpdatedAt()).isEqualTo(databaseTimestamp);
    }

    private InventoryItem item() {
        return new InventoryItem(
                UUID.randomUUID(),
                product(),
                bytes(3, (byte) 1),
                bytes(12, (byte) 2),
                bytes(16, (byte) 3),
                1,
                bytes(32, (byte) 4)
        );
    }

    private Product product() {
        Category category = new Category("Inventory", "inventory", 1, CatalogStatus.ACTIVE);
        return new Product(
                category,
                "Encrypted item",
                "encrypted-item",
                "Encrypted delivery.",
                10_000L,
                CatalogStatus.ACTIVE,
                null
        );
    }

    private static byte[] bytes(int size, byte value) {
        byte[] result = new byte[size];
        Arrays.fill(result, value);
        return result;
    }
}
