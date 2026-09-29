package com.github.yildizmy.wallet;

import com.github.yildizmy.domain.entity.Wallet;

public record WalletView(long walletId, long balanceVnd, String currency) {

    public static WalletView from(Wallet wallet) {
        return new WalletView(wallet.getId(), wallet.getBalance().longValueExact(), "VND");
    }
}
