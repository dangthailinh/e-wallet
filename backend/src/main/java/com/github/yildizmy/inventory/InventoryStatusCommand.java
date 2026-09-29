package com.github.yildizmy.inventory;

import jakarta.validation.constraints.NotNull;

public record InventoryStatusCommand(@NotNull InventoryStatus status) {
}
