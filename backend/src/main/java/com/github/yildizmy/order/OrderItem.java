package com.github.yildizmy.order;

import com.github.yildizmy.catalog.Product;
import com.github.yildizmy.inventory.InventoryItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false, updatable = false, unique = true)
    private InventoryItem inventoryItem;

    @Column(name = "product_name", nullable = false, updatable = false, length = 160)
    private String productName;

    @Column(name = "unit_price_vnd", nullable = false, updatable = false)
    private long unitPriceVnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderItem(Order order, Product product, InventoryItem inventoryItem) {
        this.id = UUID.randomUUID();
        this.order = Objects.requireNonNull(order, "order is required");
        this.product = Objects.requireNonNull(product, "product is required");
        this.inventoryItem = Objects.requireNonNull(inventoryItem, "inventoryItem is required");
        if (!product.getId().equals(inventoryItem.getProduct().getId())) {
            throw new IllegalArgumentException("inventory item belongs to another product");
        }
        this.productName = product.getName();
        this.unitPriceVnd = product.getPriceVnd();
        this.createdAt = Instant.now();
    }
}
