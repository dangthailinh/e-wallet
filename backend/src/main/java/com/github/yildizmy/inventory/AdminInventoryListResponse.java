package com.github.yildizmy.inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminInventoryListResponse(
        UUID productId,
        Map<InventoryStatus, Long> counts,
        List<AdminInventoryItemResponse> items
) {

    public AdminInventoryListResponse {
        counts = Map.copyOf(counts);
        items = List.copyOf(items);
    }
}
