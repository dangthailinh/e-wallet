package com.github.yildizmy.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RefreshTokenHash {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final String value;

    private RefreshTokenHash(String value) {
        this.value = value;
    }

    public static RefreshTokenHash fromToken(String rawToken) {
        String token = Objects.requireNonNull(rawToken, "rawToken is required");
        if (token.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return new RefreshTokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    static RefreshTokenHash fromDigest(String digest) {
        String value = Objects.requireNonNull(digest, "digest is required");
        if (!SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("digest must be a lowercase SHA-256 value");
        }
        return new RefreshTokenHash(value);
    }

    String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RefreshTokenHash that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "RefreshTokenHash[redacted]";
    }
}
