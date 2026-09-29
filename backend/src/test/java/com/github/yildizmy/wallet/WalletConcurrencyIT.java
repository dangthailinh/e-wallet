package com.github.yildizmy.wallet;

import com.github.yildizmy.exception.InsufficientFundsException;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(WalletLedgerService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WalletConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void configurePostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WalletLedgerService service;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(value = 3, failureThreshold = 1)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDebitsNeverOverdrawTheWallet() throws Exception {
        long walletId = insertEmptyWallet();
        service.credit(walletId, 1_000, WalletEntryType.DEPOSIT, "seed:" + walletId, "Initial deposit");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(8);

        ExecutorService executor = raceExecutor();
        try {
            List<Future<Boolean>> attempts = IntStream.range(0, 16)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            service.debit(walletId, 100, WalletEntryType.PURCHASE,
                                    "purchase:" + walletId + ":" + index, "Concurrent purchase");
                            return true;
                        } catch (InsufficientFundsException expected) {
                            return false;
                        }
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long successful = 0;
            for (Boolean result : awaitAll(attempts)) {
                if (result) successful++;
            }
            assertThat(successful).isEqualTo(10);
        } finally {
            stopRace(executor, start);
        }

        assertThat(balance(walletId)).isZero();
        assertThat(entryCount(walletId, "DEBIT")).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "select count(*) from wallet_entries where wallet_id = ? and balance_after_vnd < 0",
                Integer.class, walletId
        )).isZero();
    }

    @RepeatedTest(value = 3, failureThreshold = 1)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateReferenceDebitsExactlyOnce() throws Exception {
        long walletId = insertEmptyWallet();
        service.credit(walletId, 1_000, WalletEntryType.DEPOSIT, "seed:" + walletId, "Initial deposit");
        String reference = "purchase:duplicate:" + walletId;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(8);

        ExecutorService executor = raceExecutor();
        try {
            List<Future<WalletLedgerService.Result>> attempts = IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.debit(walletId, 100, WalletEntryType.PURCHASE,
                                reference, "Duplicate purchase");
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long created = 0;
            for (WalletLedgerService.Result result : awaitAll(attempts)) {
                if (result.created()) created++;
            }
            assertThat(created).isEqualTo(1);
        } finally {
            stopRace(executor, start);
        }

        assertThat(balance(walletId)).isEqualTo(900);
        assertThat(entryCount(walletId, "DEBIT")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from wallet_entries where business_reference = ?",
                Integer.class, reference
        )).isEqualTo(1);
    }

    private long insertEmptyWallet() {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Race', 'Test', ?, ?, 'not-used')",
                userId,
                "wallet-race-" + userId,
                "wallet-race-" + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', 0, ?, 0)",
                walletId,
                "VN" + String.format("%032d", walletId),
                userId
        );
        return walletId;
    }

    private ExecutorService raceExecutor() {
        return Executors.newFixedThreadPool(8, Thread.ofPlatform().daemon().name("wallet-race-", 0).factory());
    }

    private <T> List<T> awaitAll(List<Future<T>> attempts) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        List<T> results = new java.util.ArrayList<>(attempts.size());
        for (Future<T> attempt : attempts) {
            results.add(attempt.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
        }
        return results;
    }

    private void stopRace(ExecutorService executor, CountDownLatch start) throws InterruptedException {
        start.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
    }

    private long balance(long walletId) {
        return jdbc.queryForObject("select balance from wallet where id = ?", Long.class, walletId);
    }

    private int entryCount(long walletId, String direction) {
        return jdbc.queryForObject(
                "select count(*) from wallet_entries where wallet_id = ? and direction = ?",
                Integer.class, walletId, direction
        );
    }
}
