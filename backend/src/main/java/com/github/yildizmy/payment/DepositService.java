package com.github.yildizmy.payment;

import com.github.yildizmy.config.BusinessMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DepositService {

    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    private final DepositPersistenceService persistence;
    private final Map<DepositChannel, PaymentProvider> providers;
    private final ObjectProvider<BusinessMetrics> metrics;

    public DepositService(
            DepositPersistenceService persistence,
            List<PaymentProvider> configuredProviders,
            ObjectProvider<BusinessMetrics> metrics
    ) {
        this.persistence = persistence;
        this.metrics = Objects.requireNonNull(metrics, "metrics provider is required");
        EnumMap<DepositChannel, PaymentProvider> byChannel = new EnumMap<>(DepositChannel.class);
        for (PaymentProvider provider : configuredProviders) {
            PaymentProvider previous = byChannel.putIfAbsent(provider.channel(), provider);
            if (previous != null) {
                throw new IllegalStateException("Multiple payment providers configured for " + provider.channel());
            }
        }
        this.providers = Map.copyOf(byChannel);
    }

    public DepositResponse create(long userId, CreateDepositRequest request) {
        DepositChannel channel = request.channel().toDomain();
        PaymentProvider provider = providers.get(channel);
        if (provider == null) {
            throw new PaymentChannelUnavailableException();
        }
        provider.validateAmount(request.amountVnd(), "VND");

        Instant expiresAt = Instant.now().plus(PAYMENT_WINDOW);
        String reference = newReference();
        Deposit deposit = persistence.create(
                userId,
                channel,
                provider.providerCode(),
                reference,
                request.amountVnd(),
                expiresAt
        );
        PaymentPreparation preparation = new PaymentPreparation(
                        deposit.getId(),
                        deposit.getMerchantReference(),
                        deposit.getAmountVnd(),
                        deposit.getCurrency(),
                        deposit.getExpiresAt()
                );
        BusinessMetrics businessMetrics = metrics.getIfAvailable();
        PaymentInstructions instructions = businessMetrics == null
                ? Objects.requireNonNull(provider.prepare(preparation), "payment provider returned no instructions")
                : businessMetrics.timeDepositProvider(
                        provider.providerCode(),
                        () -> Objects.requireNonNull(provider.prepare(preparation), "payment provider returned no instructions")
                );
        return DepositResponse.created(deposit, instructions);
    }

    public DepositResponse status(long userId, UUID depositId) {
        return DepositResponse.status(persistence.ownedBy(userId, depositId));
    }

    private String newReference() {
        return "DEP_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
