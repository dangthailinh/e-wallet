package com.github.yildizmy.order;

import java.util.UUID;

public record SecretRevealResponse(UUID itemId, String secret) {
}
