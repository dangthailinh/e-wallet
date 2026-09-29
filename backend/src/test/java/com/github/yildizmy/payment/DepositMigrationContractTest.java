package com.github.yildizmy.payment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DepositMigrationContractTest {

    @Test
    void depositMigrationDefinesPositiveMoneyOwnershipAndWebhookUniqueness() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V12__add_deposits.sql"
        )).toLowerCase(Locale.ROOT);

        assertThat(migration).contains(
                "create table payment_deposits",
                "create table webhook_events",
                "ck_deposit_amount_positive",
                "ck_deposit_currency_vnd",
                "needs_reconciliation_review",
                "fk_deposit_owned_wallet",
                "uc_deposit_merchant_reference",
                "uc_deposit_provider_transaction",
                "uc_webhook_provider_event",
                "uc_webhook_provider_transaction",
                "ck_webhook_request_hash_length",
                "trg_deposit_initial_state",
                "trg_deposit_status_transition",
                "trg_deposit_no_delete",
                "trg_webhook_initial_state",
                "trg_webhook_event_identity",
                "trg_webhook_event_no_delete"
        );
        assertThat(migration).doesNotContain("raw_payload", "secret_key", "access_key", "partner_secret");
    }
}
