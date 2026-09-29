# Wallet ledger migration

Flyway V9 introduces an append-only VND ledger, V10 creates deterministic
opening entries for balances held by the legacy wallet table, and V11 creates
empty wallets for legacy users who had none. New signups create their empty
wallet in the same transaction as the user.

## Backfill rules

- Every wallet with a positive cached balance receives exactly one
  `OPENING_BALANCE` credit. Its reference is `wallet-opening:<wallet-id>`, so a
  retry cannot duplicate money.
- A zero-balance wallet receives no monetary entry. This preserves the rule
  that every ledger amount is a positive whole VND value; its empty ledger sums
  to the cached zero balance.
- The migration compares the signed sum of entries with every cached wallet
  balance and aborts if any value differs.
- No wallet is merged or deleted. When a legacy user owns more than one wallet,
  V10 writes `MULTIPLE_LEGACY_WALLETS` to `wallet_migration_audits` for manual
  review. The audit deliberately contains identifiers and counts only, not
  credentials or payment secrets.
- V11 leaves every existing wallet untouched. A missing wallet receives a
  deterministic internal `EW` identifier and a cached zero balance, with no
  ledger entry. Rerunning the SQL does not create a second wallet.

## Operator checks

Before deploying a later migration that enforces one wallet per user, review:

```sql
SELECT issue_code, user_id, wallet_count, details, created_at
FROM wallet_migration_audits
ORDER BY created_at, user_id;
```

Reconciliation should always return zero rows:

```sql
SELECT w.id, w.balance, COALESCE(e.ledger_balance, 0) AS ledger_balance
FROM wallet w
LEFT JOIN (
    SELECT wallet_id,
           SUM(CASE WHEN direction = 'CREDIT' THEN amount_vnd ELSE -amount_vnd END) AS ledger_balance
    FROM wallet_entries
    GROUP BY wallet_id
) e ON e.wallet_id = w.id
WHERE w.balance <> COALESCE(e.ledger_balance, 0);
```

Resolve multiple-wallet audits through an explicitly approved data migration.
Do not delete, merge or reassign wallets by hand on a live database.
