package com.github.yildizmy.inventory;

import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

public record AdminInventoryItemResponse(
        UUID id,
        UUID productId,
        InventoryStatus status,
        String checksumSuffix,
        int keyVersion,
        Instant createdAt,
        Instant updatedAt
) {

    static AdminInventoryItemResponse from(InventoryItem item) {
        byte[] checksum = item.getSecretChecksum();
        int start = Math.max(0, checksum.length - 3);
        String suffix = HexFormat.of().formatHex(Arrays.copyOfRange(checksum, start, checksum.length));
        return new AdminInventoryItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getStatus(),
                suffix,
                item.getKeyVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
