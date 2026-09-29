package com.github.yildizmy.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductCommand(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 180)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
        @NotBlank @Size(max = 10_000) String description,
        @Positive long priceVnd,
        @NotNull CatalogStatus status,
        @Size(max = 2048)
        @Pattern(regexp = "^https://\\S+$", message = "must be an HTTPS URL") String imageUrl
) {
}
