package com.github.yildizmy.inventory;

import com.github.yildizmy.catalog.Product;
import com.github.yildizmy.catalog.ProductRepository;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class InventoryImportService {

    static final int MAX_BATCH_SIZE = 100;
    static final int MAX_SECRET_BYTES = 4_096;

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryCryptoService cryptoService;

    public InventoryImportService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryCryptoService cryptoService
    ) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.cryptoService = cryptoService;
    }

    public InventoryImportResponse importBatch(UUID productId, InventoryImportRequest request, String actor) {
        UUID requiredProductId = Objects.requireNonNull(productId, "productId is required");
        String safeActor = requireActor(actor);
        List<String> secrets = requireBatch(request);
        Product product = productRepository.findById(requiredProductId)
                .orElseThrow(() -> new NoSuchElementFoundException("Product not found."));
        List<InventoryImportRowResult> results = new ArrayList<>(secrets.size());
        List<InventoryItem> accepted = new ArrayList<>();
        Set<String> checksumsInBatch = new HashSet<>();

        for (int index = 0; index < secrets.size(); index++) {
            int row = index + 1;
            String secret = secrets.get(index);
            if (secret == null || secret.isBlank()) {
                results.add(InventoryImportRowResult.rejected(row, "BLANK_SECRET", "Secret must not be blank."));
                continue;
            }
            String normalized = secret.strip();
            if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_SECRET_BYTES) {
                results.add(InventoryImportRowResult.rejected(
                        row,
                        "SECRET_TOO_LARGE",
                        "Secret exceeds the maximum encoded size."
                ));
                continue;
            }

            UUID itemId = UUID.randomUUID();
            EncryptedPayload payload = cryptoService.encrypt(itemId, product.getId(), normalized);
            byte[] checksum = payload.secretChecksum();
            String checksumKey = Base64.getEncoder().encodeToString(checksum);
            boolean duplicate = !checksumsInBatch.add(checksumKey)
                    || inventoryRepository.existsByProductIdAndSecretChecksum(product.getId(), checksum);
            if (duplicate) {
                results.add(InventoryImportRowResult.rejected(
                        row,
                        "DUPLICATE_SECRET",
                        "Secret already exists for this product."
                ));
                continue;
            }

            accepted.add(new InventoryItem(
                    itemId,
                    product,
                    payload.ciphertext(),
                    payload.nonce(),
                    payload.authTag(),
                    payload.keyVersion(),
                    payload.secretChecksum()
            ));
            results.add(InventoryImportRowResult.imported(row, itemId));
        }

        if (!accepted.isEmpty()) inventoryRepository.saveAll(accepted);
        log.info(
                "securityAudit action=INVENTORY_IMPORTED actor={} productId={} requested={} imported={} rejected={}",
                safeActor,
                product.getId(),
                secrets.size(),
                accepted.size(),
                secrets.size() - accepted.size()
        );
        return new InventoryImportResponse(secrets.size(), accepted.size(), results);
    }

    private static List<String> requireBatch(InventoryImportRequest request) {
        InventoryImportRequest command = Objects.requireNonNull(request, "request is required");
        List<String> secrets = Objects.requireNonNull(command.secrets(), "secrets are required");
        if (secrets.isEmpty() || secrets.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Inventory batch must contain 1 to " + MAX_BATCH_SIZE + " rows");
        }
        return secrets;
    }

    private static String requireActor(String actor) {
        String value = Objects.requireNonNull(actor, "actor is required").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("actor is required");
        return value.replace('\r', '_').replace('\n', '_').replace('\t', '_');
    }
}
