# PostgreSQL backup and restore

This runbook is the operational contract for the wallet database. It is
written for a staging rehearsal first and for production only after the
operator, backup destination and secret-manager owners are recorded in the
change ticket. Never paste a password, JWT key, inventory key or provider
credential into a command, log, ticket or this repository.

## Controls

- Backups are PostgreSQL custom-format dumps encrypted before they leave the
  host. The encryption key is held by the configured KMS/secret manager; the
  database password is injected through a short-lived secret or `.pgpass`
  file with mode `0600`.
- Retain daily backups for 35 days, weekly backups for 12 weeks and monthly
  backups for 13 months. The backup bucket must have versioning, server-side
  encryption, object-lock/immutability for the retention window and access
  logging enabled.
- Keep at least one recent backup in a second failure domain. Do not copy a
  plaintext dump to a developer workstation.
- Verify every dump immediately (checksum and `pg_restore --list`) and run a
  staging restore at least monthly and before a destructive migration.

## Create an encrypted backup

The commands below deliberately use placeholders. Set them from the secret
manager in the operator's process; do not commit an environment file.

```powershell
$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $env:EWALLET_REPO_ROOT 'docker-compose.yml'
$backupRoot = Join-Path $env:EWALLET_BACKUP_ROOT 'ewallet'
$stamp = Get-Date -AsUTC -Format 'yyyyMMdd-HHmmss'
$workDir = Join-Path $backupRoot $stamp
New-Item -ItemType Directory -Path $workDir -Force | Out-Null

# The DB service reads its password from the Compose secret environment. The
# dump is written to the container's /tmp and copied only to the scoped folder.
docker compose -f $composeFile exec -T db pg_dump `
  --format=custom --no-owner --no-acl --file=/tmp/ewallet-$stamp.dump `
  --username=$env:DB_USERNAME --dbname=$env:DB_NAME
docker compose -f $composeFile cp db:/tmp/ewallet-$stamp.dump `
  (Join-Path $workDir 'ewallet.dump')
docker compose -f $composeFile exec -T db rm -f /tmp/ewallet-$stamp.dump

Get-FileHash (Join-Path $workDir 'ewallet.dump') -Algorithm SHA256 |
  ConvertTo-Json | Set-Content (Join-Path $workDir 'SHA256.json') -Encoding UTF8

# `age` is an example envelope tool. The recipient comes from the KMS
# integration; never replace it with a private key or a literal secret.
age -r $env:BACKUP_AGE_RECIPIENT `
  -o (Join-Path $workDir 'ewallet.dump.age') `
  (Join-Path $workDir 'ewallet.dump')
Remove-Item -LiteralPath (Join-Path $workDir 'ewallet.dump')

# Upload only the encrypted object and checksum/metadata to the immutable
# backup bucket. Use the provider's authenticated CLI, never an anonymous URL.
backup-cli upload --source (Join-Path $workDir 'ewallet.dump.age') `
  --destination "$env:BACKUP_BUCKET/ewallet/$stamp/ewallet.dump.age"
backup-cli upload --source (Join-Path $workDir 'SHA256.json') `
  --destination "$env:BACKUP_BUCKET/ewallet/$stamp/SHA256.json"
```

Record the dump timestamp, migration version, object version ID, SHA-256,
operator and expiration date in the change ticket. The record must contain no
database password or encryption key.

## Restore rehearsal (staging only)

A restore is never run against production in place. Provision a disposable
staging database with a name and host explicitly matching the staging profile,
then require a second operator to approve the target. The guard below prevents
an accidental production restore.

```powershell
$ErrorActionPreference = 'Stop'
if ($env:RESTORE_TARGET -ne 'staging') { throw 'RESTORE_TARGET must be exactly staging' }
if ($env:ALLOW_STAGING_RESTORE -ne 'I_HAVE_VERIFIED_STAGING') {
  throw 'Set the explicit staging restore confirmation before continuing'
}

$restoreDir = Join-Path $env:EWALLET_BACKUP_ROOT 'restore-work'
New-Item -ItemType Directory -Path $restoreDir -Force | Out-Null
backup-cli download --source "$env:BACKUP_OBJECT" `
  --destination (Join-Path $restoreDir 'ewallet.dump.age')
age --decrypt -i $env:BACKUP_AGE_IDENTITY `
  -o (Join-Path $restoreDir 'ewallet.dump') `
  (Join-Path $restoreDir 'ewallet.dump.age')

# The staging profile points to a disposable DB service, never the live one.
docker compose --env-file $env:STAGING_ENV_FILE `
  -f (Join-Path $env:EWALLET_REPO_ROOT 'docker-compose.yml') `
  up -d db
docker compose --env-file $env:STAGING_ENV_FILE `
  -f (Join-Path $env:EWALLET_REPO_ROOT 'docker-compose.yml') `
  cp (Join-Path $restoreDir 'ewallet.dump') db:/tmp/restore.dump
docker compose --env-file $env:STAGING_ENV_FILE `
  -f (Join-Path $env:EWALLET_REPO_ROOT 'docker-compose.yml') `
  exec -T db pg_restore --clean --if-exists --no-owner --no-acl `
  --dbname=$env:DB_NAME /tmp/restore.dump
```

After restore, run Flyway validation and the reconciliation queries below.
The restore is successful only when migrations are current, the wallet ledger
has no balance mismatch, all `PAID` deposits have at most one credit entry and
encrypted inventory can be revealed by an authorized test account.

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;

SELECT w.id, w.balance,
       COALESCE(SUM(CASE WHEN e.direction = 'CREDIT'
                         THEN e.amount_vnd ELSE -e.amount_vnd END), 0) AS ledger_balance
FROM wallet w
LEFT JOIN wallet_entries e ON e.wallet_id = w.id
GROUP BY w.id, w.balance
HAVING w.balance <> COALESCE(SUM(CASE WHEN e.direction = 'CREDIT'
                                     THEN e.amount_vnd ELSE -e.amount_vnd END), 0);

SELECT reference, COUNT(*)
FROM wallet_entries
GROUP BY reference
HAVING COUNT(*) > 1;
```

Destroy only the named disposable staging resources after evidence is
captured. Do not run `docker system prune`, recursive deletion, or a wildcard
database drop as part of this runbook.

## Monthly drill record

| Field | Value |
| --- | --- |
| Date/time (UTC) | _record in change ticket_ |
| Backup object/version | _record object ID only_ |
| SHA-256 verified | yes/no |
| Restore target | _staging database identifier_ |
| Flyway version | _version_ |
| Ledger reconciliation | zero rows / exception |
| Authorized reveal smoke test | pass/fail |
| Operator + reviewer | _names_ |
| Follow-up ticket | _ID_ |
