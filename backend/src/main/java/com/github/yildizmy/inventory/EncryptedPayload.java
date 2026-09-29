package com.github.yildizmy.inventory;

import java.util.Arrays;
import java.util.Objects;

public record EncryptedPayload(
        byte[] ciphertext,
        byte[] nonce,
        byte[] authTag,
        int keyVersion,
        byte[] secretChecksum
) {

    public EncryptedPayload {
        ciphertext = copy(ciphertext, "ciphertext");
        nonce = copy(nonce, "nonce");
        authTag = copy(authTag, "authTag");
        secretChecksum = copy(secretChecksum, "secretChecksum");
        if (ciphertext.length < 1 || ciphertext.length > InventoryItem.MAX_CIPHERTEXT_BYTES) {
            throw new IllegalArgumentException("ciphertext length is invalid");
        }
        if (nonce.length != InventoryItem.NONCE_BYTES) {
            throw new IllegalArgumentException("nonce length is invalid");
        }
        if (authTag.length != InventoryItem.AUTH_TAG_BYTES) {
            throw new IllegalArgumentException("authTag length is invalid");
        }
        if (secretChecksum.length != InventoryItem.CHECKSUM_BYTES) {
            throw new IllegalArgumentException("secretChecksum length is invalid");
        }
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
    }

    @Override
    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public byte[] authTag() {
        return Arrays.copyOf(authTag, authTag.length);
    }

    @Override
    public byte[] secretChecksum() {
        return Arrays.copyOf(secretChecksum, secretChecksum.length);
    }

    private static byte[] copy(byte[] value, String name) {
        byte[] source = Objects.requireNonNull(value, name + " is required");
        return Arrays.copyOf(source, source.length);
    }
}
