package com.github.yildizmy.wallet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class WalletLedgerBackfillContractTest {

    @Test
    void backfillIsDeterministicAuditedAndSelfReconciling() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V10__backfill_wallet_ledger.sql"
        )).toLowerCase(Locale.ROOT);

        assertThat(migration).contains(
                "multiple_legacy_wallets",
                "having count(*) > 1",
                "wallet-opening:",
                "on conflict (business_reference) do nothing",
                "wallet ledger reconciliation failed"
        );
        assertThat(migration).doesNotContain("delete from wallet", "update wallet set");
    }
}
