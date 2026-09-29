package com.github.yildizmy.payment;

import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.repository.UserRepository;
import com.github.yildizmy.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepositPersistenceService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final DepositRepository deposits;

    @Transactional
    public Deposit create(
            long userId,
            DepositChannel channel,
            String providerCode,
            String merchantReference,
            long amountVnd,
            Instant expiresAt
    ) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NoSuchElementFoundException("User not found"));
        List<Wallet> ownedWallets = wallets.lockAllByUserId(userId);
        if (ownedWallets.isEmpty()) {
            throw new NoSuchElementFoundException("Wallet not found");
        }
        if (ownedWallets.size() > 1) {
            throw new ElementAlreadyExistsException("Multiple legacy wallets require manual review");
        }
        Deposit deposit = new Deposit(
                user,
                ownedWallets.getFirst(),
                channel,
                providerCode,
                merchantReference,
                amountVnd,
                expiresAt
        );
        return deposits.saveAndFlush(deposit);
    }

    @Transactional(readOnly = true)
    public Deposit ownedBy(long userId, UUID depositId) {
        return deposits.findByIdAndUserId(depositId, userId)
                .orElseThrow(() -> new NoSuchElementFoundException("Deposit not found"));
    }
}
