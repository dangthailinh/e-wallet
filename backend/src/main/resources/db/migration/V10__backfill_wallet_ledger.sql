CREATE TABLE IF NOT EXISTS wallet_migration_audits
(
    id           UUID                     NOT NULL,
    issue_code   VARCHAR(48)              NOT NULL,
    user_id      BIGINT                   NOT NULL,
    wallet_count INTEGER                  NOT NULL,
    details      VARCHAR(500)             NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_wallet_migration_audit PRIMARY KEY (id),
    CONSTRAINT fk_wallet_migration_audit_user FOREIGN KEY (user_id) REFERENCES public."user" (id) ON DELETE RESTRICT,
    CONSTRAINT uc_wallet_migration_audit_issue_user UNIQUE (issue_code, user_id),
    CONSTRAINT ck_wallet_migration_audit_issue CHECK (issue_code IN ('MULTIPLE_LEGACY_WALLETS')),
    CONSTRAINT ck_wallet_migration_audit_count CHECK (wallet_count > 1)
);

INSERT INTO wallet_migration_audits (id, issue_code, user_id, wallet_count, details)
SELECT (
           substr(md5('multiple-legacy-wallets:' || user_id), 1, 8) || '-' ||
           substr(md5('multiple-legacy-wallets:' || user_id), 9, 4) || '-' ||
           substr(md5('multiple-legacy-wallets:' || user_id), 13, 4) || '-' ||
           substr(md5('multiple-legacy-wallets:' || user_id), 17, 4) || '-' ||
           substr(md5('multiple-legacy-wallets:' || user_id), 21, 12)
       )::UUID,
       'MULTIPLE_LEGACY_WALLETS',
       user_id,
       count(*)::INTEGER,
       'Legacy wallets were retained without merging; manual ownership review is required.'
FROM wallet
GROUP BY user_id
HAVING count(*) > 1
ON CONFLICT (issue_code, user_id) DO NOTHING;

INSERT INTO wallet_entries (
    id,
    wallet_id,
    direction,
    entry_type,
    amount_vnd,
    business_reference,
    business_label,
    balance_after_vnd,
    created_at
)
SELECT (
           substr(md5('wallet-opening:' || id), 1, 8) || '-' ||
           substr(md5('wallet-opening:' || id), 9, 4) || '-' ||
           substr(md5('wallet-opening:' || id), 13, 4) || '-' ||
           substr(md5('wallet-opening:' || id), 17, 4) || '-' ||
           substr(md5('wallet-opening:' || id), 21, 12)
       )::UUID,
       id,
       'CREDIT',
       'OPENING_BALANCE',
       balance::BIGINT,
       'wallet-opening:' || id,
       'Legacy opening balance',
       balance::BIGINT,
       CURRENT_TIMESTAMP
FROM wallet
WHERE balance > 0
ON CONFLICT (business_reference) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM wallet w
        LEFT JOIN (
            SELECT wallet_id,
                   sum(CASE WHEN direction = 'CREDIT' THEN amount_vnd ELSE -amount_vnd END) AS ledger_balance
            FROM wallet_entries
            GROUP BY wallet_id
        ) entries ON entries.wallet_id = w.id
        WHERE w.balance <> coalesce(entries.ledger_balance, 0)
    ) THEN
        RAISE EXCEPTION 'wallet ledger reconciliation failed after opening balance backfill';
    END IF;
END
$$;
