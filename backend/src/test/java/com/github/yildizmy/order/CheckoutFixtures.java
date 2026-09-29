package com.github.yildizmy.order;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CheckoutFixtures {

    private final JdbcTemplate jdbc;

    CheckoutFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Buyer buyer(long balanceVnd) {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Checkout', 'Buyer', ?, ?, 'unused')",
                userId, "checkout-" + userId, "checkout-" + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', ?, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), balanceVnd, userId
        );
        return new Buyer(userId, walletId);
    }

    Stock stock(long priceVnd, int quantity) {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbc.update(
                "insert into categories (id, name, slug, status) values (?, 'Checkout', ?, 'ACTIVE')",
                categoryId, "checkout-" + categoryId
        );
        jdbc.update(
                "insert into products (id, category_id, name, slug, description, price_vnd, status) "
                        + "values (?, ?, 'Checkout product', ?, 'Encrypted delivery', ?, 'ACTIVE')",
                productId, categoryId, "checkout-" + productId, priceVnd
        );
        List<UUID> inventoryIds = new ArrayList<>();
        for (int index = 0; index < quantity; index++) {
            UUID inventoryId = UUID.randomUUID();
            inventoryIds.add(inventoryId);
            byte[] checksum = ByteBuffer.allocate(32)
                    .putLong(inventoryId.getMostSignificantBits())
                    .putLong(inventoryId.getLeastSignificantBits())
                    .putLong(index)
                    .putLong(~index)
                    .array();
            jdbc.update(
                    "insert into inventory_items (id, product_id, ciphertext, nonce, auth_tag, "
                            + "key_version, secret_checksum, status) values (?, ?, ?, ?, ?, 1, ?, 'AVAILABLE')",
                    inventoryId, productId, new byte[]{1}, new byte[12], new byte[16], checksum
            );
        }
        return new Stock(productId, inventoryIds, priceVnd);
    }

    long balance(long walletId) {
        return jdbc.queryForObject("select balance from wallet where id = ?", Long.class, walletId);
    }

    int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    record Buyer(long userId, long walletId) {
    }

    record Stock(UUID productId, List<UUID> inventoryIds, long priceVnd) {
    }
}
