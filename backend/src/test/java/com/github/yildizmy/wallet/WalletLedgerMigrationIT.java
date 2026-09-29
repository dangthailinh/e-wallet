package com.github.yildizmy.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class WalletLedgerMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationDefinesLedgerIntegrityAndImmutability() {
        Boolean migrationApplied = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '9'",
                Boolean.class
        );
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint where conrelid in ('wallet'::regclass, 'wallet_entries'::regclass)",
                String.class
        );
        List<String> triggers = jdbcTemplate.queryForList(
                "select tgname from pg_trigger where tgrelid = 'wallet_entries'::regclass and not tgisinternal",
                String.class
        );
        Integer balanceScale = jdbcTemplate.queryForObject(
                "select numeric_scale from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'wallet' and column_name = 'balance'",
                Integer.class
        );

        assertThat(migrationApplied).isTrue();
        assertThat(balanceScale).isZero();
        assertThat(constraints).contains(
                "ck_wallet_balance_non_negative",
                "uc_wallet_entry_business_reference",
                "ck_wallet_entry_amount_positive",
                "ck_wallet_entry_type_direction"
        );
        assertThat(triggers).contains("trg_wallet_entry_no_update", "trg_wallet_entry_no_delete");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRejectsInvalidOrMutatedLedgerData() {
        long walletId = insertWallet(100_000);
        UUID entryId = UUID.randomUUID();
        insertEntry(entryId, walletId, "CREDIT", "DEPOSIT", 100_000, "deposit:test-1", 100_000);

        assertThatThrownBy(() -> insertEntry(
                UUID.randomUUID(), walletId, "CREDIT", "DEPOSIT", 1, "deposit:test-1", 100_001
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertEntry(
                UUID.randomUUID(), walletId, "DEBIT", "DEPOSIT", 1, "deposit:test-2", 99_999
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertEntry(
                UUID.randomUUID(), walletId, "DEBIT", "PURCHASE", 0, "purchase:test-1", 100_000
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update wallet_entries set business_label = 'Changed' where id = ?", entryId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from wallet_entries where id = ?", entryId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update wallet set balance = -1 where id = ?", walletId
        )).isInstanceOf(DataAccessException.class);
    }

    private long insertWallet(long balance) {
        long userId = jdbcTemplate.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbcTemplate.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbcTemplate.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Ledger', 'Test', ?, ?, 'not-used')",
                userId,
                "ledger" + userId,
                "ledger" + userId + "@example.test"
        );
        jdbcTemplate.update(
                "insert into wallet (id, iban, name, balance, user_id, version) values (?, ?, 'Primary', ?, ?, 0)",
                walletId,
                "VN" + String.format("%032d", walletId),
                balance,
                userId
        );
        return walletId;
    }

    private void insertEntry(
            UUID id,
            long walletId,
            String direction,
            String type,
            long amount,
            String reference,
            long balanceAfter
    ) {
        jdbcTemplate.update(
                "insert into wallet_entries "
                        + "(id, wallet_id, direction, entry_type, amount_vnd, business_reference, business_label, "
                        + "balance_after_vnd, created_at) values (?, ?, ?, ?, ?, ?, 'Test entry', ?, now())",
                id,
                walletId,
                direction,
                type,
                amount,
                reference,
                balanceAfter
        );
    }
}
