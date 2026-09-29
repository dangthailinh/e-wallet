package com.github.yildizmy.order;

import com.github.yildizmy.audit.AuditLog;
import com.github.yildizmy.audit.AuditLogRepository;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.inventory.InventoryCryptoService;
import com.github.yildizmy.inventory.InventoryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecretRevealService {

    private final OrderItemRepository orderItems;
    private final InventoryCryptoService crypto;
    private final AuditLogRepository auditLogs;

    @Transactional
    public SecretRevealResponse reveal(long userId, UUID orderId, UUID itemId, String requestId) {
        OrderItem item = orderItems.findOwnedSoldItemForReveal(userId, orderId, itemId)
                .orElseThrow(() -> new NoSuchElementFoundException("Order item not found"));
        InventoryItem inventory = item.getInventoryItem();
        String secret = crypto.decrypt(
                inventory.getId(), item.getProduct().getId(), inventory.encryptedPayload()
        );
        auditLogs.saveAndFlush(AuditLog.secretRevealed(userId, orderId, itemId, requestId));
        return new SecretRevealResponse(item.getId(), secret);
    }
}
