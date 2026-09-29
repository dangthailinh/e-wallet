package com.github.yildizmy.catalog;

import jakarta.validation.constraints.NotNull;

public record CatalogStatusCommand(@NotNull CatalogStatus status) {
}
