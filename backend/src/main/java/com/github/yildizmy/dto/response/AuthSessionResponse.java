package com.github.yildizmy.dto.response;

import java.util.List;
import java.util.Objects;

/** Public session representation. Authentication credentials are deliberately excluded. */
public record AuthSessionResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        List<String> roles
) {

    public AuthSessionResponse {
        Objects.requireNonNull(id, "id is required");
        username = requireText(username, "username");
        firstName = requireText(firstName, "firstName");
        lastName = requireText(lastName, "lastName");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles are required"));
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
