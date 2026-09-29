# Incident response

This runbook covers suspected credential leakage, forged payment callbacks,
wallet/ledger mismatch, inventory disclosure, data loss and availability
incidents. The incident commander owns the timeline and assigns a separate
scribe/reviewer. Preserve evidence before making reversible containment
changes; never put secrets or plaintext digital goods into the incident room.

## Severity and first actions

| Severity | Examples | Target action |
| --- | --- | --- |
| SEV-1 | Unauthorized wallet credit/debit, key/JWT leak, customer secrets exposed, database loss | Page incident commander/security owner immediately; stop financial flows |
| SEV-2 | Repeated invalid webhooks, stuck deposits, broad checkout failure, proxy outage | Contain within 15 minutes; preserve logs and provider references |
| SEV-3 | Single-account error, low-stock alert, degraded non-critical admin page | Triage during business hours; add a ticket and monitor |

For SEV-1/SEV-2:

1. Open an incident record with UTC time, affected release, request IDs and
   last known good backup. Do not copy request bodies or authorization headers.
2. Notify the payment/provider and security contacts through the approved
   channel. Ask for provider-side callback replay/credential suspension only
   with the owner who controls that account.
3. Preserve sanitized application logs, proxy access logs and audit rows in
   write-once storage. Restrict access to the incident team.

## Containment actions

Use the smallest named Compose service; do not run broad prune/reset commands.

```powershell
$envFile = $env:PRODUCTION_ENV_FILE
docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml `
  stop backend
```

Stopping `backend` pauses checkout, deposit creation and webhook processing;
the proxy may continue serving a maintenance response. If only payment
callbacks are suspect, disable the configured MoMo webhook route through the
approved deployment/configuration mechanism and preserve the raw provider
event identifier for reconciliation. Never credit a wallet from a client
redirect or an unverified callback.

If a JWT or inventory key may have leaked, follow the key-rotation runbook:

- rotate JWT signing material with overlapping validation only for the planned
  window, then revoke old refresh sessions;
- add a new inventory AES-GCM key version while retaining every referenced
  old version, re-encrypt through the reviewed job, and remove an old key only
  after a zero-row/version check and restore rehearsal;
- invalidate/revoke the suspected provider credential through the provider's
  dashboard or support process; do not paste it into a ticket.

## Financial integrity checks

Export identifiers and totals only (never inventory plaintext):

```sql
SELECT w.id, w.balance,
       COALESCE(SUM(CASE WHEN e.direction = 'CREDIT'
                         THEN e.amount_vnd ELSE -e.amount_vnd END), 0) AS ledger_balance
FROM wallet w
LEFT JOIN wallet_entries e ON e.wallet_id = w.id
GROUP BY w.id, w.balance
HAVING w.balance <> COALESCE(SUM(CASE WHEN e.direction = 'CREDIT'
                                     THEN e.amount_vnd ELSE -e.amount_vnd END), 0);

SELECT status, COUNT(*)
FROM payment_deposits
GROUP BY status
ORDER BY status;

SELECT action, COUNT(*)
FROM audit_logs
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY action
ORDER BY action;
```

Any ledger mismatch is a release blocker. Freeze manual balance adjustments,
preserve the append-only ledger, and reconcile provider transaction IDs and
signed webhook evidence before considering a correction. Do not delete or
edit ledger rows.

## Recovery and rollback

- For a code/proxy regression, use the previous image tag in
  [deploy-rollback.md](deploy-rollback.md), then verify health and critical
  flows.
- For suspected data loss, restore an encrypted snapshot into a replacement
  staging database first. Capture Flyway validation and ledger reconciliation;
  production restore requires explicit owner approval and a maintenance
  window.
- For a stuck/duplicate deposit, keep the terminal provider state and
  idempotency record. Queue reconciliation/manual review rather than issuing
  a second credit.
- For an inventory disclosure, disable reveal/import operations, preserve
  audit evidence, rotate the affected key and notify impacted users according
  to the approved legal/privacy process.

## Closeout

The incident commander closes the event only after service health, error rate,
deposit reconciliation and checkout smoke tests are green for the agreed
monitoring window. The postmortem must include timeline, impact, detection,
containment, root cause, contributing controls, recovery evidence and owned
follow-up tasks with due dates. Remove temporary access and confirm that no
secret, token, signature or digital-goods plaintext remains in logs, tickets,
chat or local work directories.
