package com.github.yildizmy.payment.momo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yildizmy.payment.VerifiedNotification;
import com.github.yildizmy.payment.WebhookProcessor;
import com.github.yildizmy.wallet.WalletLedgerService;
import org.junit.jupiter.api.BeforeEach;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
@Import({WebhookProcessor.class, WalletLedgerService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MomoReplayIT {

    private static final String ACCESS_KEY = "fixture-access";
    private static final String SECRET_KEY = "fixture-secret-key-32-characters";

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

    private ObjectMapper objectMapper;
    private MomoPaymentProvider provider;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        provider = new MomoPaymentProvider(mock(MomoClient.class), properties(), objectMapper);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void validSignedFixtureCreditsExactlyOnceAcrossTenDeliveries() {
        Fixture fixture = fixture("DEP_MOMO_REPLAY_0001", 100_000);
        byte[] raw = signedBody(fixture.reference(), fixture.amountVnd(), "momo-request-replay", 4_088_878_653L, 0);

        for (int delivery = 0; delivery < 10; delivery++) {
            VerifiedNotification verified = provider.verify(raw, Map.of());
            processor.process(verified, sha256(raw));
        }

        assertThat(balance(fixture.walletId())).isEqualTo(100_000);
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", fixture.walletId())).isEqualTo(1);
        assertThat(count("select count(*) from webhook_events where deposit_id = ?", fixture.depositId())).isEqualTo(1);
        assertThat(status(fixture.depositId())).isEqualTo("PAID");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void forgedAndSignedAmountMismatchNeverCreditWallet() {
        Fixture forgedFixture = fixture("DEP_MOMO_FORGED_0001", 100_000);
        byte[] valid = signedBody(forgedFixture.reference(), 100_000, "momo-request-forged", 4_088_878_654L, 0);
        byte[] forged = new String(valid, StandardCharsets.UTF_8)
                .replace("\"amount\":100000", "\"amount\":100001")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider.verify(forged, Map.of()))
                .isInstanceOf(MomoNotificationInvalidException.class);
        assertThat(balance(forgedFixture.walletId())).isZero();

        Fixture mismatchFixture = fixture("DEP_MOMO_MISMATCH_01", 125_000);
        byte[] mismatch = signedBody(
                mismatchFixture.reference(), 124_000, "momo-request-mismatch", 4_088_878_655L, 0
        );
        processor.process(provider.verify(mismatch, Map.of()), sha256(mismatch));

        assertThat(balance(mismatchFixture.walletId())).isZero();
        assertThat(status(mismatchFixture.depositId())).isEqualTo("REVIEW");
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", mismatchFixture.walletId()))
                .isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void successArrivingAfterFinalFailureIsRetainedForReviewWithoutCredit() {
        Fixture fixture = fixture("DEP_MOMO_ORDER_00001", 150_000);
        byte[] failure = signedBody(fixture.reference(), fixture.amountVnd(), "momo-request-failed", 4_088_878_656L, 1001);
        byte[] lateSuccess = signedBody(fixture.reference(), fixture.amountVnd(), "momo-request-late", 4_088_878_657L, 0);

        processor.process(provider.verify(failure, Map.of()), sha256(failure));
        processor.process(provider.verify(lateSuccess, Map.of()), sha256(lateSuccess));

        assertThat(balance(fixture.walletId())).isZero();
        assertThat(status(fixture.depositId())).isEqualTo("FAILED");
        assertThat(count("select count(*) from wallet_entries where wallet_id = ?", fixture.walletId())).isZero();
        assertThat(count("select count(*) from webhook_events where deposit_id = ?", fixture.depositId())).isEqualTo(2);
    }

    private byte[] signedBody(String reference, long amount, String requestId, long transactionId, int resultCode) {
        String message = resultCode == 0 ? "Successful." : "Insufficient funds.";
        MomoNotification unsigned = new MomoNotification(
                "FIXTURE_PARTNER", reference, requestId, amount, "Nap tien vi " + reference,
                "momo_wallet", transactionId, resultCode, message, "qr", 1_727_000_000_000L, "", null
        );
        String signature = MomoSignature.signNotification(ACCESS_KEY, SECRET_KEY, unsigned);
        MomoNotification signed = new MomoNotification(
                unsigned.partnerCode(), unsigned.orderId(), unsigned.requestId(), unsigned.amount(),
                unsigned.orderInfo(), unsigned.orderType(), unsigned.transId(), unsigned.resultCode(),
                unsigned.message(), unsigned.payType(), unsigned.responseTime(), unsigned.extraData(), signature
        );
        try {
            return objectMapper.writeValueAsBytes(signed);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Fixture fixture(String reference, long amountVnd) {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        UUID depositId = UUID.randomUUID();
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'MoMo', 'Buyer', ?, ?, 'not-used')",
                userId, "momo-" + userId, "momo-" + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) values (?, ?, 'Primary', 0, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), userId
        );
        jdbc.update(
                "insert into payment_deposits "
                        + "(id, user_id, wallet_id, channel, provider_code, merchant_reference, amount_vnd, expires_at) "
                        + "values (?, ?, ?, 'MOMO', 'MOMO', ?, ?, now() + interval '1 hour')",
                depositId, userId, walletId, reference, amountVnd
        );
        return new Fixture(depositId, walletId, reference, amountVnd);
    }

    private long balance(long walletId) {
        return jdbc.queryForObject("select balance from wallet where id = ?", Long.class, walletId);
    }

    private String status(UUID depositId) {
        return jdbc.queryForObject("select status from payment_deposits where id = ?", String.class, depositId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static MomoProperties properties() {
        return new MomoProperties(
                URI.create("https://test-payment.momo.vn/v2/gateway/api/create"),
                "FIXTURE_PARTNER", ACCESS_KEY, SECRET_KEY,
                URI.create("https://shop.example.com/top-up"),
                URI.create("https://shop.example.com/api/v1/webhooks/momo"),
                Duration.ofSeconds(5), Duration.ofSeconds(30)
        );
    }

    private record Fixture(UUID depositId, long walletId, String reference, long amountVnd) {
    }
}
