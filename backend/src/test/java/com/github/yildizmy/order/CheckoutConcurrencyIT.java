package com.github.yildizmy.order;

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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({CheckoutService.class, WalletLedgerService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CheckoutConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CheckoutService checkout;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoBuyersCannotAcquireTheSameFinalInventoryItem() throws Exception {
        CheckoutFixtures fixtures = new CheckoutFixtures(jdbc);
        CheckoutFixtures.Buyer firstBuyer = fixtures.buyer(100_000);
        CheckoutFixtures.Buyer secondBuyer = fixtures.buyer(100_000);
        CheckoutFixtures.Stock stock = fixtures.stock(50_000, 1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Optional<CheckoutResult>>> attempts = List.of(firstBuyer, secondBuyer).stream()
                    .map(buyer -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            return Optional.of(checkout.checkout(
                                    buyer.userId(), stock.productId(), stock.priceVnd(), "race-key-" + buyer.userId()
                            ));
                        } catch (OutOfStockException expected) {
                            return Optional.<CheckoutResult>empty();
                        }
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long successes = 0;
            for (Future<Optional<CheckoutResult>> attempt : attempts) {
                if (attempt.get(20, TimeUnit.SECONDS).isPresent()) successes++;
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            start.countDown();
        }

        assertThat(fixtures.count(
                "select count(*) from orders where user_id in (?, ?)",
                firstBuyer.userId(), secondBuyer.userId()
        )).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from order_items where product_id = ?", stock.productId()
        )).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from inventory_items where product_id = ? and status = 'SOLD'", stock.productId()
        )).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id in (?, ?) "
                        + "and direction = 'DEBIT' and entry_type = 'PURCHASE'",
                firstBuyer.walletId(), secondBuyer.walletId()
        )).isEqualTo(1);
        long winningUserId = jdbc.queryForObject(
                "select user_id from orders where user_id in (?, ?)",
                Long.class,
                firstBuyer.userId(), secondBuyer.userId()
        );
        CheckoutFixtures.Buyer winner = winningUserId == firstBuyer.userId() ? firstBuyer : secondBuyer;
        CheckoutFixtures.Buyer loser = winningUserId == firstBuyer.userId() ? secondBuyer : firstBuyer;
        assertThat(fixtures.balance(winner.walletId())).isEqualTo(50_000);
        assertThat(fixtures.balance(loser.walletId())).isEqualTo(100_000);
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id = ? and entry_type = 'PURCHASE'",
                winner.walletId()
        )).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id = ? and entry_type = 'PURCHASE'",
                loser.walletId()
        )).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetryWithTheSameKeyReturnsOneOrderAndDebitsOnce() throws Exception {
        CheckoutFixtures fixtures = new CheckoutFixtures(jdbc);
        CheckoutFixtures.Buyer buyer = fixtures.buyer(100_000);
        CheckoutFixtures.Stock stock = fixtures.stock(25_000, 2);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<CheckoutResult>> attempts = IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return checkout.checkout(buyer.userId(), stock.productId(), stock.priceVnd(), "same-key-0001");
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<CheckoutResult> results = new java.util.ArrayList<>();
            for (Future<CheckoutResult> attempt : attempts) {
                results.add(attempt.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).extracting(CheckoutResult::orderId).containsOnly(results.getFirst().orderId());
            assertThat(results).filteredOn(CheckoutResult::created).hasSize(1);
        } finally {
            start.countDown();
        }

        assertThat(fixtures.balance(buyer.walletId())).isEqualTo(75_000);
        assertThat(fixtures.count("select count(*) from orders where user_id = ?", buyer.userId())).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from order_items where product_id = ?", stock.productId()
        )).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from inventory_items where product_id = ? and status = 'SOLD'", stock.productId()
        )).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id = ? and entry_type = 'PURCHASE'", buyer.walletId()
        )).isEqualTo(1);
    }
}
