package com.github.yildizmy.wallet;

import com.github.yildizmy.domain.entity.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "wallet_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntry {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 8)
    private WalletEntryDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 32)
    private WalletEntryType type;

    @Column(name = "amount_vnd", nullable = false, updatable = false)
    private long amountVnd;

    @Column(name = "business_reference", nullable = false, updatable = false, unique = true, length = 160)
    private String businessReference;

    @Column(name = "business_label", nullable = false, updatable = false, length = 160)
    private String businessLabel;

    @Column(name = "balance_after_vnd", nullable = false, updatable = false)
    private long balanceAfterVnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public WalletEntry(
            Wallet wallet,
            WalletEntryDirection direction,
            WalletEntryType type,
            long amountVnd,
            String businessReference,
            String businessLabel,
            long balanceAfterVnd
    ) {
        this.id = UUID.randomUUID();
        this.wallet = Objects.requireNonNull(wallet, "wallet is required");
        this.direction = Objects.requireNonNull(direction, "direction is required");
        this.type = Objects.requireNonNull(type, "type is required");
        if (!type.supports(direction)) {
            throw new IllegalArgumentException("entry type does not support direction");
        }
        if (amountVnd <= 0) throw new IllegalArgumentException("amountVnd must be positive");
        if (balanceAfterVnd < 0) throw new IllegalArgumentException("balanceAfterVnd must not be negative");
        this.amountVnd = amountVnd;
        this.businessReference = requireText(businessReference, "businessReference");
        this.businessLabel = requireText(businessLabel, "businessLabel");
        this.balanceAfterVnd = balanceAfterVnd;
        this.createdAt = Instant.now();
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (text.isEmpty() || text.length() > 160) {
            throw new IllegalArgumentException(fieldName + " must contain 1 to 160 characters");
        }
        return text;
    }
}
