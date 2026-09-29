package com.github.yildizmy.wallet;

import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletQueryServiceTest {

    @Mock
    private WalletRepository wallets;

    @Mock
    private WalletEntryRepository entries;

    private WalletQueryService service;

    @BeforeEach
    void setUp() {
        service = new WalletQueryService(wallets, entries);
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersBalance() {
        Wallet wallet = wallet(12L, 45_000);
        when(wallets.findByUserId(7L)).thenReturn(List.of(wallet));

        WalletView view = service.currentWallet(7L);

        assertThat(view.walletId()).isEqualTo(12L);
        assertThat(view.balanceVnd()).isEqualTo(45_000L);
        assertThat(view.currency()).isEqualTo("VND");
        verify(wallets).findByUserId(7L);
    }

    @Test
    void returnsNewestEntriesWithoutBusinessReferencesOrEntityDetails() {
        Wallet wallet = wallet(12L, 45_000);
        WalletEntry entry = new WalletEntry(
                wallet, WalletEntryDirection.CREDIT, WalletEntryType.DEPOSIT,
                5_000, "momo:private-provider-reference", "MoMo deposit", 45_000
        );
        when(wallets.findByUserId(7L)).thenReturn(List.of(wallet));
        when(entries.findByWalletId(eq(12L), any())).thenReturn(new PageImpl<>(List.of(entry)));

        var page = service.entries(7L, 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().businessLabel()).isEqualTo("MoMo deposit");
        assertThat(page.getContent().getFirst().amountVnd()).isEqualTo(5_000L);
        assertThat(page.getContent().getFirst().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("businessReference", "wallet", "userId");
        verify(entries).findByWalletId(12L, PageRequest.of(
                0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        ));
    }

    @Test
    void refusesMissingOrAmbiguousLegacyWalletOwnership() {
        when(wallets.findByUserId(7L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.currentWallet(7L))
                .isInstanceOf(NoSuchElementFoundException.class);

        when(wallets.findByUserId(7L)).thenReturn(List.of(wallet(12L, 1), wallet(13L, 2)));
        assertThatThrownBy(() -> service.currentWallet(7L))
                .isInstanceOf(ElementAlreadyExistsException.class);
    }

    @Test
    void stripsMarkupAndControlsFromBusinessLabel() {
        Wallet wallet = wallet(12L, 45_000);
        WalletEntry entry = new WalletEntry(
                wallet, WalletEntryDirection.CREDIT, WalletEntryType.DEPOSIT,
                5_000, "deposit:2", "<b>MoMo</b>\n", 45_000
        );

        WalletEntryView view = WalletEntryView.from(entry);

        assertThat(view.businessLabel()).isEqualTo("bMoMo/b");
    }

    private Wallet wallet(long id, long balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setBalance(BigDecimal.valueOf(balance));
        return wallet;
    }
}
