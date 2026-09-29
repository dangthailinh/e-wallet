package com.github.yildizmy.order;

import com.github.yildizmy.exception.NoSuchElementFoundException;
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
@Import({OrderQueryService.class, CheckoutService.class, WalletLedgerService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderQueryServiceIT {

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
    private OrderQueryService queries;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void listAndDetailReturnOnlyTheOwnersOrders() {
        CheckoutFixtures fixtures = new CheckoutFixtures(jdbc);
        CheckoutFixtures.Buyer owner = fixtures.buyer(100_000);
        CheckoutFixtures.Buyer stranger = fixtures.buyer(100_000);
        CheckoutFixtures.Stock stock = fixtures.stock(30_000, 1);
        CheckoutResult purchase = checkout.checkout(owner.userId(), stock.productId(), stock.priceVnd(), "query-key-001");

        OrderListResponse list = queries.list(owner.userId(), 0, 20);
        OrderDetailResponse detail = queries.detail(owner.userId(), purchase.orderId());

        assertThat(list.items()).extracting(OrderSummaryResponse::orderId)
                .containsExactly(purchase.orderId());
        assertThat(list.totalItems()).isEqualTo(1);
        assertThat(detail.items()).singleElement().satisfies(item -> {
            assertThat(item.itemId()).isEqualTo(purchase.orderItemId());
            assertThat(item.productId()).isEqualTo(stock.productId());
            assertThat(item.productName()).isEqualTo("Checkout product");
            assertThat(item.unitPriceVnd()).isEqualTo(30_000);
        });
        assertThatThrownBy(() -> queries.detail(stranger.userId(), purchase.orderId()))
                .isInstanceOf(NoSuchElementFoundException.class)
                .hasMessage("Order not found");
        assertThatThrownBy(() -> queries.detail(stranger.userId(), java.util.UUID.randomUUID()))
                .isInstanceOf(NoSuchElementFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void rejectsUnboundedPagination() {
        assertThatThrownBy(() -> queries.list(1L, -1, 20))
                .isInstanceOf(InvalidOrderQueryException.class);
        assertThatThrownBy(() -> queries.list(1L, 0, 51))
                .isInstanceOf(InvalidOrderQueryException.class);
    }
}
