package com.github.yildizmy.order;

import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.domain.entity.Wallet;
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
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "total_vnd", nullable = false, updatable = false)
    private long totalVnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Order(User user, Wallet wallet, String idempotencyKey, long totalVnd) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user is required");
        this.wallet = Objects.requireNonNull(wallet, "wallet is required");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 100);
        if (totalVnd <= 0) {
            throw new IllegalArgumentException("totalVnd must be positive");
        }
        this.totalVnd = totalVnd;
        this.status = OrderStatus.COMPLETED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    private void touchUpdatedAt() {
        updatedAt = Instant.now();
    }

    private static String requireText(String value, String field, int maxLength) {
        String text = Objects.requireNonNull(value, field + " is required");
        if (text.isEmpty() || text.length() > maxLength || !text.equals(text.trim())) {
            throw new IllegalArgumentException(field + " must contain 1 to " + maxLength + " trimmed characters");
        }
        return text;
    }
}
