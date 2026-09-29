package com.github.yildizmy.inventory;

import java.util.List;

public record InventoryImportResponse(
        int requested,
        int imported,
        List<InventoryImportRowResult> results
) {

    public InventoryImportResponse {
        results = List.copyOf(results);
    }
}
