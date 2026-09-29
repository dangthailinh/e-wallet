# Release-candidate checklist

This checklist is the final gate for the Digital Storefront + Wallet release.
It separates evidence produced locally/CI from approvals that only an authorized
operator can provide.

## Automated gates

- [x] Backend: `cd backend; .\\mvnw.cmd clean verify` passes with zero failures,
  errors and skips locally: 225 tests, 0 failures, 0 errors, 0 skipped.
- [x] Frontend: `npm ci`, `npm run lint`, `npm run typecheck`,
  `npm test -- --run`, `npm run build` pass locally.
- [x] Browser: `npm run test:e2e -- --workers=2` passes locally (11 tests),
  including auth, duplicate checkout and protected secret-reveal coverage.
- [x] PostgreSQL migrations are applied to clean disposable Testcontainers
  databases and the schema is validated by the integration suite. Existing
  non-empty volumes are never reset as a substitute for migration verification.
- [ ] `npm audit --json`, the configured repository secret scanner and Trivy
  image scans report no unaccepted critical/high finding.
- [x] `docker compose config` is reviewed with disposable staging variables;
  only the TLS proxy publishes host ports in the production graph.

These local checks are evidence for the implementation, not a production
approval: the release commit still needs CI image/secret scans and the
staging/provider gates below.

## Operational rehearsal

- [ ] Build immutable backend/frontend images from the release commit and record
  their digests in the change ticket.
- [ ] Deploy to staging with `docker compose ... up --wait`; verify backend
  readiness, frontend health, Caddy routing, request IDs and security headers.
- [ ] Run the backup/restore drill from
  [`runbooks/backup-restore.md`](runbooks/backup-restore.md) against disposable
  staging data and attach the restore verification output.
- [ ] Exercise the rollback procedure and record the previous image digests.
- [ ] Configure alerts for readiness failures, webhook invalid/duplicate rates,
  checkout failures, low stock and deposit reconciliation mismatches.

## Human approval gates

- [ ] Provider owner certifies MoMo credentials, signature/IPN configuration and
  a publicly reachable HTTPS IPN endpoint.
- [ ] Finance/product owner approves the smallest live transaction and confirms
  exactly-once wallet crediting in provider and internal ledgers.
- [ ] Security owner approves production JWT/inventory keys, cookie policy,
  Caddy policy and secret rotation/incident contacts.
- [ ] Release owner approves enabling production webhooks and records the
  deployment window and rollback owner.

The repository intentionally does not claim these human/provider approvals from
local tests. Until the boxes above are checked, the code is implementation-ready
but not authorized for a public-money launch.
