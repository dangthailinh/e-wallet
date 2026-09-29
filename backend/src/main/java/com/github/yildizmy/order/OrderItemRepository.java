package com.github.yildizmy.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    Optional<OrderItem> findFirstByOrderIdOrderById(UUID orderId);

    List<OrderItem> findAllByOrderIdOrderById(UUID orderId);

    @Query("""
            select item
            from OrderItem item
            join fetch item.inventoryItem inventory
            join fetch inventory.product product
            where item.id = :itemId
              and item.order.id = :orderId
              and item.order.user.id = :userId
              and item.order.status = com.github.yildizmy.order.OrderStatus.COMPLETED
              and inventory.status = com.github.yildizmy.inventory.InventoryStatus.SOLD
              and inventory.orderItemId = item.id
            """)
    Optional<OrderItem> findOwnedSoldItemForReveal(
            @Param("userId") long userId,
            @Param("orderId") UUID orderId,
            @Param("itemId") UUID itemId
    );
}
