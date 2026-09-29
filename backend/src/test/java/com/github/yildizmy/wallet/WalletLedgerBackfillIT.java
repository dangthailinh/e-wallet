package com.github.yildizmy.wallet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WalletLedgerBackfillIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void backfillsEveryFundedWalletReportsConflictsAndCanRunAgain() throws Exception {
        DataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).target("9").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        long conflictedUserId = insertUser(jdbc, "conflicted");
        long zeroBalanceUserId = insertUser(jdbc, "zero-balance");
        long firstWalletId = insertWallet(jdbc, conflictedUserId, "First", 100_000);
        long secondWalletId = insertWallet(jdbc, conflictedUserId, "Second", 250_000);
        long zeroWalletId = insertWallet(jdbc, zeroBalanceUserId, "Empty", 0);

        Flyway.configure().dataSource(dataSource).target("10").load().migrate();

        assertThat(jdbc.queryForObject("select count(*) from wallet", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from wallet_entries", Integer.class)).isEqualTo(2);
        assertThat(openingEntries(jdbc)).containsExactlyInAnyOrder(
                Map.of("wallet_id", firstWalletId, "amount_vnd", 100_000L, "balance_after_vnd", 100_000L),
                Map.of("wallet_id", secondWalletId, "amount_vnd", 250_000L, "balance_after_vnd", 250_000L)
        );
        assertThat(jdbc.queryForObject(
                "select count(*) from wallet_entries where wallet_id = ?", Integer.class, zeroWalletId
        )).isZero();
        assertThat(jdbc.queryForMap(
                "select issue_code, wallet_count from wallet_migration_audits where user_id = ?",
                conflictedUserId
        )).containsEntry("issue_code", "MULTIPLE_LEGACY_WALLETS").containsEntry("wallet_count", 2);
        assertBalancesReconcile(jdbc);

        long lateUserId = insertUser(jdbc, "late-wallet");
        long lateWalletId = insertWallet(jdbc, lateUserId, "Late", 75_000);

        String migrationSql = new ClassPathResource("db/migration/V10__backfill_wallet_ledger.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(migrationSql);
            connection.commit();
        }

        assertThat(jdbc.queryForObject("select count(*) from wallet_entries", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForMap(
                "select amount_vnd, balance_after_vnd from wallet_entries where wallet_id = ?", lateWalletId
        )).containsEntry("amount_vnd", 75_000L).containsEntry("balance_after_vnd", 75_000L);
        assertThat(jdbc.queryForObject("select count(*) from wallet_migration_audits", Integer.class)).isEqualTo(1);
        assertBalancesReconcile(jdbc);
    }

    private DataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long insertUser(JdbcTemplate jdbc, String username) {
        long id = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Ledger', 'Test', ?, ?, 'not-used')",
                id,
                username,
                username + "@example.test"
        );
        return id;
    }

    private long insertWallet(JdbcTemplate jdbc, long userId, String name, long balance) {
        long id = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) values (?, ?, ?, ?, ?, 0)",
                id,
                "VN" + String.format("%032d", id),
                name,
                balance,
                userId
        );
        return id;
    }

    private List<Map<String, Object>> openingEntries(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "select wallet_id, amount_vnd, balance_after_vnd from wallet_entries order by wallet_id"
        );
    }

    private void assertBalancesReconcile(JdbcTemplate jdbc) {
        Integer mismatches = jdbc.queryForObject(
                "select count(*) from wallet w left join ("
                        + "select wallet_id, sum(case when direction = 'CREDIT' then amount_vnd else -amount_vnd end) total "
                        + "from wallet_entries group by wallet_id) e on e.wallet_id = w.id "
                        + "where w.balance <> coalesce(e.total, 0)",
                Integer.class
        );
        assertThat(mismatches).isZero();
    }
}
