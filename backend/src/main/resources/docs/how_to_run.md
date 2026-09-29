# How to run

This repository contains the Spring Boot wallet/catalog backend and the Next.js
digital storefront. The commands below are safe for local development and do
not delete PostgreSQL data.

## Prerequisites

- Docker Desktop with the Linux engine running.
- Java 21 and the Maven wrapper for backend development.
- Node.js 20.19+ and npm for the storefront development server.

## Local development (Windows PowerShell)

1. Create a private environment file from the committed template. Keep the
   resulting file out of Git and replace every placeholder with a unique local
   value:

   ```powershell
   Copy-Item .env.example .env.local
   ```

2. Start only PostgreSQL. The base Compose file publishes port 5432 for local
   development; the existing named volume is preserved.

   ```powershell
   docker compose --env-file .env.local up -d db
   docker compose ps
   ```

3. Export the same values for Spring Boot. PowerShell does not automatically
   load `.env.local`; load the private file and map the Compose names to the
   lowercase Spring names:

   ```powershell
   foreach ($line in (Get-Content ..\.env.local)) {
     if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
       Set-Item ("Env:" + $matches[1]) $matches[2].Trim().Trim('"')
     }
   }
   $env:db_name = $env:DB_NAME
   $env:db_username = $env:DB_USERNAME
   $env:db_password = $env:DB_PASSWORD
   $env:jwt_secret = $env:JWT_SECRET
   $env:INVENTORY_ACTIVE_KEY_VERSION = $env:INVENTORY_ACTIVE_KEY_VERSION
   $env:INVENTORY_KEYRING = $env:INVENTORY_KEYRING
   $env:INVENTORY_CHECKSUM_KEY = $env:INVENTORY_CHECKSUM_KEY
   $env:MOMO_ENABLED = 'false'
   ```

   If the values are in a file rather than the current shell, load them with a
   private script or set them explicitly; never paste real values into source
   files or commit them.

4. Start the backend with the development profile. This profile adds synthetic
   catalog smoke data only; its inventory secrets are intentionally
   non-decryptable and must never be used as sellable production stock.

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```

   Verify it at `http://localhost:8080/actuator/health` and browse the public
   catalog at `http://localhost:8080/api/v1/products`.

5. In another terminal, start the Next storefront. Use another port if 3000 is
   already occupied by an unrelated local container:

   ```powershell
   cd frontend
   npm ci
   $env:BACKEND_ORIGIN = 'http://localhost:8080'
   $env:NEXT_PUBLIC_SITE_URL = 'http://localhost:3001'
   $env:PORT = '3001'
   npm run dev -- --hostname 0.0.0.0
   ```

   Open `http://localhost:3001`. The Next route `/api/v1/*` proxies to the
   backend, so browser requests remain same-origin.

## Production-like Compose graph

Build immutable images first, inject real values through a deployment secret
manager or a private `.env.production` file, and start the proxy graph:

```powershell
docker build --tag e-wallet-backend:local ./backend
docker build --tag e-wallet-frontend:local ./frontend
$env:BACKEND_IMAGE = 'e-wallet-backend:local'
$env:FRONTEND_IMAGE = 'e-wallet-frontend:local'
docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

Production Compose publishes only the Caddy proxy on ports 80/443. Backend and
PostgreSQL stay on the private Compose network. A real public deployment also
needs a DNS name, TLS/provider configuration and the approvals listed in
[`docs/release-checklist.md`](../../../../docs/release-checklist.md).

Do not delete containers, images or volumes as a troubleshooting step. Stop a
specific process or inspect its logs first; preserve non-empty databases and
use a disposable database for migration/restore rehearsals.

## Troubleshooting

- `couldn't find env file`: use `--env-file .env.local` or
  `--env-file .env.production` from the repository root. The backend's
  optional `.env.properties` is a separate Spring import and is not a Compose
  env file.
- Port 8080/3000 already in use: identify the owner with
  `Get-NetTCPConnection -LocalPort 8080,3000`; stop only the intended process,
  or choose a different local frontend port as shown above.
- Backend cannot authenticate to PostgreSQL: confirm the database role and
  password belong to the existing volume. Do not reset or remove the volume;
  repair the local credential or use a disposable database for a clean test.
- Check service logs without exposing secrets:

  ```powershell
  docker compose logs --tail 100 db
  Get-Content backend/logs/application.log -Tail 100
  ```

## Related documentation

- [`docs/release-checklist.md`](../../../../docs/release-checklist.md)
- [`docs/runbooks/backup-restore.md`](../../../../docs/runbooks/backup-restore.md)
- [`docs/runbooks/deploy-rollback.md`](../../../../docs/runbooks/deploy-rollback.md)
- [`docs/runbooks/incident-response.md`](../../../../docs/runbooks/incident-response.md)
