package com.github.yildizmy.inventory;

import com.github.yildizmy.catalog.CatalogStatus;
import com.github.yildizmy.catalog.Category;
import com.github.yildizmy.catalog.Product;
import com.github.yildizmy.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAdminServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryAdminService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryAdminService(productRepository, inventoryRepository);
        Category category = new Category("API", "api", 1, CatalogStatus.ACTIVE);
        product = new Product(category, "API", "api-item", "Encrypted item.", 10_000, CatalogStatus.ACTIVE, null);
    }

    @Test
    void listsOnlyMaskedMetadataAndCounts() {
        InventoryItem item = item();
        when(productRepository.existsById(product.getId())).thenReturn(true);
        when(inventoryRepository.findAllByProductIdOrderByCreatedAtDesc(product.getId()))
                .thenReturn(List.of(item));

        AdminInventoryListResponse response = service.list(product.getId());

        assertThat(response.counts()).containsEntry(InventoryStatus.AVAILABLE, 1L);
        assertThat(response.items()).singleElement().satisfies(row -> {
            assertThat(row.checksumSuffix()).isEqualTo("040404");
            assertThat(row.keyVersion()).isEqualTo(1);
        });
        assertThat(AdminInventoryItemResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("ciphertext", "nonce", "authTag", "secretChecksum");
    }

    @Test
    void allowsOnlyAvailableDisabledAdminTransitions() {
        InventoryItem item = item();
        when(inventoryRepository.findById(item.getId())).thenReturn(Optional.of(item));

        AdminInventoryItemResponse disabled = service.changeStatus(item.getId(), InventoryStatus.DISABLED, "admin");

        assertThat(disabled.status()).isEqualTo(InventoryStatus.DISABLED);
        verify(inventoryRepository).flush();

        assertThatThrownBy(() -> service.changeStatus(item.getId(), InventoryStatus.SOLD, "admin"))
                .isInstanceOf(InventoryStateConflictException.class);
    }

    private InventoryItem item() {
        return new InventoryItem(
                UUID.randomUUID(),
                product,
                new byte[]{1},
                bytes(12, (byte) 2),
                bytes(16, (byte) 3),
                1,
                bytes(32, (byte) 4)
        );
    }

    private static byte[] bytes(int size, byte value) {
        byte[] result = new byte[size];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
