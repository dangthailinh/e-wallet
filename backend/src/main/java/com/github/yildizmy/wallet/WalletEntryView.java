package com.github.yildizmy.wallet;

import java.time.Instant;
import java.util.UUID;

public record WalletEntryView(
        UUID id,
        WalletEntryDirection direction,
        WalletEntryType type,
        long amountVnd,
        long balanceAfterVnd,
        String businessLabel,
        Instant createdAt
) {

    public static WalletEntryView from(WalletEntry entry) {
        return new WalletEntryView(
                entry.getId(),
                entry.getDirection(),
                entry.getType(),
                entry.getAmountVnd(),
                entry.getBalanceAfterVnd(),
                entry.getBusinessLabel().replaceAll("[\\p{Cntrl}<>]", ""),
                entry.getCreatedAt()
        );
    }
}
