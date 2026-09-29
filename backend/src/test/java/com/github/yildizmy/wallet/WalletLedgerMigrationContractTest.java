package com.github.yildizmy.wallet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class WalletLedgerMigrationContractTest {

    @Test
    void ledgerMigrationConstrainsMoneyReferencesAndMutations() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V9__add_wallet_ledger.sql"
        )).toLowerCase(Locale.ROOT);

        assertThat(migration).contains(
                "ck_wallet_balance_non_negative",
                "ck_wallet_entry_amount_positive",
                "uc_wallet_entry_business_reference",
                "ck_wallet_entry_type_direction",
                "trg_wallet_entry_no_update",
                "trg_wallet_entry_no_delete"
        );
    }
}
