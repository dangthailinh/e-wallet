# Implementation Plan: Digital Storefront + Wallet

**Status:** Awaiting plan approval

**Spec:** [`docs/spec-digital-storefront.md`](../docs/spec-digital-storefront.md)

**Branch:** `feature/digital-storefront`

**UI reference:** `vercel/commerce` commit `3761e52e60df9c6a316e067dbfd7032e494d3634` (MIT)

## 1. Overview

Transform the existing Spring Boot e-wallet into a production-oriented digital-goods storefront. The Spring Boot/PostgreSQL application remains the system of record for identity, wallet ledger, payments, catalog, encrypted inventory and orders. The current Create React App frontend is replaced with a selectively adapted Vercel Commerce storefront on Next.js 16.3.3, with Shopify/cart/physical-checkout concepts replaced by Spring REST APIs, wallet top-up and immediate wallet purchase.

The implementation is incremental. Every phase must leave the branch buildable and testable, and every risky boundary is proved with an automated test before dependent features are added.

## 2. Architecture Decisions

### 2.1 Modular monolith remains the transaction boundary

Spring Boot remains a modular monolith so wallet debit, inventory allocation and order creation can commit or roll back in one PostgreSQL transaction. No payment, wallet or inventory state is owned by Next.js.

### 2.2 Next.js is storefront/BFF, not a second backend

- Public Server Components call Spring REST endpoints through a typed server-only client.
- Authenticated server calls forward only the required cookies and correlation headers.
- Browser mutations use the same-origin `/api` path served by the reverse proxy; Route Handlers are added only where cookie/header mediation is required.
- Spring Boot performs final input validation, authorization and ownership checks.
- No Next.js code connects directly to PostgreSQL.

### 2.3 Selective Vercel Commerce adoption

Port the following concepts rather than copying the repository wholesale:

- Keep: App Router layout, Geist font, navbar/mobile sheet, search, product tiles, featured grid, carousel, gallery, price display, loading/error boundaries and footer.
- Adapt: collections to categories, Shopify product types to internal API contracts, cart modal to wallet drawer, product description to digital warranty/stock/buy flow.
- Remove: Shopify GraphQL provider, Shopify environment variables, variants, quantity editing, shipping, tax and external checkout URL.
- Add: auth, balance/top-up, deposit status, immediate buy confirmation, orders, secret reveal and admin routes.

Substantial reused code retains the Vercel MIT notice in `THIRD_PARTY_NOTICES.md`.

### 2.4 JWT retained with safer browser delivery

- Short-lived access JWT in `HttpOnly; Secure; SameSite=Lax` cookie.
- Rotating opaque refresh token; only a hash is persisted.
- CSRF protection for browser state-changing requests.
- Provider webhooks use a separate filter chain and provider signature verification, not user JWT or CSRF.
- Existing localStorage token code is removed after the new auth flow passes integration tests.

### 2.5 Immutable ledger with cached balance

`wallet_entries` is append-only and records every credit/debit. `wallets.balance` is a locked cached total for efficient checkout. A transaction writes the entry and balance together. There is no production endpoint that accepts a client-directed balance increase.

### 2.6 Encrypted inventory

Inventory plaintext is encrypted in the application service with AES-256-GCM before persistence. Ciphertext, nonce, tag and key version are stored in PostgreSQL; the master key comes from the runtime secret store. Reveal is an owner/admin operation with `no-store` response headers and audit logging.

### 2.7 Idempotency at database and service layers

- Deposits: unique provider transaction/event identifiers.
- Checkout: unique `(user_id, idempotency_key)`.
- Ledger: unique business reference.
- Duplicate requests return the prior result or no-op; they never create a second credit/debit.

## 3. Dependency Graph

```text
Baseline evidence + secret hygiene
            │
            ├── Backend/toolchain upgrade ── API error contract
            │              │
            │              ├── Auth cookies + refresh + CSRF
            │              │          │
            │              │          └── Auth/account UI
            │              │
            │              ├── Catalog schema/API ── Storefront UI
            │              │          │
            │              │          └── Encrypted inventory/admin import
            │              │
            │              └── Wallet ledger + row locks
            │                         │
            │                         ├── Deposit core ── MoMo
            │                         │
            │                         └── Atomic checkout
            │                                  │
            │                                  └── Orders + secret reveal UI
            │
            └── Next.js/Vercel UI foundation
                           │
                           └── Typed Spring API client

All vertical slices ── Admin operations ── Observability/security ── Deployment/certification
```

Sequential constraints:

1. Do not connect payment providers before the wallet ledger and webhook idempotency core exist.
2. Do not expose buy UI before the atomic checkout concurrency test passes.
3. Do not expose secret reveal before encryption, ownership and no-store tests pass.
4. Do not remove the old frontend/auth path until the new Next.js auth smoke test passes.
5. Do not enable production webhooks until the exact provider contract and sandbox certification are complete.

## 4. Implementation Phases

## Phase 0 — Baseline and safety net

### Work

- Record current backend tests, frontend build, Docker health and known failures.
- Inventory tracked secrets/config. Replace tracked `.env.properties` with a safe `.env.example` during implementation and document local secret creation.
- Add secret/build-output exclusions to `.gitignore`.
- Add Vercel Commerce attribution and record the exact source commit.
- Add a migration/data audit for current users and wallets before changing constraints.
- Establish conventional commit checkpoints; one verified concern per commit.

### Exit criteria

- Baseline report distinguishes pre-existing failures from regressions.
- No real secret is introduced into Git history or build output.
- Existing demo data is classified as disposable or migration-required before any destructive operation.

### Verification checkpoint

- `git status`, tracked-secret scan, backend test result, frontend build result and Docker smoke result are captured.

## Phase 1 — Toolchain and application foundations

### Backend foundation

- Upgrade Spring Boot 3.0.4 to 3.5.x and replace outdated JJWT usage with a supported API.
- Align managed dependency versions; remove explicit versions that conflict with the Boot BOM.
- Add Actuator, Testcontainers/PostgreSQL and test configuration.
- Introduce RFC-style `application/problem+json` errors and request correlation ids.
- Make API origin, cookie settings and Swagger exposure environment-specific.

### Frontend foundation

- Replace CRA scaffold with Next.js 16.3.3 App Router, React 19, TypeScript, Tailwind 4 and Geist.
- Port only the approved Vercel Commerce layout/components.
- Create server-only typed Spring API client and common contract/error types.
- Add Vitest, React Testing Library, Playwright, axe, ESLint and typecheck scripts.
- Add root loading, error and not-found boundaries so no route renders a blank screen.

### Exit criteria

- Backend starts on a clean database and existing core wallet tests pass after upgrade.
- Next.js home shell renders navbar/footer/loading/error states without Shopify configuration.
- Both projects build independently and under Docker Compose.

### Checkpoint A — Foundation

- Backend: `mvnw verify` passes.
- Frontend: lint, typecheck, unit test and production build pass.
- Browser smoke test has no console error or wrong-origin request.

## Phase 2 — Secure authentication vertical slice

### Work

- Add refresh-session migration, repository and rotation/revocation service.
- Issue access JWT and opaque refresh cookies with environment-correct flags.
- Configure Spring Security SPA CSRF and a separate webhook security chain.
- Add rate limits for signup/login/refresh and consistent auth errors.
- Implement `/auth/me`, `/auth/refresh`, `/auth/logout`, `/auth/csrf`.
- Port login/signup/account UI into Next.js and remove localStorage token use.
- Add protected-route behavior based on server-validated session, not a client-stored role.

### Exit criteria

- Login, refresh rotation and logout work through same-origin cookies.
- Reusing a rotated/revoked refresh token fails safely.
- Unsafe browser mutations without CSRF fail; valid SPA flow succeeds.
- Browser storage contains no JWT or refresh token.

### Checkpoint B — Identity

- Backend auth integration/security tests pass.
- Playwright covers signup, login, refresh, protected navigation and logout.
- Cookie flags are verified in a real browser for local and production profiles.

## Phase 3 — Catalog storefront vertical slice

### Work

- Add category/product Flyway migrations, entities, repositories and public read API.
- Add admin product/category command APIs with validation and audit events.
- Create deterministic development seed data containing realistic Vietnamese digital products.
- Replace Vercel Commerce Shopify data functions with the Spring API adapter.
- Build featured grid, carousel, search/category/sort, grid/list toggle and product detail.
- Preserve metadata/JSON-LD, responsive images and accessible loading/empty/error states.

### Exit criteria

- Admin/API can create a product and it appears in public storefront/search.
- Prices render as integer VND and stock is never supplied by the browser.
- Product URLs use stable slugs and return a real not-found state.

### Checkpoint C — Read-only store

- Catalog repository/controller contract tests pass.
- Storefront component and Playwright search/product-detail tests pass.
- Responsive/a11y check passes at 375, 768, 1024 and 1440 px.

## Phase 4 — Encrypted inventory vertical slice

### Work

- Add inventory tables, statuses and constraints.
- Implement AES-GCM envelope format, key versioning and strict configuration validation.
- Implement batch import service/API with per-row validation and duplicate detection.
- Build admin import/status UI; inventory list always returns masked metadata.
- Add owner/admin reveal service boundary but keep customer reveal route disabled until orders exist.
- Add log/error redaction tests and key-rotation runbook skeleton.

### Exit criteria

- Database contains no imported plaintext secret.
- Importing duplicate/invalid items returns actionable per-row results.
- Decryption fails closed on wrong key/tag/AAD and does not leak plaintext.

### Checkpoint D — Inventory vault

- Crypto test vectors, repository tests and admin import E2E pass.
- Automated scan confirms sample secrets are absent from DB dump and logs.

## Phase 5 — Wallet ledger and concurrency foundation

### Work

- Add immutable wallet entry schema, unique business references and wallet version/lock query.
- Audit existing wallet data and create an explicit migration/backfill path; do not collapse multiple legacy wallets without approval.
- Implement credit/debit domain service with positive-integer VND invariants.
- Disable/remove client-controlled add-funds and withdraw endpoints after compatibility review.
- Add wallet balance/ledger read API and adapt Vercel cart modal into wallet drawer.
- Add row-lock and concurrent debit tests using PostgreSQL Testcontainers.

### Exit criteria

- Every balance change has exactly one immutable ledger entry.
- Concurrent debits cannot make balance negative.
- No customer API can directly set or increment balance.

### Checkpoint E — Wallet core

- Ledger/backfill reconciliation equals the cached wallet balance.
- Concurrency suite passes repeatedly against PostgreSQL.
- Wallet drawer shows balance and paginated history without exposing internal identifiers.

## Phase 6 — Deposit core and MoMo sandbox

### Provider-neutral core

- Add deposit/webhook-inbox schema and state machine.
- Define payment provider ports for create payment, verify notification and query/reconcile.
- Implement raw-body capture, request-size limits, event hashing, idempotent processing and cleaned audit records.
- Add deposit create/status API and Next.js top-up flow with pending/success/failure/expired states.

### MoMo adapter

- Implement create-payment request and HMAC canonicalization from official MoMo documentation.
- Verify IPN signature and match partner code, order id, amount and transaction id.
- Process verified IPN into deposit transition + wallet credit in one database transaction.
- Add signed fixtures for valid, invalid, duplicate, mismatched and out-of-order notifications.

### Exit criteria

- MoMo sandbox creates a QR/pay URL and verified IPN credits exactly once.
- Redirect does not credit funds and only displays a pending verification state.
- Duplicate IPN calls return the provider-required response without duplicate ledger entries.

### Checkpoint F — MoMo top-up

- Provider contract and replay tests pass.
- Playwright covers create deposit → signed sandbox fixture → balance update.
- Sensitive merchant material is absent from logs, responses and Git.

## Phase 7 — Removed from scope: Bank/VietQR

Decision recorded 2026-09-26: the first release has one external payment method, MoMo. The customer API
rejects new `BANK` deposits and the UI offers no bank/VietQR option. Existing database compatibility is retained.

## Phase 8 — Atomic purchase, orders and secret delivery

### Work

- Add order/order-item schema and idempotency constraints.
- Write failing concurrency tests first: two buyers for the final item and repeated idempotency key.
- Implement fixed lock order: wallet, then next available inventory item.
- In one transaction create order snapshot, debit ledger and mark inventory sold.
- Replace `AddToCart` with buy-with-wallet dialog and server mutation.
- Build order history/detail and audited secret reveal/copy UI.
- Add `Cache-Control: no-store`, ownership checks and redaction around reveal.

### Exit criteria

- Exactly one request can buy the last item.
- Retry returns the same order and does not debit twice.
- Rollback restores wallet/inventory/order state together after an injected failure.
- Only the owner/admin can reveal; secret never enters persistent browser storage or email.

### Checkpoint H — Complete customer journey

- E2E: register → login → top up → browse → buy → order history → reveal → logout.
- Concurrency, authorization and no-store tests pass.
- Browser has no console error, blank state or leaked secret in storage.

## Phase 9 — Admin operations and reconciliation

### Work

- Build admin product/category/inventory/order/deposit/audit screens using the Vercel visual language.
- Add filters, pagination and stable deep links.
- Add webhook failure/review queue and retry/reconcile actions with strict authorization.
- Add low-stock, pending deposit and provider error indicators.
- Require explicit reason and audit record for any future financial adjustment flow; do not add arbitrary balance editing.

### Exit criteria

- Admin can operate catalog/inventory and investigate payments without database access.
- Customer role cannot access or infer admin resources.
- Every sensitive admin action appears in sanitized audit history.

### Checkpoint I — Operations UI

- Admin API authorization matrix passes.
- Playwright covers core admin flows and keyboard navigation.

## Phase 10 — Hardening, deployment and launch readiness

### Security/quality

- Rate-limit auth, reveal, checkout and webhooks; configure body/time limits.
- Add CSP, HSTS and other response headers at reverse proxy/application boundaries.
- Triage Maven/npm advisories without force-upgrading across breaking changes.
- Add dependency, secret and container scans to CI.
- Run full negative authorization, CSRF, webhook forgery and secret-leak suite.

### Observability/operations

- Add structured logs, request ids, metrics and health/readiness endpoints.
- Add alerts for invalid webhooks, stuck deposits, reconciliation mismatch, checkout failures and low stock.
- Add encrypted backup, restore drill, key rotation and provider reconciliation runbooks.

### Deployment

- Production Compose exposes only the TLS reverse proxy; DB/backend stay internal.
- Add staging with sandbox credentials and production environment validation.
- Run migration rehearsal against a database snapshot and document rollback.
- Execute provider certification and a small-value live transaction only after explicit approval.

### Exit criteria

- All spec acceptance criteria pass in staging.
- Production secrets are injected outside Git and validated at startup.
- Backup restore and rollback are demonstrated.
- Launch checklist identifies owner, monitoring and incident path.

### Checkpoint J — Release candidate

- Full backend/frontend/E2E/security pipeline passes.
- Production image/Compose smoke test passes from a clean machine/profile.
- Human review approves live provider enablement and deployment.

## 5. Verification Matrix

| Concern | Primary proof | Secondary proof |
|---|---|---|
| Schema/migrations | Testcontainers migration on clean DB | Snapshot migration rehearsal |
| Auth | Spring Security integration tests | Playwright cookie/CSRF flow |
| Catalog | API contract tests | Search/product E2E |
| Encryption | Crypto vectors + tamper tests | DB/log plaintext scan |
| Wallet | Ledger reconciliation | Concurrent debit tests |
| Webhooks | Signed provider fixtures | Sandbox callbacks/reconciliation |
| Checkout | Concurrent last-item test | Full customer E2E |
| Authorization | Role/ownership matrix | Cross-user/admin Playwright tests |
| UI | Component tests + production build | Responsive browser/axe checks |
| Deployment | Clean Compose smoke | Backup/restore and rollback drill |

## 6. Migration and Rollback Strategy

- Use additive Flyway migrations first; defer destructive cleanup of legacy tables/endpoints until the new flow is proven.
- Take a PostgreSQL snapshot before every production schema migration.
- Backfill ledger entries with deterministic unique references and reconcile totals before enabling writes.
- Put new provider callbacks on versioned endpoints; keep them disabled until credentials and signature fixtures validate.
- Cut frontend traffic to Next.js only after auth/catalog smoke tests pass; the Git branch remains the rollback point for the old frontend.
- Never roll back a database by editing an applied Flyway migration. Use forward repair migrations or restore a tested snapshot when required.

## 7. Commit Strategy

Each checkpoint is composed of smaller conventional commits, for example:

1. `docs: record storefront specification and implementation plan`
2. `chore: upgrade Spring Boot foundation`
3. `chore: migrate frontend shell to Next.js commerce`
4. `feat: add rotating cookie authentication`
5. `feat: add product catalog storefront`
6. `feat: encrypt digital inventory`
7. `feat: add immutable wallet ledger`
8. `feat: process idempotent MoMo deposits`
9. `feat: complete atomic wallet checkout`
10. `chore: harden production deployment`

Commits are made only after their focused verification passes and staged diffs are checked for secrets.

## 8. Parallelization Opportunities

No sub-agents are planned. If human-approved parallel work is introduced later:

- Safe after contracts are frozen: catalog UI and catalog backend tests; admin presentation and already-defined admin APIs; documentation/runbooks and implemented features.
- Must remain sequential: Flyway migrations, auth cookie contract, wallet ledger/backfill, checkout transaction logic and provider credential/config changes.
- Needs a merge checkpoint: any frontend/backend pair sharing DTOs or cookie behavior.

## 9. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Vercel source uses Next 15 canary APIs | High | Port selectively to Next 16.3.3; build/typecheck after each component group |
| Existing React UI and Next replacement diverge | Medium | Keep old branch history; establish new shell before removing old routes |
| Spring Boot/JJWT upgrade breaks auth | High | Upgrade/test foundation before changing token transport |
| Multiple legacy wallets conflict with one-wallet model | High | Audit and explicit migration decision; no destructive collapse |
| Lost update/double sale | Critical | PostgreSQL row locks, fixed lock order, unique constraints, repeated concurrency suite |
| Duplicate/forged webhook credits wallet | Critical | Raw-body signature verification, DB idempotency, amount/reference matching, replay tests |
| Secret leaks through DB/log/browser | Critical | AES-GCM, no-store, redaction tests, storage/DB/log scans |
| Next BFF weakens authorization | High | Spring remains final authority; forward minimum cookies/headers; cross-user tests |
| Dependency vulnerabilities persist | High | Migrate off CRA, use supported LTS versions, triage scans without force fixes |
| Production migration cannot be rolled back | High | Snapshot, rehearsal, additive migrations and documented restore path |

## 10. Decisions Needed Before Specific Gates

These do not block foundation/catalog/auth work:

1. Before branded UI polish: shop name, logo, domain and final copy.
2. Before refund implementation: replacement/refund policy for invalid credentials.
3. Before production deployment: secret manager/host choice and backup destination.
4. Before destructive legacy cleanup: whether current users/wallet balances are disposable demo data or must migrate.

## 11. Plan Approval Gate

After this plan is approved, create `tasks/todo.md` with session-sized tasks. Every task will include acceptance criteria, exact verification commands, dependencies and a target of no more than five touched files. Implementation begins only after that task list is reviewed and approved.
