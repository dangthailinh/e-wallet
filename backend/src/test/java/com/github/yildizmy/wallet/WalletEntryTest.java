package com.github.yildizmy.wallet;

import com.github.yildizmy.domain.entity.Wallet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletEntryTest {

    @Test
    void createsAnImmutableVndEntry() {
        WalletEntry entry = new WalletEntry(
                new Wallet(),
                WalletEntryDirection.CREDIT,
                WalletEntryType.DEPOSIT,
                100_000,
                "momo:transaction-1",
                "Nạp tiền MoMo",
                150_000
        );

        assertThat(entry.getAmountVnd()).isEqualTo(100_000);
        assertThat(entry.getBalanceAfterVnd()).isEqualTo(150_000);
        assertThat(entry.getId()).isNotNull();
    }

    @Test
    void rejectsInvalidAmountBalanceAndDirection() {
        assertThatThrownBy(() -> new WalletEntry(
                new Wallet(), WalletEntryDirection.DEBIT, WalletEntryType.DEPOSIT,
                1, "ref", "label", 0
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalletEntry(
                new Wallet(), WalletEntryDirection.CREDIT, WalletEntryType.DEPOSIT,
                0, "ref", "label", 0
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WalletEntry(
                new Wallet(), WalletEntryDirection.DEBIT, WalletEntryType.PURCHASE,
                1, "ref", "label", -1
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
