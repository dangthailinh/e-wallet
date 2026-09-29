package com.github.yildizmy.wallet;

import org.springframework.data.domain.Page;

import java.util.List;

public record WalletEntriesPage(
        List<WalletEntryView> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {

    public static WalletEntriesPage from(Page<WalletEntryView> result) {
        return new WalletEntriesPage(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
