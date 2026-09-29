package com.github.yildizmy.wallet;

import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final WalletRepository wallets;
    private final WalletEntryRepository entries;

    @Transactional(readOnly = true)
    public WalletView currentWallet(long userId) {
        return WalletView.from(singleWallet(userId));
    }

    @Transactional(readOnly = true)
    public Page<WalletEntryView> entries(long userId, int page, int size) {
        Wallet wallet = singleWallet(userId);
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return entries.findByWalletId(wallet.getId(), pageable).map(WalletEntryView::from);
    }

    private Wallet singleWallet(long userId) {
        List<Wallet> owned = wallets.findByUserId(userId);
        if (owned.isEmpty()) {
            throw new NoSuchElementFoundException("Wallet not found");
        }
        if (owned.size() > 1) {
            throw new ElementAlreadyExistsException("Multiple legacy wallets require manual review");
        }
        return owned.getFirst();
    }
}
