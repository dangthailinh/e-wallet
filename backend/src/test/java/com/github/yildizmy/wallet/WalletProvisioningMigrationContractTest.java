package com.github.yildizmy.wallet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class WalletProvisioningMigrationContractTest {

    @Test
    void migrationOnlyCreatesZeroBalanceWalletsForUsersWithoutOne() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V11__provision_missing_wallets.sql"
        )).toLowerCase(Locale.ROOT);

        assertThat(migration).contains(
                "insert into wallet",
                "not exists",
                "where w.user_id = u.id",
                "'main wallet'",
                "0",
                "'ew' || md5"
        );
        assertThat(migration).doesNotContain("update wallet", "delete from wallet");
    }
}
