package com.github.yildizmy.order;

import com.github.yildizmy.catalog.CatalogStatus;
import com.github.yildizmy.catalog.Product;
import com.github.yildizmy.config.BusinessMetrics;
import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.inventory.InventoryItem;
import com.github.yildizmy.inventory.InventoryRepository;
import com.github.yildizmy.repository.WalletRepository;
import com.github.yildizmy.wallet.WalletEntryType;
import com.github.yildizmy.wallet.WalletLedgerService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CheckoutService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final WalletRepository wallets;
    private final InventoryRepository inventory;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final WalletLedgerService ledger;
    private final ObjectProvider<BusinessMetrics> metrics;

    public CheckoutService(
            WalletRepository wallets,
            InventoryRepository inventory,
            OrderRepository orders,
            OrderItemRepository orderItems,
            WalletLedgerService ledger,
            ObjectProvider<BusinessMetrics> metrics
    ) {
        this.wallets = wallets;
        this.inventory = inventory;
        this.orders = orders;
        this.orderItems = orderItems;
        this.ledger = ledger;
        this.metrics = metrics;
    }

    @Transactional
    public CheckoutResult checkout(long userId, UUID productId, long expectedPriceVnd, String idempotencyKey) {
        UUID requestedProductId = Objects.requireNonNull(productId, "productId is required");
        if (expectedPriceVnd <= 0) {
            throw new IllegalArgumentException("expectedPriceVnd must be positive");
        }
        String requestKey = requireIdempotencyKey(idempotencyKey);

        try {
            Wallet wallet = lockSingleWallet(userId);
            CheckoutResult result = orders.findByUserIdAndIdempotencyKey(userId, requestKey)
                    .map(order -> existingResult(order, requestedProductId, wallet))
                    .orElseGet(() -> createOrder(wallet, requestedProductId, expectedPriceVnd, requestKey));
            metrics.ifAvailable(value -> value.checkout(result.created()));
            return result;
        } catch (RuntimeException exception) {
            metrics.ifAvailable(BusinessMetrics::checkoutFailed);
            throw exception;
        }
    }

    private CheckoutResult createOrder(
            Wallet wallet,
            UUID productId,
            long expectedPriceVnd,
            String idempotencyKey
    ) {
        InventoryItem stock = inventory.lockNextAvailable(productId)
                .orElseThrow(OutOfStockException::new);
        Product product = stock.getProduct();
        if (product.getStatus() != CatalogStatus.ACTIVE) {
            throw new OutOfStockException();
        }
        if (product.getPriceVnd() != expectedPriceVnd) {
            throw new ProductPriceChangedException();
        }

        Order order = orders.save(new Order(
                wallet.getUser(), wallet, idempotencyKey, product.getPriceVnd()
        ));
        OrderItem item = orderItems.save(new OrderItem(order, product, stock));
        WalletLedgerService.Result debit = ledger.debit(
                wallet.getId(),
                product.getPriceVnd(),
                WalletEntryType.PURCHASE,
                "order:" + order.getId(),
                product.getName()
        );
        stock.sell(item.getId());
        inventory.save(stock);
        return CheckoutResult.from(order, item, debit.balanceAfterVnd(), true);
    }

    private CheckoutResult existingResult(Order order, UUID productId, Wallet wallet) {
        OrderItem item = orderItems.findFirstByOrderIdOrderById(order.getId())
                .orElseThrow(() -> new IllegalStateException("Completed order has no item"));
        if (!item.getProduct().getId().equals(productId)) {
            throw new IdempotencyConflictException();
        }
        return CheckoutResult.from(order, item, wallet.getBalance().longValueExact(), false);
    }

    private Wallet lockSingleWallet(long userId) {
        List<Wallet> owned = wallets.lockAllByUserId(userId);
        if (owned.isEmpty()) {
            throw new NoSuchElementFoundException("Wallet not found");
        }
        if (owned.size() > 1) {
            throw new ElementAlreadyExistsException("Multiple legacy wallets require manual review");
        }
        return owned.getFirst();
    }

    private String requireIdempotencyKey(String value) {
        String key = Objects.requireNonNull(value, "idempotencyKey is required");
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("idempotencyKey has invalid format");
        }
        return key;
    }
}
