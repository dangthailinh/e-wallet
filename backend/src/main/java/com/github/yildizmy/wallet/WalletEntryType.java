package com.github.yildizmy.wallet;

import java.util.Set;

public enum WalletEntryType {
    OPENING_BALANCE(WalletEntryDirection.CREDIT),
    DEPOSIT(WalletEntryDirection.CREDIT),
    PURCHASE(WalletEntryDirection.DEBIT),
    REFUND(WalletEntryDirection.CREDIT),
    TRANSFER_IN(WalletEntryDirection.CREDIT),
    TRANSFER_OUT(WalletEntryDirection.DEBIT),
    ADJUSTMENT(WalletEntryDirection.CREDIT, WalletEntryDirection.DEBIT);

    private final Set<WalletEntryDirection> directions;

    WalletEntryType(WalletEntryDirection... directions) {
        this.directions = Set.of(directions);
    }

    public boolean supports(WalletEntryDirection direction) {
        return directions.contains(direction);
    }
}
