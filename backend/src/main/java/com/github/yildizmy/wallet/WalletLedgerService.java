package com.github.yildizmy.wallet;

import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.exception.InsufficientFundsException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletLedgerService {

    private static final int MAX_TEXT_LENGTH = 160;

    private final WalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;

    @Transactional
    public Result credit(
            long walletId,
            long amountVnd,
            WalletEntryType type,
            String businessReference,
            String businessLabel
    ) {
        return post(
                walletId,
                WalletEntryDirection.CREDIT,
                type,
                amountVnd,
                businessReference,
                businessLabel
        );
    }

    @Transactional
    public Result debit(
            long walletId,
            long amountVnd,
            WalletEntryType type,
            String businessReference,
            String businessLabel
    ) {
        return post(
                walletId,
                WalletEntryDirection.DEBIT,
                type,
                amountVnd,
                businessReference,
                businessLabel
        );
    }

    private Result post(
            long walletId,
            WalletEntryDirection direction,
            WalletEntryType type,
            long amountVnd,
            String businessReference,
            String businessLabel
    ) {
        validateOperation(direction, type, amountVnd);
        String reference = requireText(businessReference, "businessReference");
        String label = requireText(businessLabel, "businessLabel");

        Wallet wallet = walletRepository.lockById(walletId)
                .orElseThrow(() -> new NoSuchElementFoundException("Wallet not found"));

        return walletEntryRepository.findByBusinessReference(reference)
                .map(entry -> existingResult(entry, walletId, direction, type, amountVnd))
                .orElseGet(() -> createEntry(wallet, direction, type, amountVnd, reference, label));
    }

    private Result createEntry(
            Wallet wallet,
            WalletEntryDirection direction,
            WalletEntryType type,
            long amountVnd,
            String reference,
            String label
    ) {
        long currentBalance = wallet.getBalance().longValueExact();
        long balanceAfter;
        if (direction == WalletEntryDirection.CREDIT) {
            balanceAfter = Math.addExact(currentBalance, amountVnd);
        } else {
            if (currentBalance < amountVnd) {
                throw new InsufficientFundsException("Insufficient wallet balance");
            }
            balanceAfter = currentBalance - amountVnd;
        }

        WalletEntry entry = new WalletEntry(
                wallet,
                direction,
                type,
                amountVnd,
                reference,
                label,
                balanceAfter
        );
        wallet.setBalance(BigDecimal.valueOf(balanceAfter));
        walletRepository.save(wallet);
        WalletEntry saved = walletEntryRepository.save(entry);
        return Result.from(saved, true);
    }

    private Result existingResult(
            WalletEntry entry,
            long walletId,
            WalletEntryDirection direction,
            WalletEntryType type,
            long amountVnd
    ) {
        boolean sameOperation = Objects.equals(entry.getWallet().getId(), walletId)
                && entry.getDirection() == direction
                && entry.getType() == type
                && entry.getAmountVnd() == amountVnd;
        if (!sameOperation) {
            throw new IllegalArgumentException("Business reference belongs to a different ledger operation");
        }
        return Result.from(entry, false);
    }

    private void validateOperation(
            WalletEntryDirection direction,
            WalletEntryType type,
            long amountVnd
    ) {
        Objects.requireNonNull(direction, "direction is required");
        Objects.requireNonNull(type, "type is required");
        if (!type.supports(direction)) {
            throw new IllegalArgumentException("entry type does not support direction");
        }
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
    }

    private String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName + " is required").trim();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must contain 1 to 160 characters");
        }
        return text;
    }

    public record Result(
            UUID entryId,
            long walletId,
            WalletEntryDirection direction,
            WalletEntryType type,
            long amountVnd,
            long balanceAfterVnd,
            String businessReference,
            boolean created
    ) {
        private static Result from(WalletEntry entry, boolean created) {
            return new Result(
                    entry.getId(),
                    entry.getWallet().getId(),
                    entry.getDirection(),
                    entry.getType(),
                    entry.getAmountVnd(),
                    entry.getBalanceAfterVnd(),
                    entry.getBusinessReference(),
                    created
            );
        }
    }
}
