package com.github.yildizmy.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryCryptoServiceTest {

    private static final String KEY_V1 = "WwbszMX8gi/4G820UlcrpKMm3XNiOwfEJ4wnjg798ik=";
    private static final String KEY_V2 = "KpX49U1Df+EMgWkIoxfThu12jEEPhv1PA6rZypuLAjQ=";
    private static final String CHECKSUM_KEY = "D6RhTtwfDR8AttrHb4Tny+mwBxC91zkFrFGnDgnBpL8=";

    @Test
    void repeatedEncryptionUsesUniqueNoncesAndRoundTrips() {
        InventoryCryptoService service = service(1, "1:" + KEY_V1);
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        EncryptedPayload first = service.encrypt(itemId, productId, "customer-secret-value");
        EncryptedPayload second = service.encrypt(itemId, productId, "customer-secret-value");

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.authTag()).hasSize(16);
        assertThat(first.secretChecksum()).hasSize(32);
        assertThat(service.decrypt(itemId, productId, first)).isEqualTo("customer-secret-value");
        assertThat(service.decrypt(itemId, productId, second)).isEqualTo("customer-secret-value");
    }

    @Test
    void wrongTagKeyOrAadFailsClosedWithoutLeakingPlaintext() {
        InventoryCryptoService service = service(1, "1:" + KEY_V1);
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String plaintext = "must-never-appear-in-errors";
        EncryptedPayload encrypted = service.encrypt(itemId, productId, plaintext);
        byte[] changedTag = encrypted.authTag();
        changedTag[0] ^= 1;
        EncryptedPayload tampered = new EncryptedPayload(
                encrypted.ciphertext(),
                encrypted.nonce(),
                changedTag,
                encrypted.keyVersion(),
                encrypted.secretChecksum()
        );

        assertClosed(() -> service.decrypt(itemId, productId, tampered), plaintext);
        assertClosed(() -> service.decrypt(UUID.randomUUID(), productId, encrypted), plaintext);
        assertClosed(() -> service.decrypt(itemId, UUID.randomUUID(), encrypted), plaintext);

        InventoryCryptoService wrongKey = service(1, "1:" + KEY_V2);
        assertClosed(() -> wrongKey.decrypt(itemId, productId, encrypted), plaintext);
    }

    @Test
    void keyringDecryptsOldPayloadAfterActiveKeyRotation() {
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        EncryptedPayload oldPayload = service(1, "1:" + KEY_V1 + ",2:" + KEY_V2)
                .encrypt(itemId, productId, "rotation-safe-secret");
        InventoryCryptoService rotated = service(2, "1:" + KEY_V1 + ",2:" + KEY_V2);

        EncryptedPayload newPayload = rotated.encrypt(itemId, productId, "new-secret");

        assertThat(oldPayload.keyVersion()).isEqualTo(1);
        assertThat(newPayload.keyVersion()).isEqualTo(2);
        assertThat(rotated.decrypt(itemId, productId, oldPayload)).isEqualTo("rotation-safe-secret");
    }

    @Test
    void missingOrWeakConfigurationPreventsContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(InventoryCryptoConfiguration.class)
                .run(context -> assertThat(context).hasFailed());

        String weak = Base64.getEncoder().encodeToString(new byte[32]);
        new ApplicationContextRunner()
                .withUserConfiguration(InventoryCryptoConfiguration.class)
                .withPropertyValues(
                        "app.inventory.crypto.active-key-version=1",
                        "app.inventory.crypto.keyring=1:" + weak,
                        "app.inventory.crypto.checksum-key=" + weak
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void validConfigurationCreatesTheCryptoService() {
        new ApplicationContextRunner()
                .withUserConfiguration(InventoryCryptoConfiguration.class)
                .withPropertyValues(
                        "app.inventory.crypto.active-key-version=2",
                        "app.inventory.crypto.keyring=1:" + KEY_V1 + ",2:" + KEY_V2,
                        "app.inventory.crypto.checksum-key=" + CHECKSUM_KEY
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(InventoryCryptoService.class);
                });
    }

    @Test
    void payloadAccessorsReturnDefensiveCopies() {
        EncryptedPayload payload = service(1, "1:" + KEY_V1)
                .encrypt(UUID.randomUUID(), UUID.randomUUID(), "copy-safe");
        byte[] nonce = payload.nonce();
        byte original = nonce[0];
        nonce[0] ^= 1;

        assertThat(payload.nonce()[0]).isEqualTo(original);
    }

    private InventoryCryptoService service(int activeVersion, String keyring) {
        return new InventoryCryptoService(new InventoryCryptoProperties(activeVersion, keyring, CHECKSUM_KEY));
    }

    private void assertClosed(ThrowingCall call, String plaintext) {
        assertThatThrownBy(call::run)
                .isInstanceOf(InventoryCryptoException.class)
                .hasMessageNotContaining(plaintext)
                .satisfies(error -> assertThat(Arrays.toString(error.getStackTrace())).doesNotContain(plaintext));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
