package com.github.yildizmy.inventory;

import com.github.yildizmy.catalog.CatalogStatus;
import com.github.yildizmy.catalog.Category;
import com.github.yildizmy.catalog.Product;
import com.github.yildizmy.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InventoryImportServiceIT {

    private static final String KEY = "WwbszMX8gi/4G820UlcrpKMm3XNiOwfEJ4wnjg798ik=";
    private static final String CHECKSUM_KEY = "D6RhTtwfDR8AttrHb4Tny+mwBxC91zkFrFGnDgnBpL8=";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryCryptoService cryptoService;
    private InventoryImportService service;
    private Product product;

    @BeforeEach
    void setUp() {
        cryptoService = new InventoryCryptoService(new InventoryCryptoProperties(1, "1:" + KEY, CHECKSUM_KEY));
        service = new InventoryImportService(productRepository, inventoryRepository, cryptoService);
        Category category = new Category("API", "api", 1, CatalogStatus.ACTIVE);
        product = new Product(
                category,
                "API credits",
                "api-credits",
                "Encrypted API credentials.",
                50_000L,
                CatalogStatus.ACTIVE,
                null
        );
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    }

    @Test
    void validatesDeduplicatesEncryptsAndNeverLogsPayload(CapturedOutput output) {
        String plaintext = "plain-value-that-must-not-be-logged";
        String oversized = "x".repeat(4_097);
        when(inventoryRepository.existsByProductIdAndSecretChecksum(eq(product.getId()), any(byte[].class)))
                .thenReturn(false);
        when(inventoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryImportResponse response = service.importBatch(
                product.getId(),
                new InventoryImportRequest(List.of("  " + plaintext + "  ", plaintext, " ", oversized)),
                "admin"
        );

        assertThat(response.requested()).isEqualTo(4);
        assertThat(response.imported()).isEqualTo(1);
        assertThat(response.results()).extracting(InventoryImportRowResult::code)
                .containsExactly(null, "DUPLICATE_SECRET", "BLANK_SECRET", "SECRET_TOO_LARGE");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<InventoryItem>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(inventoryRepository).saveAll(captor.capture());
        InventoryItem stored = captor.getValue().iterator().next();
        assertThat(stored.getCiphertext()).isNotEqualTo(plaintext.getBytes());
        assertThat(cryptoService.decrypt(stored.getId(), product.getId(), stored.encryptedPayload()))
                .isEqualTo(plaintext);
        assertThat(output).doesNotContain(plaintext);
    }

    @Test
    void reportsExistingChecksumAsASanitizedDuplicate() {
        when(inventoryRepository.existsByProductIdAndSecretChecksum(eq(product.getId()), any(byte[].class)))
                .thenReturn(true);

        InventoryImportResponse response = service.importBatch(
                product.getId(),
                new InventoryImportRequest(List.of("existing-secret")),
                "admin"
        );

        assertThat(response.imported()).isZero();
        assertThat(response.results().getFirst().code()).isEqualTo("DUPLICATE_SECRET");
        assertThat(response.results().getFirst().detail()).doesNotContain("existing-secret");
        verify(inventoryRepository, never()).saveAll(any());
    }
}
