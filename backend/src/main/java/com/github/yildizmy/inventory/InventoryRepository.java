package com.github.yildizmy.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

    boolean existsByProductIdAndSecretChecksum(UUID productId, byte[] secretChecksum);

    List<InventoryItem> findAllByProductIdOrderByCreatedAtDesc(UUID productId);

    @Query(value = """
            SELECT *
            FROM inventory_items
            WHERE product_id = :productId
              AND status = 'AVAILABLE'
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<InventoryItem> lockNextAvailable(@Param("productId") UUID productId);
}
