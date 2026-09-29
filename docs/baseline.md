# Pre-migration baseline

Captured on 2026-09-17 (Asia/Bangkok) from branch
`feature/digital-storefront`, after commit `76ade50` and before application code or
dependency changes.

This report is the comparison point for the digital-storefront migration. A failure
listed here is pre-existing and must not be attributed to later work unless its error
or scope changes.

## Toolchain

| Tool | Detected version | Target from approved spec |
| --- | --- | --- |
| Java | 17.0.18 | 21 |
| Node.js | 24.18.0 | Supported runtime for Next.js 16 |
| npm | 11.16.0 | Lockfile-managed |
| Docker CLI | 29.7.2 | Docker Engine was unavailable |

## Reproduction results

Run from the repository root unless the command begins with `cd`.

### Backend tests — PASS

```powershell
cd backend
.\mvnw.cmd test
```

Result: Maven exited 0. All 35 tests passed with 0 failures, 0 errors and 0
skipped. JaCoCo analyzed 60 classes. The first run populated the local Maven cache
and took about 90 seconds.

### Frontend tests — PRE-EXISTING ENVIRONMENT FAILURE

```powershell
cd frontend
npm test -- --watchAll=false
```

Result: exited 1 before running tests because `node_modules` is absent and Windows
could not resolve `react-scripts`. The repository does contain `package-lock.json`.
No frontend test result can be claimed from this checkout until dependencies are
installed.

### Frontend production build — PRE-EXISTING ENVIRONMENT FAILURE

```powershell
cd frontend
npm run build
```

Result: exited 1 for the same missing `react-scripts` installation. No build output
was produced.

### Compose status — PRE-EXISTING ENVIRONMENT FAILURE

```powershell
docker compose --env-file .env.properties \
  -f docker-compose.yml -f docker-compose.prod.yml ps
```

Result: exited 1 because the Docker Desktop Linux engine pipe did not exist. No
containers were inspected or changed. The state and importance of the named
PostgreSQL volume `ewallet_postgres_data` are therefore unknown; it must not be
deleted or migrated destructively without a later inspection and backup decision.

## Known pre-existing application and security issues

- The backend is Spring Boot 3.0.4 on Java 17; the approved migration target is a
  supported Spring Boot 3.5.x baseline on Java 21.
- The frontend is Create React App (`react-scripts` 5) with React 18 and MUI. An
  earlier install/audit in this workspace reported 72 dependency vulnerabilities:
  15 low, 19 moderate, 35 high and 3 critical. The lockfile will be replaced by the
  approved Next.js migration rather than force-upgrading incompatible transitive
  packages in place.
- A blank page was reported after successful login. The legacy application uses
  nested dashboard routes; this checkout cannot reproduce the browser symptom until
  dependencies are installed, so the report remains an open pre-existing defect.
- The frontend stores the authenticated user and bearer token in `localStorage`.
- The backend disables CSRF protection globally and accepts stateless bearer JWTs.
- The frontend hard-codes `http://localhost:8080/api/v1` as its API base URL.
- `.env.properties` is tracked and contains development database and JWT example
  values. Treat every committed value as compromised; Task 02 removes the file from
  tracking and introduces placeholder-only configuration.

## Regression rule

Subsequent checkpoints must compare against this report:

- backend behavior starts from 35 passing tests;
- frontend tests/build have no passing baseline because dependencies are absent;
- Docker/runtime behavior has no passing baseline because the engine is unavailable;
- data-volume preservation remains mandatory until its contents can be inspected.
