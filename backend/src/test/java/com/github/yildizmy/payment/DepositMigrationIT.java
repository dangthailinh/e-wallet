package com.github.yildizmy.payment;

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
@Testcontainers
class DepositMigrationIT {

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
    void migrationCreatesFinancialConstraintsAndTransitionTrigger() {
        assertThat(jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where version = '12'", Boolean.class
        )).isTrue();
        List<String> constraints = jdbcTemplate.queryForList(
                "select conname from pg_constraint where conrelid in "
                        + "('payment_deposits'::regclass, 'webhook_events'::regclass)", String.class
        );
        assertThat(constraints).contains(
                "fk_deposit_owned_wallet", "uc_deposit_merchant_reference",
                "uc_deposit_provider_transaction", "ck_deposit_amount_positive",
                "ck_deposit_currency_vnd", "fk_webhook_deposit_provider",
                "uc_webhook_provider_event", "uc_webhook_provider_transaction",
                "ck_webhook_request_hash_length"
        );
        assertThat(jdbcTemplate.queryForList(
                "select tgname from pg_trigger where tgrelid = 'payment_deposits'::regclass "
                        + "and not tgisinternal", String.class
        )).contains("trg_deposit_initial_state", "trg_deposit_status_transition", "trg_deposit_no_delete");
        assertThat(jdbcTemplate.queryForList(
                "select tgname from pg_trigger where tgrelid = 'webhook_events'::regclass "
                        + "and not tgisinternal", String.class
        )).contains("trg_webhook_initial_state", "trg_webhook_event_identity", "trg_webhook_event_no_delete");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void depositRejectsWrongWalletDuplicateIdentityAndInvalidMoney() {
        long[] owner = insertOwnerAndWallet();
        long[] anotherOwner = insertOwnerAndWallet();
        UUID first = UUID.randomUUID();
        insertDeposit(first, owner[0], owner[1], "BANK", "BANK_TEST", "DEP_00000001", 50_000);

        assertThatThrownBy(() -> insertDeposit(UUID.randomUUID(), anotherOwner[0], owner[1],
                "BANK", "BANK_TEST", "DEP_00000002", 50_000)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertDeposit(UUID.randomUUID(), owner[0], owner[1],
                "BANK", "BANK_TEST", "DEP_00000001", 50_000)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertDeposit(UUID.randomUUID(), owner[0], owner[1],
                "BANK", "BANK_TEST", "DEP_00000003", 0)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into payment_deposits "
                        + "(id, user_id, wallet_id, channel, provider_code, merchant_reference, "
                        + "amount_vnd, expires_at, status, provider_transaction_id, paid_at) "
                        + "values (?, ?, ?, 'BANK', 'BANK_TEST', 'DEP_00000006', 50000, "
                        + "now() + interval '1 hour', 'PAID', 'tx-direct', now())",
                UUID.randomUUID(), owner[0], owner[1]
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update payment_deposits set currency = 'USD' where id = ?", first
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update payment_deposits set needs_reconciliation_review = true where id = ?", first
        )).isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("update payment_deposits set provider_transaction_id = 'bank-tx-1' where id = ?", first);
        insertDeposit(UUID.randomUUID(), owner[0], owner[1],
                "BANK", "BANK_TEST", "DEP_00000004", 50_000);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update payment_deposits set provider_transaction_id = 'bank-tx-1' "
                        + "where merchant_reference = 'DEP_00000004'"
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void webhookIdentityProviderAssociationAndTerminalStateAreEnforced() {
        long[] owner = insertOwnerAndWallet();
        UUID depositId = UUID.randomUUID();
        insertDeposit(depositId, owner[0], owner[1], "BANK", "BANK_TEST", "DEP_00000005", 90_000);
        UUID eventId = UUID.randomUUID();
        insertWebhook(eventId, depositId, "BANK_TEST", "event-1", "tx-1");

        assertThatThrownBy(() -> insertWebhook(UUID.randomUUID(), depositId,
                "BANK_TEST", "event-1", "tx-2")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertWebhook(UUID.randomUUID(), depositId,
                "BANK_TEST", "event-2", "tx-1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertWebhook(UUID.randomUUID(), depositId,
                "MOMO", "event-3", "tx-3")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into webhook_events (id, deposit_id, provider_code, provider_event_id, "
                        + "request_hash, result, processed_at) values "
                        + "(?, ?, 'BANK_TEST', 'event-direct', decode(repeat('ab', 32), 'hex'), 'PROCESSED', now())",
                UUID.randomUUID(), depositId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update webhook_events set provider_event_id = 'rewritten' where id = ?", eventId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update webhook_events set request_hash = decode(repeat('cd', 32), 'hex') where id = ?", eventId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from webhook_events where id = ?", eventId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from payment_deposits where id = ?", depositId
        )).isInstanceOf(DataAccessException.class);
        jdbcTemplate.update(
                "update webhook_events set result = 'PROCESSED', processed_at = now() where id = ?", eventId
        );
        assertThat(jdbcTemplate.queryForObject(
                "select result from webhook_events where id = ?", String.class, eventId
        )).isEqualTo("PROCESSED");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update webhook_events set result = 'RECEIVED', processed_at = null where id = ?", eventId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update payment_deposits set status = 'PAID' where id = ?", depositId
        )).isInstanceOf(DataAccessException.class);

        jdbcTemplate.update(
                "update payment_deposits set status = 'PAID', paid_at = now(), "
                        + "provider_transaction_id = 'tx-1' where id = ?", depositId
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update payment_deposits set status = 'PENDING' where id = ?", depositId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update payment_deposits set paid_at = now() where id = ?", depositId
        )).isInstanceOf(DataAccessException.class);
        jdbcTemplate.update(
                "update payment_deposits set needs_reconciliation_review = true, updated_at = now() where id = ?",
                depositId
        );
        assertThat(jdbcTemplate.queryForObject(
                "select status from payment_deposits where id = ?", String.class, depositId
        )).isEqualTo("PAID");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update payment_deposits set needs_reconciliation_review = false where id = ?", depositId
        )).isInstanceOf(DataAccessException.class);

        UUID expiredId = UUID.randomUUID();
        insertDeposit(expiredId, owner[0], owner[1], "BANK", "BANK_TEST", "DEP_00000007", 90_000);
        jdbcTemplate.update("update payment_deposits set status = 'EXPIRED' where id = ?", expiredId);
        jdbcTemplate.update(
                "update payment_deposits set needs_reconciliation_review = true where id = ?", expiredId
        );
        assertThat(jdbcTemplate.queryForObject(
                "select status from payment_deposits where id = ?", String.class, expiredId
        )).isEqualTo("EXPIRED");
    }

    private long[] insertOwnerAndWallet() {
        long userId = jdbcTemplate.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbcTemplate.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbcTemplate.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Deposit', 'Test', ?, ?, 'not-used')",
                userId, "deposit" + userId, "deposit" + userId + "@example.test"
        );
        jdbcTemplate.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', 0, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), userId
        );
        return new long[]{userId, walletId};
    }

    private void insertDeposit(
            UUID id, long userId, long walletId, String channel, String provider,
            String reference, long amount
    ) {
        jdbcTemplate.update(
                "insert into payment_deposits "
                        + "(id, user_id, wallet_id, channel, provider_code, merchant_reference, "
                        + "amount_vnd, expires_at) values (?, ?, ?, ?, ?, ?, ?, now() + interval '1 hour')",
                id, userId, walletId, channel, provider, reference, amount
        );
    }

    private void insertWebhook(UUID id, UUID depositId, String provider, String eventId, String transactionId) {
        jdbcTemplate.update(
                "insert into webhook_events (id, deposit_id, provider_code, provider_event_id, "
                        + "provider_transaction_id, request_hash) "
                        + "values (?, ?, ?, ?, ?, decode(repeat('ab', 32), 'hex'))",
                id, depositId, provider, eventId, transactionId
        );
    }
}
