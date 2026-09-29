package com.github.yildizmy.inventory;

import java.util.UUID;

public record InventoryImportRowResult(
        int row,
        InventoryImportRowStatus status,
        UUID inventoryItemId,
        String code,
        String detail
) {

    public static InventoryImportRowResult imported(int row, UUID inventoryItemId) {
        return new InventoryImportRowResult(
                row,
                InventoryImportRowStatus.IMPORTED,
                inventoryItemId,
                null,
                null
        );
    }

    public static InventoryImportRowResult rejected(int row, String code, String detail) {
        return new InventoryImportRowResult(row, InventoryImportRowStatus.REJECTED, null, code, detail);
    }
}
