package com.github.yildizmy.payment;

import com.github.yildizmy.audit.AuditLogRepository;
import com.github.yildizmy.domain.entity.User;
import com.github.yildizmy.domain.entity.Wallet;
import com.github.yildizmy.wallet.WalletEntry;
import com.github.yildizmy.wallet.WalletEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceIT {

    @Mock
    private DepositRepository deposits;

    @Mock
    private WalletEntryRepository entries;

    @Mock
    private AuditLogRepository audits;

    @Test
    void detectsPaidDepositWithoutMatchingLedgerEntryAndDoesNotWriteLedger() {
        UUID depositId = UUID.randomUUID();
        Deposit deposit = org.mockito.Mockito.mock(Deposit.class);
        when(deposit.getId()).thenReturn(depositId);
        when(deposit.getAmountVnd()).thenReturn(100_000L);
        when(deposit.getStatus()).thenReturn(DepositStatus.PAID);
        when(deposit.isNeedsReconciliationReview()).thenReturn(false);
        when(deposits.findById(depositId)).thenReturn(Optional.of(deposit));
        when(entries.findByBusinessReference("deposit:" + depositId)).thenReturn(Optional.empty());

        ReconciliationService service = new ReconciliationService(
                deposits, entries, audits, List.of()
        );

        ReconciliationResult result = service.reconcile(42L, depositId, "request-reconcile-001");

        assertThat(result.mismatch()).isTrue();
        assertThat(result.reason()).isEqualTo("LEDGER_ENTRY_MISSING");
        assertThat(result.ledgerEntryPresent()).isFalse();
        verify(entries, never()).save(any());
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRepeatedProviderQueriesWithinRateWindowAndAuditsTheAttempt() {
        UUID depositId = UUID.randomUUID();
        Deposit deposit = org.mockito.Mockito.mock(Deposit.class);
        when(deposit.getId()).thenReturn(depositId);
        when(deposit.getProviderCode()).thenReturn("MOMO");
        when(deposit.getMerchantReference()).thenReturn("DEP_12345678");
        when(deposit.getAmountVnd()).thenReturn(100_000L);
        when(deposit.getCurrency()).thenReturn("VND");
        when(deposit.getExpiresAt()).thenReturn(Instant.parse("2026-09-29T00:00:00Z"));
        when(deposits.findById(depositId)).thenReturn(Optional.of(deposit));

        PaymentProvider provider = org.mockito.Mockito.mock(PaymentProvider.class);
        when(provider.providerCode()).thenReturn("MOMO");
        when(provider.requery(any(PaymentPreparation.class))).thenReturn(Optional.empty());

        ReconciliationService service = new ReconciliationService(
                deposits, entries, audits, List.of(provider)
        );

        service.requery(42L, depositId, "request-requery-001");
        service.requery(42L, depositId, "request-requery-002");
        service.requery(42L, depositId, "request-requery-003");
        assertThatThrownBy(() -> service.requery(42L, depositId, "request-requery-004"))
                .isInstanceOf(ReconciliationRateLimitException.class);
        verify(audits, org.mockito.Mockito.atLeastOnce()).saveAndFlush(any());
    }

    private Wallet wallet(long id, long balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setBalance(BigDecimal.valueOf(balance));
        User user = new User();
        user.setId(42L);
        wallet.setUser(user);
        return wallet;
    }
}
