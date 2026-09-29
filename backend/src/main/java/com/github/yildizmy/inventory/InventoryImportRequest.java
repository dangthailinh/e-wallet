package com.github.yildizmy.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InventoryImportRequest(
        @NotNull
        @Size(min = 1, max = 100)
        List<String> secrets
) {
}
