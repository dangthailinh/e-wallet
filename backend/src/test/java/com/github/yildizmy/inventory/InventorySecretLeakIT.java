package com.github.yildizmy.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yildizmy.catalog.CatalogStatus;
import com.github.yildizmy.catalog.Category;
import com.github.yildizmy.catalog.Product;
import com.github.yildizmy.catalog.ProductRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InventorySecretLeakIT {

    private static final String KNOWN_SECRET = "KNOWN_SECRET_FIXTURE_7c8f2a6e_do_not_leak";
    private static final String KEY = "WwbszMX8gi/4G820UlcrpKMm3XNiOwfEJ4wnjg798ik=";
    private static final String CHECKSUM_KEY = "D6RhTtwfDR8AttrHb4Tny+mwBxC91zkFrFGnDgnBpL8=";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryCryptoService cryptoService;
    private Product product;

    @BeforeEach
    void setUp() {
        cryptoService = new InventoryCryptoService(new InventoryCryptoProperties(1, "1:" + KEY, CHECKSUM_KEY));
        Category category = new Category("Leak test", "leak-test", 1, CatalogStatus.ACTIVE);
        product = new Product(
                category,
                "Leak test product",
                "leak-test-product",
                "Encrypted test inventory.",
                10_000,
                CatalogStatus.ACTIVE,
                null
        );
    }

    @Test
    void fixtureIsAbsentFromStoredFieldsApiJsonErrorsAndLogs(CapturedOutput output) throws Exception {
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(inventoryRepository.existsByProductIdAndSecretChecksum(eq(product.getId()), any(byte[].class)))
                .thenReturn(false);
        when(inventoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        InventoryImportService importService = new InventoryImportService(
                productRepository,
                inventoryRepository,
                cryptoService
        );

        InventoryImportResponse response = importService.importBatch(
                product.getId(),
                new InventoryImportRequest(List.of(KNOWN_SECRET)),
                "security-test"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<InventoryItem>> captor = ArgumentCaptor.forClass(Iterable.class);
        org.mockito.Mockito.verify(inventoryRepository).saveAll(captor.capture());
        InventoryItem stored = captor.getValue().iterator().next();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String adminJson = mapper.writeValueAsString(AdminInventoryItemResponse.from(stored));
        String importJson = mapper.writeValueAsString(response);
        String encryptedFields = new String(stored.getCiphertext(), StandardCharsets.ISO_8859_1)
                + new String(stored.getNonce(), StandardCharsets.ISO_8859_1)
                + new String(stored.getAuthTag(), StandardCharsets.ISO_8859_1)
                + new String(stored.getSecretChecksum(), StandardCharsets.ISO_8859_1);

        assertThat(encryptedFields).doesNotContain(KNOWN_SECRET);
        assertThat(adminJson).doesNotContain(KNOWN_SECRET, "ciphertext", "authTag", "nonce");
        assertThat(importJson).doesNotContain(KNOWN_SECRET);
        assertThat(output).doesNotContain(KNOWN_SECRET);
        assertThat(cryptoService.decrypt(stored.getId(), product.getId(), stored.encryptedPayload()))
                .isEqualTo(KNOWN_SECRET);

        byte[] changedTag = stored.getAuthTag();
        changedTag[0] ^= 1;
        EncryptedPayload tampered = new EncryptedPayload(
                stored.getCiphertext(),
                stored.getNonce(),
                changedTag,
                stored.getKeyVersion(),
                stored.getSecretChecksum()
        );
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> cryptoService.decrypt(stored.getId(), product.getId(), tampered)
        )).hasMessageNotContaining(KNOWN_SECRET);
    }

    @Test
    void postgresDumpContainsCiphertextButNotFixture() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker Engine is unavailable");
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            UUID categoryId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            EncryptedPayload payload = cryptoService.encrypt(itemId, productId, KNOWN_SECRET);
            insertFixture(postgres, categoryId, productId, itemId, payload);

            org.testcontainers.containers.Container.ExecResult dump = postgres.execInContainer(
                    "pg_dump",
                    "--no-owner",
                    "--no-privileges",
                    "-U",
                    postgres.getUsername(),
                    "-d",
                    postgres.getDatabaseName()
            );

            assertThat(dump.getExitCode()).isZero();
            assertThat(dump.getStdout()).contains("inventory_items").doesNotContain(KNOWN_SECRET);
        }
    }

    private void insertFixture(
            PostgreSQLContainer<?> postgres,
            UUID categoryId,
            UUID productId,
            UUID itemId,
            EncryptedPayload payload
    ) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into categories "
                            + "(id, name, slug, display_order, status, created_at, updated_at, version) "
                            + "values (?, 'Leak test', 'leak-test', 1, 'ACTIVE', now(), now(), 0)"
            )) {
                statement.setObject(1, categoryId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into products "
                            + "(id, category_id, name, slug, description, price_vnd, status, created_at, updated_at, version) "
                            + "values (?, ?, 'Leak test product', 'leak-test-product', 'Encrypted.', 10000, "
                            + "'ACTIVE', now(), now(), 0)"
            )) {
                statement.setObject(1, productId);
                statement.setObject(2, categoryId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into inventory_items "
                            + "(id, product_id, ciphertext, nonce, auth_tag, key_version, secret_checksum, status, "
                            + "created_at, updated_at, version) values (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', now(), now(), 0)"
            )) {
                statement.setObject(1, itemId);
                statement.setObject(2, productId);
                statement.setBytes(3, payload.ciphertext());
                statement.setBytes(4, payload.nonce());
                statement.setBytes(5, payload.authTag());
                statement.setInt(6, payload.keyVersion());
                statement.setBytes(7, payload.secretChecksum());
                statement.executeUpdate();
            }
        }
    }
}
