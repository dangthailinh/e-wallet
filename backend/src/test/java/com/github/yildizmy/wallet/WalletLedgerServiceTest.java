package com.github.yildizmy.wallet;

import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.exception.InsufficientFundsException;
import com.github.yildizmy.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletLedgerServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletEntryRepository walletEntryRepository;

    private WalletLedgerService service;

    @BeforeEach
    void setUp() {
        service = new WalletLedgerService(walletRepository, walletEntryRepository);
    }

    @Test
    void creditsLockedWalletAndWritesMatchingEntry() {
        Wallet wallet = wallet(1L, 50_000);
        when(walletRepository.lockById(1L)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByBusinessReference("deposit:1")).thenReturn(Optional.empty());
        when(walletEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WalletLedgerService.Result result = service.credit(
                1L, 100_000, WalletEntryType.DEPOSIT, "deposit:1", "MoMo deposit"
        );

        assertThat(result.balanceAfterVnd()).isEqualTo(150_000);
        assertThat(result.created()).isTrue();
        assertThat(wallet.getBalance()).isEqualByComparingTo("150000");
        verify(walletRepository).save(wallet);
        verify(walletEntryRepository).save(any(WalletEntry.class));
    }

    @Test
    void returnsExistingEntryForAnIdenticalReference() {
        Wallet wallet = wallet(1L, 150_000);
        WalletEntry existing = new WalletEntry(
                wallet, WalletEntryDirection.CREDIT, WalletEntryType.DEPOSIT,
                100_000, "deposit:1", "MoMo deposit", 150_000
        );
        when(walletRepository.lockById(1L)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByBusinessReference("deposit:1")).thenReturn(Optional.of(existing));

        WalletLedgerService.Result result = service.credit(
                1L, 100_000, WalletEntryType.DEPOSIT, "deposit:1", "MoMo deposit"
        );

        assertThat(result.entryId()).isEqualTo(existing.getId());
        assertThat(result.created()).isFalse();
        verify(walletRepository, never()).save(any());
        verify(walletEntryRepository, never()).save(any());
    }

    @Test
    void rejectsReferenceReuseForADifferentOperation() {
        Wallet wallet = wallet(1L, 150_000);
        WalletEntry existing = new WalletEntry(
                wallet, WalletEntryDirection.CREDIT, WalletEntryType.DEPOSIT,
                100_000, "shared-ref", "Deposit", 150_000
        );
        when(walletRepository.lockById(1L)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByBusinessReference("shared-ref")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.debit(
                1L, 100_000, WalletEntryType.PURCHASE, "shared-ref", "Purchase"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different ledger operation");

        assertThat(wallet.getBalance()).isEqualByComparingTo("150000");
        verify(walletRepository, never()).save(any());
        verify(walletEntryRepository, never()).save(any());
    }

    @Test
    void debitsAvailableFunds() {
        Wallet wallet = wallet(1L, 50_000);
        when(walletRepository.lockById(1L)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByBusinessReference("purchase:2")).thenReturn(Optional.empty());
        when(walletEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WalletLedgerService.Result result = service.debit(
                1L, 20_000, WalletEntryType.PURCHASE, "purchase:2", "Order payment"
        );

        assertThat(result.balanceAfterVnd()).isEqualTo(30_000);
        assertThat(result.direction()).isEqualTo(WalletEntryDirection.DEBIT);
        assertThat(wallet.getBalance()).isEqualByComparingTo("30000");
    }

    @Test
    void insufficientDebitChangesNothing() {
        Wallet wallet = wallet(1L, 20_000);
        when(walletRepository.lockById(1L)).thenReturn(Optional.of(wallet));
        when(walletEntryRepository.findByBusinessReference("purchase:1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.debit(
                1L, 30_000, WalletEntryType.PURCHASE, "purchase:1", "Order payment"
        )).isInstanceOf(InsufficientFundsException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo("20000");
        verify(walletRepository, never()).save(any());
        verify(walletEntryRepository, never()).save(any());
    }

    private Wallet wallet(long id, long balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setBalance(BigDecimal.valueOf(balance));
        return wallet;
    }
}
