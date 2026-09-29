package com.github.yildizmy.inventory;

import com.github.yildizmy.catalog.ProductRepository;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class InventoryAdminService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryAdminService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public AdminInventoryListResponse list(UUID productId) {
        UUID requiredProductId = Objects.requireNonNull(productId, "productId is required");
        if (!productRepository.existsById(requiredProductId)) {
            throw new NoSuchElementFoundException("Product not found.");
        }
        List<AdminInventoryItemResponse> items = inventoryRepository
                .findAllByProductIdOrderByCreatedAtDesc(requiredProductId)
                .stream()
                .map(AdminInventoryItemResponse::from)
                .toList();
        Map<InventoryStatus, Long> counts = new EnumMap<>(InventoryStatus.class);
        for (InventoryStatus status : InventoryStatus.values()) counts.put(status, 0L);
        items.forEach(item -> counts.compute(item.status(), (ignored, count) -> count + 1));
        return new AdminInventoryListResponse(requiredProductId, counts, items);
    }

    @Transactional
    public AdminInventoryItemResponse changeStatus(UUID itemId, InventoryStatus target, String actor) {
        String safeActor = requireActor(actor);
        InventoryItem item = inventoryRepository.findById(Objects.requireNonNull(itemId, "itemId is required"))
                .orElseThrow(() -> new NoSuchElementFoundException("Inventory item not found."));
        InventoryStatus requiredTarget = Objects.requireNonNull(target, "status is required");
        try {
            if (requiredTarget == InventoryStatus.DISABLED) {
                item.disable();
            } else if (requiredTarget == InventoryStatus.AVAILABLE) {
                item.enable();
            } else {
                throw new InventoryStateConflictException("Only AVAILABLE and DISABLED can be selected by an admin.");
            }
        } catch (IllegalStateException exception) {
            throw new InventoryStateConflictException("Inventory item cannot change to the requested status.");
        }
        inventoryRepository.flush();
        log.info(
                "securityAudit action=INVENTORY_STATUS_CHANGED actor={} inventoryItemId={} status={}",
                safeActor,
                item.getId(),
                item.getStatus()
        );
        return AdminInventoryItemResponse.from(item);
    }

    private static String requireActor(String actor) {
        String value = Objects.requireNonNull(actor, "actor is required").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("actor is required");
        return value.replace('\r', '_').replace('\n', '_').replace('\t', '_');
    }
}
