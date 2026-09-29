# Dependency and secret audit

Audit date: 2026-09-28
Scope: `frontend/`, `backend/`, Docker/CI configuration and tracked documentation.

## Findings

| Check | Evidence | Result |
| --- | --- | --- |
| npm dependency audit | `cd frontend && npm audit --json` | 604 dependency entries; 0 info, 0 low, 0 moderate, 0 high and 0 critical findings |
| Backend dependency/build gate | `cd backend && .\\mvnw.cmd clean verify` | 225 tests; 0 failures, 0 errors and 0 skipped (local evidence; rerun on the release commit in CI) |
| Tracked credential files | `git ls-files` filtered for `.env`, `.pem`, `.key`, `.p12`, `.jks` | No credential-like tracked file; only documented example templates are allowed |
| Repository secret scan | `gitleaks` | The workstation does not have the `gitleaks` executable installed. CI runs `gitleaks/gitleaks-action@v2`; a conservative tracked-file pattern scan found no credential material. |

The npm audit was run as a read-only report. No `npm audit fix --force` (or other
automatic dependency rewrite) was used. Findings are archived by the CI
`dependency-audit` job for every pull request and push to `main`.

## Interpretation and follow-up

- The audit is not proof that a provider credential, database password or JWT
  secret is safe if it is later placed in a local environment file. Production
  values must be injected by the deployment secret manager and rotated on the
  documented schedule.
- Test fixtures and the development catalog contain explicitly synthetic values;
  they are not sellable inventory or payment credentials. The development seed
  inventory is intentionally non-decryptable and must never be promoted to a
  production database.
- Dependabot is configured for npm and Maven updates. A dependency update still
  requires the backend, frontend, browser, security and image gates before merge.
- The first production release remains blocked until an operator runs the CI
  scanner on the final commit and explicitly accepts any new advisory that is
  not fixed or unreachable.
