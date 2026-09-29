package com.github.yildizmy.wallet;

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

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(WalletLedgerService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class WalletLedgerServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WalletLedgerService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serializesConcurrentCreditsAndKeepsReferencesIdempotent() throws Exception {
        long walletId = insertWallet(0);

        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> service.credit(
                            walletId,
                            1_000,
                            WalletEntryType.DEPOSIT,
                            "deposit:concurrent:" + index,
                            "Concurrent deposit"
                    )))
                    .toList();
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        WalletLedgerService.Result duplicate = service.credit(
                walletId, 1_000, WalletEntryType.DEPOSIT,
                "deposit:concurrent:0", "Concurrent deposit"
        );

        assertThat(duplicate.created()).isFalse();
        assertThat(jdbc.queryForObject(
                "select balance from wallet where id = ?", Long.class, walletId
        )).isEqualTo(8_000L);
        assertThat(jdbc.queryForObject(
                "select count(*) from wallet_entries where wallet_id = ?", Integer.class, walletId
        )).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "select count(*) from wallet_entries where wallet_id = ? "
                        + "and balance_after_vnd < amount_vnd",
                Integer.class,
                walletId
        )).isZero();
    }

    private long insertWallet(long balance) {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Ledger', 'Test', ?, ?, 'not-used')",
                userId,
                "ledger-service-" + userId,
                "ledger-service-" + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', ?, ?, 0)",
                walletId,
                "VN" + String.format("%032d", walletId),
                balance,
                userId
        );
        return walletId;
    }
}
