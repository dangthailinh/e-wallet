package com.github.yildizmy.inventory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class InventoryCryptoService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String CHECKSUM_ALGORITHM = "HmacSHA256";
    private static final int GCM_TAG_BITS = 128;

    private final InventoryCryptoProperties properties;
    private final SecureRandom secureRandom;

    public InventoryCryptoService(InventoryCryptoProperties properties) {
        this(properties, new SecureRandom());
    }

    InventoryCryptoService(InventoryCryptoProperties properties, SecureRandom secureRandom) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom is required");
    }

    public EncryptedPayload encrypt(UUID inventoryItemId, UUID productId, String plaintext) {
        Objects.requireNonNull(inventoryItemId, "inventoryItemId is required");
        Objects.requireNonNull(productId, "productId is required");
        byte[] cleartext = Objects.requireNonNull(plaintext, "plaintext is required").getBytes(StandardCharsets.UTF_8);
        if (cleartext.length < 1 || cleartext.length > InventoryItem.MAX_CIPHERTEXT_BYTES) {
            Arrays.fill(cleartext, (byte) 0);
            throw new IllegalArgumentException("Inventory plaintext length is invalid");
        }

        int keyVersion = properties.activeKeyVersion();
        byte[] nonce = new byte[InventoryItem.NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, properties.encryptionKey(keyVersion), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad(inventoryItemId, productId, keyVersion));
            byte[] cipherWithTag = cipher.doFinal(cleartext);
            int tagOffset = cipherWithTag.length - InventoryItem.AUTH_TAG_BYTES;
            byte[] ciphertext = Arrays.copyOfRange(cipherWithTag, 0, tagOffset);
            byte[] authTag = Arrays.copyOfRange(cipherWithTag, tagOffset, cipherWithTag.length);
            byte[] checksum = checksum(productId, cleartext);
            Arrays.fill(cipherWithTag, (byte) 0);
            return new EncryptedPayload(ciphertext, nonce, authTag, keyVersion, checksum);
        } catch (GeneralSecurityException exception) {
            throw new InventoryCryptoException("Unable to encrypt inventory payload", exception);
        } finally {
            Arrays.fill(cleartext, (byte) 0);
        }
    }

    public String decrypt(UUID inventoryItemId, UUID productId, EncryptedPayload payload) {
        Objects.requireNonNull(inventoryItemId, "inventoryItemId is required");
        Objects.requireNonNull(productId, "productId is required");
        EncryptedPayload envelope = Objects.requireNonNull(payload, "payload is required");
        byte[] ciphertext = envelope.ciphertext();
        byte[] authTag = envelope.authTag();
        byte[] combined = ByteBuffer.allocate(ciphertext.length + authTag.length)
                .put(ciphertext)
                .put(authTag)
                .array();
        byte[] cleartext = null;
        byte[] actualChecksum = null;
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    properties.encryptionKey(envelope.keyVersion()),
                    new GCMParameterSpec(GCM_TAG_BITS, envelope.nonce())
            );
            cipher.updateAAD(aad(inventoryItemId, productId, envelope.keyVersion()));
            cleartext = cipher.doFinal(combined);
            actualChecksum = checksum(productId, cleartext);
            if (!MessageDigest.isEqual(actualChecksum, envelope.secretChecksum())) {
                throw new GeneralSecurityException("Inventory checksum mismatch");
            }
            return new String(cleartext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | InventoryCryptoException exception) {
            throw new InventoryCryptoException("Unable to decrypt inventory payload", exception);
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(authTag, (byte) 0);
            Arrays.fill(combined, (byte) 0);
            if (actualChecksum != null) Arrays.fill(actualChecksum, (byte) 0);
            if (cleartext != null) Arrays.fill(cleartext, (byte) 0);
        }
    }

    private byte[] checksum(UUID productId, byte[] cleartext) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(CHECKSUM_ALGORITHM);
        mac.init(properties.checksumKey());
        mac.update(uuidBytes(productId));
        return mac.doFinal(cleartext);
    }

    private static byte[] aad(UUID inventoryItemId, UUID productId, int keyVersion) {
        return ByteBuffer.allocate(36)
                .putLong(inventoryItemId.getMostSignificantBits())
                .putLong(inventoryItemId.getLeastSignificantBits())
                .putLong(productId.getMostSignificantBits())
                .putLong(productId.getLeastSignificantBits())
                .putInt(keyVersion)
                .array();
    }

    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
