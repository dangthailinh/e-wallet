package com.github.yildizmy.payment;

import com.github.yildizmy.wallet.WalletLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({WebhookProcessor.class, WalletLedgerService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WebhookProcessorIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebhookProcessor processor;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void verifiedSuccessCreditsExactlyOnceAcrossEventAndTransactionReplays() {
        Fixture fixture = fixture("DEP_WEBHOOK_0001", 50_000);
        VerifiedNotification first = success(
                "provider-event-1", "provider-transaction-1", fixture.reference(), fixture.amountVnd()
        );

        WebhookProcessor.Result created = processor.process(first, digest("first-payload"));
        WebhookProcessor.Result sameEvent = processor.process(first, digest("first-payload"));
        WebhookProcessor.Result sameTransaction = processor.process(
                success("provider-event-2", "provider-transaction-1", fixture.reference(), fixture.amountVnd()),
                digest("second-delivery")
        );

        assertThat(created.created()).isTrue();
        assertThat(created.result()).isEqualTo(WebhookEventResult.PROCESSED);
        assertThat(sameEvent.created()).isFalse();
        assertThat(sameTransaction.created()).isFalse();
        assertThat(balance(fixture.walletId())).isEqualTo(50_000);
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", fixture.walletId())).isEqualTo(1);
        assertThat(count("select count(*) from webhook_events where deposit_id = ?", fixture.depositId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select status from payment_deposits where id = ?", String.class, fixture.depositId()
        )).isEqualTo("PAID");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reusedEventIdentityWithDifferentPayloadIsRejectedWithoutAnotherCredit() {
        Fixture fixture = fixture("DEP_WEBHOOK_0002", 75_000);
        VerifiedNotification notification = success(
                "provider-event-conflict", "provider-transaction-conflict", fixture.reference(), fixture.amountVnd()
        );
        processor.process(notification, digest("original-payload"));

        assertThatThrownBy(() -> processor.process(notification, digest("tampered-payload")))
                .isInstanceOf(WebhookIdentityConflictException.class);

        assertThat(balance(fixture.walletId())).isEqualTo(75_000);
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", fixture.walletId())).isEqualTo(1);
        assertThat(count("select count(*) from webhook_events where deposit_id = ?", fixture.depositId())).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void verifiedMismatchIsRecordedForReviewAndNeverCreditsWallet() {
        Fixture fixture = fixture("DEP_WEBHOOK_0003", 90_000);

        WebhookProcessor.Result result = processor.process(
                success("provider-event-review", "provider-transaction-review", fixture.reference(), 89_000),
                digest("mismatched-payload")
        );

        assertThat(result.created()).isTrue();
        assertThat(result.result()).isEqualTo(WebhookEventResult.REVIEW);
        assertThat(balance(fixture.walletId())).isZero();
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", fixture.walletId())).isZero();
        assertThat(jdbc.queryForObject(
                "select status from payment_deposits where id = ?", String.class, fixture.depositId()
        )).isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject(
                "select error_code from webhook_events where deposit_id = ?", String.class, fixture.depositId()
        )).isEqualTo("PAYMENT_TERMS_MISMATCH");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void verifiedUnknownReferenceIsRetainedInInboxWithoutCreditingAnyWallet() {
        VerifiedNotification notification = success(
                "provider-event-unknown", "provider-transaction-unknown", "DEP_UNKNOWN_0001", 50_000
        );

        WebhookProcessor.Result result = processor.process(notification, digest("unknown-reference-payload"));

        assertThat(result.created()).isTrue();
        assertThat(result.depositId()).isNull();
        assertThat(result.walletBalanceVnd()).isNull();
        assertThat(result.result()).isEqualTo(WebhookEventResult.REVIEW);
        assertThat(count("select count(*) from wallet_entries")).isZero();
        assertThat(count("select count(*) from webhook_events where deposit_id is null")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select error_code from webhook_events where provider_event_id = 'provider-event-unknown'",
                String.class
        )).isEqualTo("DEPOSIT_NOT_FOUND");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void processingFailureRollsBackInboxWalletAndDepositTogether() {
        Fixture fixture = futureDatedFixture("DEP_WEBHOOK_0004", 125_000);
        VerifiedNotification notification = success(
                "provider-event-rollback", "provider-transaction-rollback",
                fixture.reference(), fixture.amountVnd()
        );

        assertThatThrownBy(() -> processor.process(notification, digest("rollback-payload")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("settlementTime must not precede creation");

        assertThat(balance(fixture.walletId())).isZero();
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", fixture.walletId())).isZero();
        assertThat(count("select count(*) from webhook_events where provider_event_id = ?", notification.providerEventId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select status from payment_deposits where id = ?", String.class, fixture.depositId()
        )).isEqualTo("PENDING");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void laterFailureForPaidDepositIsRetainedForReviewWithoutReversingOrCreditingAgain() {
        Fixture fixture = fixture("DEP_WEBHOOK_0005", 60_000);
        processor.process(
                success("provider-event-paid", "provider-transaction-paid", fixture.reference(), fixture.amountVnd()),
                digest("paid-payload")
        );

        WebhookProcessor.Result result = processor.process(
                failure("provider-event-late-failure", "provider-transaction-late-failure",
                        fixture.reference(), fixture.amountVnd()),
                digest("late-failure-payload")
        );

        assertThat(result.created()).isTrue();
        assertThat(result.result()).isEqualTo(WebhookEventResult.REVIEW);
        assertThat(balance(fixture.walletId())).isEqualTo(60_000);
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", fixture.walletId())).isEqualTo(1);
        assertThat(count("select count(*) from webhook_events where deposit_id = ?", fixture.depositId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select status from payment_deposits where id = ?", String.class, fixture.depositId()
        )).isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "select needs_reconciliation_review from payment_deposits where id = ?",
                Boolean.class, fixture.depositId()
        )).isTrue();
    }

    private VerifiedNotification success(String eventId, String transactionId, String reference, long amountVnd) {
        return new VerifiedNotification(
                "BANK_TEST",
                eventId,
                transactionId,
                reference,
                amountVnd,
                "VND",
                VerifiedNotification.Outcome.SUCCEEDED,
                Instant.now()
        );
    }

    private VerifiedNotification failure(String eventId, String transactionId, String reference, long amountVnd) {
        return new VerifiedNotification(
                "BANK_TEST",
                eventId,
                transactionId,
                reference,
                amountVnd,
                "VND",
                VerifiedNotification.Outcome.FAILED,
                Instant.now()
        );
    }

    private Fixture fixture(String reference, long amountVnd) {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        UUID depositId = UUID.randomUUID();
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Webhook', 'Buyer', ?, ?, 'not-used')",
                userId, "webhook-" + userId, "webhook-" + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', 0, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), userId
        );
        jdbc.update(
                "insert into payment_deposits "
                        + "(id, user_id, wallet_id, channel, provider_code, merchant_reference, amount_vnd, expires_at) "
                        + "values (?, ?, ?, 'BANK', 'BANK_TEST', ?, ?, now() + interval '1 hour')",
                depositId, userId, walletId, reference, amountVnd
        );
        return new Fixture(depositId, walletId, reference, amountVnd);
    }

    private Fixture futureDatedFixture(String reference, long amountVnd) {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        UUID depositId = UUID.randomUUID();
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Webhook', 'Buyer', ?, ?, 'not-used')",
                userId, "webhook-" + userId, "webhook-" + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', 0, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), userId
        );
        jdbc.update(
                "insert into payment_deposits "
                        + "(id, user_id, wallet_id, channel, provider_code, merchant_reference, amount_vnd, "
                        + "created_at, updated_at, expires_at) "
                        + "values (?, ?, ?, 'BANK', 'BANK_TEST', ?, ?, "
                        + "now() + interval '10 minutes', now() + interval '10 minutes', now() + interval '1 hour')",
                depositId, userId, walletId, reference, amountVnd
        );
        return new Fixture(depositId, walletId, reference, amountVnd);
    }

    private long balance(long walletId) {
        return jdbc.queryForObject("select balance from wallet where id = ?", Long.class, walletId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(UUID depositId, long walletId, String reference, long amountVnd) {
    }
}
