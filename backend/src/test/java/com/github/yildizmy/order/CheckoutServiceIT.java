package com.github.yildizmy.order;

import com.github.yildizmy.exception.InsufficientFundsException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import({CheckoutService.class, WalletLedgerService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CheckoutServiceIT {

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
    void failureAtCommitRollsBackWalletOrderAndInventoryTogether() {
        CheckoutFixtures fixtures = new CheckoutFixtures(jdbc);
        CheckoutFixtures.Buyer buyer = fixtures.buyer(100_000);
        CheckoutFixtures.Stock stock = fixtures.stock(40_000, 1);
        jdbc.execute("""
                CREATE FUNCTION fail_inventory_sale() RETURNS TRIGGER LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'injected late checkout failure';
                END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER trg_test_checkout_failure BEFORE UPDATE ON inventory_items
                FOR EACH ROW
                WHEN (OLD.status = 'AVAILABLE' AND NEW.status = 'SOLD')
                EXECUTE FUNCTION fail_inventory_sale()
                """);

        try {
            assertThatThrownBy(() -> checkout.checkout(buyer.userId(), stock.productId(), stock.priceVnd(), "rollback-key"))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER trg_test_checkout_failure ON inventory_items");
            jdbc.execute("DROP FUNCTION fail_inventory_sale()");
        }

        assertThat(fixtures.balance(buyer.walletId())).isEqualTo(100_000);
        assertThat(fixtures.count("select count(*) from orders where user_id = ?", buyer.userId())).isZero();
        assertThat(fixtures.count(
                "select count(*) from order_items where product_id = ?", stock.productId()
        )).isZero();
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id = ? and entry_type = 'PURCHASE'", buyer.walletId()
        )).isZero();
        assertThat(fixtures.count(
                "select count(*) from inventory_items where product_id = ? and status = 'AVAILABLE'", stock.productId()
        )).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void insufficientFundsLeavesNoPartialWrites() {
        CheckoutFixtures fixtures = new CheckoutFixtures(jdbc);
        CheckoutFixtures.Buyer buyer = fixtures.buyer(10_000);
        CheckoutFixtures.Stock stock = fixtures.stock(40_000, 1);

        assertThatThrownBy(() -> checkout.checkout(buyer.userId(), stock.productId(), stock.priceVnd(), "poor-key-001"))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(fixtures.balance(buyer.walletId())).isEqualTo(10_000);
        assertThat(fixtures.count("select count(*) from orders where user_id = ?", buyer.userId())).isZero();
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id = ? and entry_type = 'PURCHASE'", buyer.walletId()
        )).isZero();
        assertThat(fixtures.count(
                "select count(*) from inventory_items where product_id = ? and status = 'AVAILABLE'", stock.productId()
        )).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reusingAKeyForAnotherProductIsRejectedWithoutASecondDebit() {
        CheckoutFixtures fixtures = new CheckoutFixtures(jdbc);
        CheckoutFixtures.Buyer buyer = fixtures.buyer(100_000);
        CheckoutFixtures.Stock firstStock = fixtures.stock(20_000, 1);
        CheckoutFixtures.Stock secondStock = fixtures.stock(30_000, 1);

        CheckoutResult original = checkout.checkout(buyer.userId(), firstStock.productId(), firstStock.priceVnd(), "reuse-key-001");
        assertThatThrownBy(() -> checkout.checkout(buyer.userId(), secondStock.productId(), secondStock.priceVnd(), "reuse-key-001"))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(original.created()).isTrue();
        assertThat(fixtures.balance(buyer.walletId())).isEqualTo(80_000);
        assertThat(fixtures.count("select count(*) from orders where user_id = ?", buyer.userId())).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id = ? and entry_type = 'PURCHASE'", buyer.walletId()
        )).isEqualTo(1);
        assertThat(fixtures.count(
                "select count(*) from inventory_items where product_id = ? and status = 'AVAILABLE'",
                secondStock.productId()
        )).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void changedPriceIsRejectedBeforeAnyDebitOrStockMutation() {
        CheckoutFixtures fixtures = new CheckoutFixtures(jdbc);
        CheckoutFixtures.Buyer buyer = fixtures.buyer(100_000);
        CheckoutFixtures.Stock stock = fixtures.stock(60_000, 1);

        assertThatThrownBy(() -> checkout.checkout(buyer.userId(), stock.productId(), 50_000, "stale-price-001"))
                .isInstanceOf(ProductPriceChangedException.class);

        assertThat(fixtures.balance(buyer.walletId())).isEqualTo(100_000);
        assertThat(fixtures.count("select count(*) from orders where user_id = ?", buyer.userId())).isZero();
        assertThat(fixtures.count(
                "select count(*) from wallet_entries where wallet_id = ? and entry_type = 'PURCHASE'", buyer.walletId()
        )).isZero();
        assertThat(fixtures.count(
                "select count(*) from inventory_items where product_id = ? and status = 'AVAILABLE'", stock.productId()
        )).isEqualTo(1);
    }
}
