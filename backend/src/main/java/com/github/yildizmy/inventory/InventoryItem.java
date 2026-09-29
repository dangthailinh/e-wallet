package com.github.yildizmy.inventory;

import com.github.yildizmy.catalog.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "inventory_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryItem {

    static final int NONCE_BYTES = 12;
    static final int AUTH_TAG_BYTES = 16;
    static final int CHECKSUM_BYTES = 32;
    static final int MAX_CIPHERTEXT_BYTES = 65_536;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] ciphertext;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] nonce;

    @Getter(AccessLevel.NONE)
    @Column(name = "auth_tag", nullable = false, columnDefinition = "bytea")
    private byte[] authTag;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Getter(AccessLevel.NONE)
    @Column(name = "secret_checksum", nullable = false, columnDefinition = "bytea")
    private byte[] secretChecksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InventoryStatus status;

    @Column(name = "order_item_id", unique = true)
    private UUID orderItemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public InventoryItem(
            UUID id,
            Product product,
            byte[] ciphertext,
            byte[] nonce,
            byte[] authTag,
            int keyVersion,
            byte[] secretChecksum
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.product = Objects.requireNonNull(product, "product is required");
        this.ciphertext = requireBytes(ciphertext, "ciphertext", 1, MAX_CIPHERTEXT_BYTES);
        this.nonce = requireBytes(nonce, "nonce", NONCE_BYTES, NONCE_BYTES);
        this.authTag = requireBytes(authTag, "authTag", AUTH_TAG_BYTES, AUTH_TAG_BYTES);
        if (keyVersion < 1) {
            throw new IllegalArgumentException("keyVersion must be positive");
        }
        this.keyVersion = keyVersion;
        this.secretChecksum = requireBytes(secretChecksum, "secretChecksum", CHECKSUM_BYTES, CHECKSUM_BYTES);
        this.status = InventoryStatus.AVAILABLE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public byte[] getCiphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public byte[] getNonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    public byte[] getAuthTag() {
        return Arrays.copyOf(authTag, authTag.length);
    }

    public byte[] getSecretChecksum() {
        return Arrays.copyOf(secretChecksum, secretChecksum.length);
    }

    public EncryptedPayload encryptedPayload() {
        return new EncryptedPayload(ciphertext, nonce, authTag, keyVersion, secretChecksum);
    }

    public void reserve() {
        requireStatus(InventoryStatus.AVAILABLE);
        status = InventoryStatus.RESERVED;
    }

    public void release() {
        requireStatus(InventoryStatus.RESERVED);
        status = InventoryStatus.AVAILABLE;
    }

    public void sell(UUID orderItemId) {
        if (status != InventoryStatus.AVAILABLE && status != InventoryStatus.RESERVED) {
            throw new IllegalStateException("Only available or reserved inventory can be sold");
        }
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId is required");
        status = InventoryStatus.SOLD;
    }

    public void disable() {
        requireStatus(InventoryStatus.AVAILABLE);
        status = InventoryStatus.DISABLED;
    }

    public void enable() {
        requireStatus(InventoryStatus.DISABLED);
        status = InventoryStatus.AVAILABLE;
    }

    @PreUpdate
    private void touchUpdatedAt() {
        Instant observedAt = Instant.now();
        if (createdAt != null && createdAt.isAfter(observedAt)) {
            observedAt = createdAt;
        }
        if (updatedAt != null && updatedAt.isAfter(observedAt)) {
            observedAt = updatedAt;
        }
        updatedAt = observedAt;
    }

    private void requireStatus(InventoryStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Inventory status must be " + expected);
        }
    }

    private static byte[] requireBytes(byte[] value, String fieldName, int minimum, int maximum) {
        byte[] copy = Arrays.copyOf(Objects.requireNonNull(value, fieldName + " is required"), value.length);
        if (copy.length < minimum || copy.length > maximum) {
            throw new IllegalArgumentException(fieldName + " length is invalid");
        }
        return copy;
    }
}
