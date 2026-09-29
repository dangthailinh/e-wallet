package com.github.yildizmy.inventory;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "app.inventory.crypto")
public final class InventoryCryptoProperties {

    private static final int KEY_BYTES = 32;
    private static final int MAX_KEY_VERSIONS = 16;

    private final int activeKeyVersion;
    private final Map<Integer, byte[]> encryptionKeys;
    private final byte[] checksumKey;

    public InventoryCryptoProperties(int activeKeyVersion, String keyring, String checksumKey) {
        if (activeKeyVersion < 1) {
            throw new IllegalArgumentException("Inventory active key version must be positive");
        }
        this.activeKeyVersion = activeKeyVersion;
        this.encryptionKeys = parseKeyring(keyring);
        if (!encryptionKeys.containsKey(activeKeyVersion)) {
            throw new IllegalArgumentException("Inventory active key version is missing from the keyring");
        }
        this.checksumKey = decodeStrongKey(checksumKey, "checksum key");
        if (encryptionKeys.values().stream().anyMatch(key -> MessageDigest.isEqual(key, this.checksumKey))) {
            throw new IllegalArgumentException("Inventory checksum key must differ from encryption keys");
        }
    }

    public int activeKeyVersion() {
        return activeKeyVersion;
    }

    public SecretKey encryptionKey(int version) {
        byte[] key = encryptionKeys.get(version);
        if (key == null) {
            throw new InventoryCryptoException("Inventory encryption key version is unavailable");
        }
        return new SecretKeySpec(key.clone(), "AES");
    }

    public SecretKey checksumKey() {
        return new SecretKeySpec(checksumKey.clone(), "HmacSHA256");
    }

    private static Map<Integer, byte[]> parseKeyring(String value) {
        String candidate = Objects.requireNonNull(value, "Inventory keyring is required").trim();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("Inventory keyring is required");
        }
        Map<Integer, byte[]> keys = new HashMap<>();
        for (String entry : candidate.split(",", -1)) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Inventory keyring entry is invalid");
            }
            int version;
            try {
                version = Integer.parseInt(parts[0]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Inventory key version is invalid", exception);
            }
            if (version < 1 || keys.putIfAbsent(version, decodeStrongKey(parts[1], "encryption key")) != null) {
                throw new IllegalArgumentException("Inventory key version is invalid or duplicated");
            }
        }
        if (keys.size() > MAX_KEY_VERSIONS) {
            throw new IllegalArgumentException("Inventory keyring has too many versions");
        }
        Map<Integer, byte[]> immutable = new HashMap<>();
        keys.forEach((version, key) -> immutable.put(version, key.clone()));
        return Collections.unmodifiableMap(immutable);
    }

    private static byte[] decodeStrongKey(String value, String name) {
        String encoded = Objects.requireNonNull(value, "Inventory " + name + " is required").trim();
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Inventory " + name + " must be valid Base64", exception);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalArgumentException("Inventory " + name + " must decode to 32 bytes");
        }
        Set<Byte> distinctBytes = new HashSet<>();
        for (byte item : decoded) distinctBytes.add(item);
        if (distinctBytes.size() < 12) {
            throw new IllegalArgumentException("Inventory " + name + " is too weak");
        }
        return decoded;
    }
}
