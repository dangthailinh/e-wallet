# Deployment and rollback

This runbook applies to a tagged release of the storefront. Production uses
the reverse proxy as its only public entry point; PostgreSQL and Spring stay
on the private Compose network. Production credentials and the environment
file are supplied by the deployment secret manager, never by Git.

## Pre-deploy checklist

1. Confirm the release tag, image digests, reviewer and maintenance window.
2. Confirm CI passed backend verify, frontend lint/typecheck/unit/build,
   Playwright smoke, dependency/secret scans and image scans.
3. Take and verify a PostgreSQL backup using
   [backup-restore.md](backup-restore.md). Record the immutable object ID.
4. Review every Flyway migration. Migrations are additive and forward-only;
   never edit a migration that has already been applied.
5. Validate the production Compose graph without starting it:

   ```powershell
   docker compose --env-file $env:PRODUCTION_ENV_FILE `
     -f docker-compose.yml -f docker-compose.prod.yml config --quiet
   ```

   Set `BACKEND_IMAGE` and `FRONTEND_IMAGE` to immutable registry references
   (prefer digests) in the deployment environment before validation. The only
   host bindings in the rendered graph must be `80:80` and
   `443:443` on `proxy`. Verify that `db` and `backend` have no `ports` entry.
6. Confirm `PUBLIC_DOMAIN` resolves to the host and the Caddy data volume is
   persistent. The first HTTPS certificate issuance requires outbound access
   to the configured ACME provider.

## Deploy

```powershell
$ErrorActionPreference = 'Stop'
$envFile = $env:PRODUCTION_ENV_FILE

docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml `
  pull
docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml `
  up -d --wait
docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml `
  ps

# Smoke-test through the public proxy, not the private service ports.
Invoke-WebRequest -UseBasicParsing "https://$env:PUBLIC_DOMAIN/" | Select-Object StatusCode
Invoke-WebRequest -UseBasicParsing "https://$env:PUBLIC_DOMAIN/actuator/health" | Select-Object StatusCode
```

The second request must return a sanitized `200` health response. Do not
expose actuator details or configuration values at the proxy. Run a login,
catalog, deposit-status and wallet purchase smoke test with disposable
staging/sandbox accounts before enabling live traffic.

## Application rollback

Rollback the application image first when code, UI or proxy behavior is bad.
Keep the database at the current schema when the previous application is
backward-compatible with it (the normal additive migration case).

```powershell
$ErrorActionPreference = 'Stop'
$envFile = $env:PRODUCTION_ENV_FILE
# Set BACKEND_IMAGE and FRONTEND_IMAGE to the previous immutable image
# references in the deployment system, then restart only the named application
# services. Do not alter the database volume.
docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml `
  pull backend frontend
docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml `
  up -d --no-deps --force-recreate backend frontend proxy
docker compose --env-file $envFile -f docker-compose.yml -f docker-compose.prod.yml `
  ps
```

Verify health, error rate, latency and the critical customer journey for at
least 15 minutes. Record the trigger, previous/current tags and evidence. Do
not use `git reset --hard` on a deployment host.

## Migration failure or incompatible schema

- Stop promotion and preserve logs, request IDs and the backup object ID.
- If the migration did not commit, fix forward with a new reviewed migration
  after restoring staging evidence. Never modify `flyway_schema_history` by
  hand and never edit an applied migration.
- If data integrity is at risk, put the site in maintenance mode, disable
  checkout/webhook processing, and obtain explicit owner approval to restore a
  tested snapshot into a replacement database. Restore verification is
  documented in [backup-restore.md](backup-restore.md).
- Repoint the application only after ledger reconciliation, migration
  validation and authorized secret smoke tests pass.

## Post-deploy evidence

Record release tag/image digests, migration version, backup object ID, health
responses, monitoring window, reviewer and any rollback decision. Never put
cookies, JWTs, provider signatures, inventory plaintext or encryption keys in
the evidence bundle.
