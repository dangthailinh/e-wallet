DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM wallet WHERE balance < 0 OR balance <> trunc(balance)) THEN
        RAISE EXCEPTION 'wallet balances must be non-negative whole VND before ledger migration';
    END IF;
END
$$;

ALTER TABLE wallet
    ALTER COLUMN balance TYPE NUMERIC(19, 0) USING balance::NUMERIC(19, 0),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_wallet_balance_non_negative CHECK (balance >= 0);

CREATE TABLE wallet_entries
(
    id                 UUID                     NOT NULL,
    wallet_id          BIGINT                   NOT NULL,
    direction          VARCHAR(8)               NOT NULL,
    entry_type         VARCHAR(32)              NOT NULL,
    amount_vnd         BIGINT                   NOT NULL,
    business_reference VARCHAR(160)             NOT NULL,
    business_label     VARCHAR(160)             NOT NULL,
    balance_after_vnd  BIGINT                   NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_wallet_entry PRIMARY KEY (id),
    CONSTRAINT fk_wallet_entry_wallet FOREIGN KEY (wallet_id) REFERENCES wallet (id) ON DELETE RESTRICT,
    CONSTRAINT uc_wallet_entry_business_reference UNIQUE (business_reference),
    CONSTRAINT ck_wallet_entry_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_wallet_entry_type CHECK (entry_type IN (
        'OPENING_BALANCE', 'DEPOSIT', 'PURCHASE', 'REFUND',
        'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT'
    )),
    CONSTRAINT ck_wallet_entry_amount_positive CHECK (amount_vnd > 0),
    CONSTRAINT ck_wallet_entry_balance_non_negative CHECK (balance_after_vnd >= 0),
    CONSTRAINT ck_wallet_entry_type_direction CHECK (
        entry_type = 'ADJUSTMENT'
        OR (direction = 'CREDIT' AND entry_type IN ('OPENING_BALANCE', 'DEPOSIT', 'REFUND', 'TRANSFER_IN'))
        OR (direction = 'DEBIT' AND entry_type IN ('PURCHASE', 'TRANSFER_OUT'))
    ),
    CONSTRAINT ck_wallet_entry_reference_trimmed CHECK (
        business_reference <> '' AND business_reference = btrim(business_reference)
    ),
    CONSTRAINT ck_wallet_entry_label_trimmed CHECK (
        business_label <> '' AND business_label = btrim(business_label)
    )
);

CREATE INDEX idx_wallet_entry_wallet_created ON wallet_entries (wallet_id, created_at DESC, id);
CREATE INDEX idx_wallet_entry_type_created ON wallet_entries (entry_type, created_at DESC, id);

CREATE FUNCTION reject_wallet_entry_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'wallet entries are immutable';
END
$$;

CREATE TRIGGER trg_wallet_entry_no_update
BEFORE UPDATE ON wallet_entries
FOR EACH ROW EXECUTE FUNCTION reject_wallet_entry_mutation();

CREATE TRIGGER trg_wallet_entry_no_delete
BEFORE DELETE ON wallet_entries
FOR EACH ROW EXECUTE FUNCTION reject_wallet_entry_mutation();
