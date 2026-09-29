package com.github.yildizmy.payment;

import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.domain.entity.Wallet;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepositModelTest {

    @Test
    void newDepositStartsPendingAndOnlySupportsVnd() {
        Deposit deposit = new Deposit(
                new User(), new Wallet(), DepositChannel.BANK, "BANK_TEST",
                "DEP_12345678", 50_000, Instant.now().plusSeconds(3600)
        );

        assertThat(deposit.getId()).isNotNull();
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PENDING);
        assertThat(deposit.getCurrency()).isEqualTo("VND");
        assertThat(deposit.getAmountVnd()).isEqualTo(50_000);
        assertThat(deposit.getPaidAt()).isNull();
        assertThat(deposit.isNeedsReconciliationReview()).isFalse();
        assertThatThrownBy(deposit::flagForReconciliationReview)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidPaymentTermsBeforePersistence() {
        User user = new User();
        Wallet wallet = new Wallet();
        Instant expiry = Instant.now().plusSeconds(3600);

        assertThatThrownBy(() -> new Deposit(user, wallet, DepositChannel.MOMO,
                "BANK_TEST", "DEP_12345678", 50_000, expiry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Deposit(user, wallet, DepositChannel.BANK,
                "BANK_TEST", "DEP_12345678", 0, expiry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Deposit(user, wallet, DepositChannel.BANK,
                "BANK_TEST", "bad", 50_000, expiry))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webhookStoresOnlyDefensivelyCopiedHashAndRejectsProviderMismatch() {
        Deposit deposit = new Deposit(
                new User(), new Wallet(), DepositChannel.BANK, "BANK_TEST",
                "DEP_12345678", 50_000, Instant.now().plusSeconds(3600)
        );
        byte[] hash = new byte[32];
        Arrays.fill(hash, (byte) 7);
        WebhookEvent event = new WebhookEvent(deposit, "BANK_TEST", "event-1", "tx-1", hash);
        hash[0] = 0;
        assertThat(event.getRequestHash()[0]).isEqualTo((byte) 7);
        byte[] leakedCopy = event.getRequestHash();
        leakedCopy[1] = 0;
        assertThat(event.getRequestHash()[1]).isEqualTo((byte) 7);
        assertThat(event.getResult()).isEqualTo(WebhookEventResult.RECEIVED);

        assertThatThrownBy(() -> new WebhookEvent(deposit, "MOMO", "event-2", null, hash))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
