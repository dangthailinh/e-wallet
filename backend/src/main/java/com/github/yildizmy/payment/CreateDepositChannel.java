package com.github.yildizmy.payment;

/** Payment channels currently available to customer-created deposits. */
public enum CreateDepositChannel {
    MOMO;

    public DepositChannel toDomain() {
        return DepositChannel.MOMO;
    }
}
