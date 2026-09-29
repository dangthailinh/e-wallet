# Task Checklist: Digital Storefront + Wallet

**Status:** Approved; implementation in progress

**Spec:** [`docs/spec-digital-storefront.md`](../docs/spec-digital-storefront.md)

**Plan:** [`tasks/plan.md`](plan.md)

## Working agreement

- Execute tasks in numeric order unless dependencies explicitly allow otherwise.
- Start each behavior change with a failing test where practical.
- A checkbox is completed only after its verification commands pass.
- Touch no more than five planned files per task; split the task if discovery requires more.
- Review staged diff and scan for secrets before each commit.
- Use conventional commits and keep refactors separate from behavior changes.
- Update this checklist and the spec when an approved design decision changes.
- Never place merchant credentials, JWT keys, encryption keys or production passwords in this file, Git, logs or fixtures.

## Phase 0 — Baseline and safety

## Task 01 — Capture reproducible baseline

**Description:** Record the current backend, frontend and Docker results before changing dependencies.

**Acceptance criteria:**
- [x] Report records exact commands, pass/fail status and known blank-screen/dependency issues.
- [x] Pre-existing failures are distinguishable from later regressions.

**Verification:**
- [x] `cd backend; .\mvnw.cmd test` — 35 tests passed.
- [x] `cd frontend; npm test -- --watchAll=false; npm run build` — executed; missing installed dependencies is recorded as the pre-existing baseline.
- [x] `docker compose --env-file .env.properties -f docker-compose.yml -f docker-compose.prod.yml ps` — executed; unavailable Docker engine is recorded as the pre-existing baseline.

**Dependencies:** None

**Files likely touched:** `docs/baseline.md`

**Estimated scope:** S (1 file)

## Task 02 — Remove tracked development secrets

**Description:** Replace tracked `.env.properties` with a safe example and environment-driven Compose configuration.

**Acceptance criteria:**
- [x] `.env.properties` is no longer tracked and `.env.example` contains placeholders only.
- [x] Local Compose configuration resolves when a developer copies the example to an ignored `.env.local`.
- [x] Git ignores all real environment files, key files and build output.

**Verification:**
- [x] `git ls-files | Select-String -Pattern '^\.env|\.pem$|\.key$'` returns only approved examples after staging.
- [x] `docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.prod.yml config --quiet` (the same placeholder schema copied to `.env.local`) exits 0.

**Dependencies:** 01

**Files likely touched:** `.env.properties`, `.env.example`, `.gitignore`, `docker-compose.yml`, `docker-compose.prod.yml`

**Estimated scope:** M (5 files)

## Task 03 — Add third-party attribution

**Description:** Record Vercel Commerce source, commit and MIT terms before porting components.

**Acceptance criteria:**
- [x] Vercel copyright/license text and source commit are included.
- [x] Project license remains clearly distinguishable from third-party notices.

**Verification:**
- [x] `Select-String -Path THIRD_PARTY_NOTICES.md -Pattern 'Vercel|3761e52|MIT'`

**Dependencies:** 01

**Files likely touched:** `THIRD_PARTY_NOTICES.md`

**Estimated scope:** S (1 file)

## Checkpoint 0 — Clean starting point

- [x] Baseline report is reviewable.
- [x] Compose config resolves with ignored local environment values.
- [x] No credential-like file is staged.
- [x] Commit: `chore: establish safe project baseline`

## Phase 1 — Supported toolchains and app shells

## Task 04 — Upgrade Spring Boot dependency baseline

**Description:** Move backend to Spring Boot 3.5.x/Java 21 and align dependencies with the Boot BOM.

**Acceptance criteria:**
- [x] Backend compiles on Java 21 without the old JAXB/JJWT workaround.
- [x] Managed dependencies do not override the Boot BOM unnecessarily.
- [x] Existing unit tests pass and focused JWT upgrade tests cover round-trip and weak-key rejection.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -DskipTests compile`
- [x] `cd backend; .\mvnw.cmd clean test` — 37 tests passed on Java 21.

**Dependencies:** 02

**Files likely touched:** `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/test/java/com/github/yildizmy/EWalletApplicationTests.java`

**Estimated scope:** M (3 files)

## Task 05 — Add PostgreSQL integration-test harness

**Description:** Configure Testcontainers and a reusable Spring integration-test base.

**Acceptance criteria:**
- [ ] Tests start a real PostgreSQL container dynamically.
- [ ] Flyway runs from an empty database during integration tests.
- [ ] Tests do not depend on the developer's local port 5432.

**Verification:**
- [ ] `cd backend; .\mvnw.cmd -Dtest=PostgresIntegrationTest test`

**Runtime verification note (2026-09-17):** Harness compiles and the command exits 0,
but the test is skipped because Docker Desktop did not expose a daemon after startup.
Keep the acceptance and verification boxes open until the PostgreSQL 17 container and
Flyway migrations execute for real.

**Dependencies:** 04

**Files likely touched:** `backend/pom.xml`, `backend/src/test/resources/application-test.yml`, `backend/src/test/java/com/github/yildizmy/support/PostgresIntegrationTest.java`

**Estimated scope:** M (3 files)

## Task 06 — Standardize API errors and request IDs

**Description:** Return `application/problem+json` and attach a correlation ID to responses/log context.

**Acceptance criteria:**
- [x] Validation and domain errors expose stable error codes without stack traces.
- [x] Every response contains/echoes a request ID.
- [x] Error details do not expose secrets or SQL internals.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=GlobalExceptionHandlerTest,RequestIdFilterTest test` — 5 tests passed.

**Dependencies:** 04

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/exception/ErrorResponse.java`, `backend/src/main/java/com/github/yildizmy/exception/GlobalExceptionHandler.java`, `backend/src/main/java/com/github/yildizmy/config/RequestIdFilter.java`, `backend/src/test/java/com/github/yildizmy/exception/GlobalExceptionHandlerTest.java`, `backend/src/test/java/com/github/yildizmy/config/RequestIdFilterTest.java`

**Estimated scope:** M (5 files)

## Task 07 — Replace CRA configuration with Next.js

**Description:** Establish Next.js 16.3.3, TypeScript, Tailwind 4 and reproducible npm scripts.

**Acceptance criteria:**
- [x] `npm ci`, lint, typecheck and production build scripts exist.
- [x] No `react-scripts` dependency remains from the npm dependency graph.
- [x] Next config contains only required settings and no Shopify/Vercel account dependency.

**Verification:**
- [x] `cd frontend; npm ci; npm run typecheck; npm run build` — all exit 0 on Node 24.18.0.

**Dependencies:** 02, 03

**Files likely touched:** `frontend/package.json`, `frontend/package-lock.json`, `frontend/tsconfig.json`, `frontend/next.config.ts`, `frontend/postcss.config.mjs`

**Estimated scope:** M (5 files)

## Task 08 — Port the Vercel Commerce application shell

**Description:** Add Geist, global theme, root layout and explicit error/not-found states.

**Acceptance criteria:**
- [x] Root page renders without Shopify environment variables.
- [x] Dark/light system theme remains legible.
- [x] Runtime errors show a Vietnamese retry state instead of a blank page.

**Verification:**
- [x] `cd frontend; npm run build`
- [x] Manual: open `/`, an unknown route and an intentionally failed test route.

**Dependencies:** 07

**Files likely touched:** `frontend/src/app/layout.tsx`, `frontend/src/app/page.tsx`, `frontend/src/app/globals.css`, `frontend/src/app/error.tsx`, `frontend/src/app/not-found.tsx`

**Estimated scope:** M (5 files)

## Task 09 — Add frontend test and browser harness

**Description:** Configure Vitest/RTL, Playwright and axe for Next.js.

**Acceptance criteria:**
- [x] Unit DOM environment and shared test setup work.
- [x] Playwright boots the app and runs an accessibility smoke test.
- [x] CI-friendly non-watch scripts are available.

**Verification:**
- [x] `cd frontend; npm test`
- [x] `cd frontend; npm run test:e2e -- --grep "shell"`

**Dependencies:** 07, 08

**Files likely touched:** `frontend/vitest.config.ts`, `frontend/playwright.config.ts`, `frontend/test/setup.ts`, `frontend/e2e/shell.spec.ts`, `frontend/package.json`

**Estimated scope:** M (5 files)

## Checkpoint A — Supported foundations

- [x] `cd backend; .\mvnw.cmd verify`
- [x] `cd frontend; npm run lint; npm run typecheck; npm test; npm run build`
- [x] Shell browser test has no console errors.
- [x] Commit backend and frontend foundations separately.

## Phase 2 — Secure authentication slice

## Task 10 — Persist refresh sessions

**Description:** Add the refresh-session schema, entity and repository using hashed tokens.

**Acceptance criteria:**
- [x] Database never stores the raw refresh token.
- [x] Session family, expiry, rotation and revocation fields are constrained/indexed.
- [x] Repository can atomically locate an active hash.

**Verification:**
- [ ] `cd backend; .\mvnw.cmd -Dtest=RefreshSessionRepositoryIT test`

Runtime verification note (2026-09-17): Maven discovers all 8 PostgreSQL integration tests, but they are skipped because no Docker engine is available on this machine. Unit and regression tests pass; keep this verification open until the suite executes against PostgreSQL.

**Dependencies:** 05

**Files likely touched:** `backend/src/main/resources/db/migration/V6__add_refresh_sessions.sql`, `backend/src/main/java/com/github/yildizmy/auth/RefreshSession.java`, `backend/src/main/java/com/github/yildizmy/auth/RefreshSessionRepository.java`, `backend/src/test/java/com/github/yildizmy/auth/RefreshSessionRepositoryIT.java`

**Estimated scope:** M (4 files)

## Task 11 — Implement JWT access and refresh rotation service

**Description:** Replace old JJWT calls and implement access JWT issuance plus opaque refresh rotation.

**Acceptance criteria:**
- [x] Access token has explicit issuer, audience, subject, issued/expiry times and strong configured key.
- [x] Successful refresh rotates the token; reuse revokes the token family.
- [x] Token/cookie values are never logged.

**Verification:**
- [x] `cd backend; .\mvnw.cmd "-Dtest=JwtServiceTest,RefreshSessionServiceTest" test` — 8 tests passed.

PostgreSQL concurrency note (2026-09-17): the root-mutex race regression is included in `RefreshSessionRepositoryIT`, but remains skipped locally with the rest of the PostgreSQL suite until Docker is available.

**Dependencies:** 04, 10

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/security/JwtUtils.java`, `backend/src/main/java/com/github/yildizmy/auth/RefreshSessionService.java`, `backend/src/test/java/com/github/yildizmy/security/JwtServiceTest.java`, `backend/src/test/java/com/github/yildizmy/auth/RefreshSessionServiceTest.java`

**Estimated scope:** M (4 files)

## Task 12 — Configure cookie auth, CSRF and webhook chain

**Description:** Replace blanket CSRF disablement with SPA CSRF and isolate webhook authentication rules.

**Acceptance criteria:**
- [x] Browser mutations require a valid CSRF token.
- [x] Access/refresh cookies use environment-correct HttpOnly/Secure/SameSite/path flags.
- [x] Webhooks bypass user JWT/CSRF but are restricted to their dedicated endpoints.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=SecurityConfigIT test` — 4 tests passed.

Full backend verification (2026-09-17): `cd backend; .\mvnw.cmd verify` passed with 55 unit tests and 4 security integration tests. Eight PostgreSQL integration tests remain skipped because the local Docker engine is unavailable.

**Dependencies:** 11

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/config/SecurityConfig.java`, `backend/src/main/java/com/github/yildizmy/security/AuthTokenFilter.java`, `backend/src/main/java/com/github/yildizmy/config/AuthCookieProperties.java`, `backend/src/test/java/com/github/yildizmy/config/SecurityConfigIT.java`

**Estimated scope:** M (4 files)

## Task 13 — Expose session lifecycle endpoints

**Description:** Update login and add me/refresh/logout/CSRF endpoints without returning browser tokens in JSON.

**Acceptance criteria:**
- [x] Login sets cookies and returns sanitized user data.
- [x] Refresh rotates, logout revokes and `/me` reflects the authenticated user.
- [x] Error response is indistinguishable for invalid username/password.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=AuthControllerIT test` — 7 tests passed.

Full backend verification (2026-09-17): `cd backend; .\mvnw.cmd clean verify` passed with 58 unit tests and 11 non-PostgreSQL integration tests. Eight PostgreSQL integration tests remain skipped because the local Docker engine is unavailable.

**Dependencies:** 12

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/controller/AuthController.java`, `backend/src/main/java/com/github/yildizmy/service/AuthService.java`, `backend/src/main/java/com/github/yildizmy/dto/response/AuthSessionResponse.java`, `backend/src/test/java/com/github/yildizmy/controller/AuthControllerIT.java`

**Estimated scope:** M (4 files)

## Task 14 — Build typed auth client and auth pages

**Description:** Add the Next server API wrapper and Vercel-styled login/signup screens.

**Acceptance criteria:**
- [x] Frontend sends credentials/cookies and CSRF correctly without Web Storage tokens.
- [x] Forms have labels, field errors, pending state and safe generic login failure.
- [x] Protected navigation derives from `/auth/me`.

**Verification:**
- [x] `cd frontend; npm test -- auth` — 7 tests passed.
- [x] `cd frontend; npm run typecheck`

Additional verification (2026-09-17): frontend lint and production build passed. Browser QA passed at 375, 768, 1024 and 1440 CSS-pixel widths plus 812×375 landscape, with no horizontal overflow or console warnings/errors. With the backend intentionally unavailable, session navigation settled safely to the login action and login failures exposed only the generic Vietnamese message.

**Dependencies:** 08, 13

**Files likely touched:** `frontend/lib/api/auth.ts`, `frontend/lib/api/client.ts`, `frontend/app/(auth)/login/page.tsx`, `frontend/app/(auth)/signup/page.tsx`, `frontend/components/auth/auth-form.tsx`

**Estimated scope:** M (5 files)

## Task 15 — Prove authentication end to end

**Description:** Cover session refresh, CSRF failure and logout in a real browser.

**Acceptance criteria:**
- [x] Signup/login/protected navigation/logout works.
- [x] Browser storage contains no token.
- [x] Missing CSRF and reused refresh token fail safely.

**Verification:**
- [x] `cd frontend; npm run test:e2e -- --grep "authentication"` — 2 browser tests passed.
- [x] `cd backend; .\mvnw.cmd -Dtest=AuthSecurityIT test` — 3 security tests passed.

The browser suite uses a deterministic API contract fixture so it can exercise cookie, CSRF, refresh and Web Storage behavior while the local Docker engine is unavailable. Full regression on 2026-09-18 passed 9 frontend unit tests, 3 Playwright tests, the production build, 58 non-PostgreSQL backend unit tests and 14 non-PostgreSQL backend integration tests. One support test and eight repository integration tests remain skipped until PostgreSQL Testcontainers can run.

**Dependencies:** 14

**Files likely touched:** `frontend/e2e/auth.spec.ts`, `backend/src/test/java/com/github/yildizmy/auth/AuthSecurityIT.java`

**Estimated scope:** S (2 files)

## Checkpoint B — Identity

- [x] Backend auth/security suite passes.
- [ ] Browser cookies have expected flags in dev and production profiles.
- [ ] `rg -n "localStorage|sessionStorage" frontend` finds no auth token storage.
- [x] Commit: `feat: add rotating cookie authentication`

## Phase 3 — Catalog and Vercel storefront

## Task 16 — Add category and product schema

**Description:** Create catalog tables/entities with stable UUIDs, slugs, VND prices and status.

**Acceptance criteria:**
- [ ] Constraints enforce unique slugs and positive integer prices.
- [ ] Entity mapping passes a clean Flyway migration.

**Verification:**
- [ ] `cd backend; .\mvnw.cmd -Dtest=CatalogMigrationIT test`

Implementation note (2026-09-18): V7, the UUID-backed catalog entities and database/domain constraint tests are implemented. `CatalogEntityTest` passes (2 tests), while all 3 `CatalogMigrationIT` tests are skipped because this machine has a Docker CLI but no reachable Docker engine or Docker Desktop executable. Keep the migration acceptance items open until the suite runs against its fresh PostgreSQL 17 Testcontainer.

**Dependencies:** 05

**Files likely touched:** `backend/src/main/resources/db/migration/V7__add_catalog.sql`, `backend/src/main/java/com/github/yildizmy/catalog/Category.java`, `backend/src/main/java/com/github/yildizmy/catalog/Product.java`, `backend/src/test/java/com/github/yildizmy/catalog/CatalogMigrationIT.java`

**Estimated scope:** M (4 files)

## Task 17 — Implement catalog query service

**Description:** Add repositories and a paginated filter/sort/search service.

**Acceptance criteria:**
- [ ] Search supports query, category, availability and approved sort values.
- [ ] Product detail resolves by slug and never returns JPA entities.

**Verification:**
- [ ] `cd backend; .\mvnw.cmd -Dtest=CatalogQueryServiceIT test`

Implementation note (2026-09-18): the typed query contract, approved availability/sort enums, paginated native projection, public detail lookup and a forward-compatible database availability function are implemented. `CatalogQueryServiceTest` passes (3 tests); all 3 PostgreSQL query integration tests are skipped until a Docker engine is available, so the acceptance items remain open.

**Dependencies:** 16

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/catalog/CategoryRepository.java`, `backend/src/main/java/com/github/yildizmy/catalog/ProductRepository.java`, `backend/src/main/java/com/github/yildizmy/catalog/CatalogQueryService.java`, `backend/src/main/java/com/github/yildizmy/catalog/ProductView.java`, `backend/src/test/java/com/github/yildizmy/catalog/CatalogQueryServiceIT.java`

**Estimated scope:** M (5 files)

## Task 18 — Expose public catalog API

**Description:** Add public list/detail endpoints and OpenAPI examples.

**Acceptance criteria:**
- [x] Response supports pagination and shows computed available count.
- [x] Invalid filter/sort/page values return stable problem responses.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=CatalogControllerIT test` — 3 controller/security contract tests passed.

**Dependencies:** 17

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/catalog/CatalogController.java`, `backend/src/main/java/com/github/yildizmy/catalog/ProductListResponse.java`, `backend/src/main/java/com/github/yildizmy/catalog/ProductDetailResponse.java`, `backend/src/test/java/com/github/yildizmy/catalog/CatalogControllerIT.java`

**Estimated scope:** M (4 files)

## Task 19 — Add admin catalog commands

**Description:** Implement admin-only category/product create/update/status endpoints.

**Acceptance criteria:**
- [x] Customer calls are denied and changes are validated/audited.
- [x] Price/name/status updates are isolated to catalog entities and cannot mutate later order snapshots.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=AdminCatalogControllerIT test` — 3 controller/security contract tests passed.

**Dependencies:** 17, 13

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/catalog/AdminCatalogController.java`, `backend/src/main/java/com/github/yildizmy/catalog/CatalogCommandService.java`, `backend/src/main/java/com/github/yildizmy/catalog/ProductCommand.java`, `backend/src/test/java/com/github/yildizmy/catalog/AdminCatalogControllerIT.java`

**Estimated scope:** M (4 files)

## Task 20 — Seed realistic development catalog

**Description:** Add deterministic dev-only categories/products matching the requested digital-goods shop.

**Acceptance criteria:**
- [x] Seed is disabled by default outside dev/test.
- [x] Re-running seed is idempotent and contains no real credential/API key.

**Verification:**
- [ ] `cd backend; .\mvnw.cmd -Dtest=CatalogSeedIT test`

**Dependencies:** 16

**Files likely touched:** `backend/src/main/resources/db/migration/V8__add_demo_catalog.sql`, `backend/src/test/java/com/github/yildizmy/catalog/CatalogSeedIT.java`

**Estimated scope:** S (2 files)

## Task 21 — Create typed storefront catalog adapter

**Description:** Replace Shopify product types/fetches with Spring REST contracts.

**Acceptance criteria:**
- [x] Server-only client maps Spring problem responses and product/page types.
- [x] No Shopify import, token or environment variable remains in the catalog path.

**Verification:**
- [x] `cd frontend; npm test -- catalog-api; npm run typecheck` — 4 contract tests and typecheck passed.

**Dependencies:** 18, 08

**Files likely touched:** `frontend/lib/api/catalog.ts`, `frontend/lib/contracts/catalog.ts`, `frontend/lib/api/errors.ts`, `frontend/lib/api/catalog.test.ts`

**Estimated scope:** M (4 files)

## Task 22 — Adapt Vercel navbar and featured home

**Description:** Port the three-zone navbar, mobile menu, featured grid and carousel to project data.

**Acceptance criteria:**
- [x] Navbar shows category menu/search and auth/wallet placeholders responsively.
- [x] Featured grid/carousel render API products and handle empty/error states.

**Verification:**
- [x] `cd frontend; npm test -- storefront-home; npm run build` — 2 component tests, typecheck, lint and production build passed.

**Dependencies:** 21

**Files likely touched:** `frontend/components/layout/navbar.tsx`, `frontend/components/layout/mobile-menu.tsx`, `frontend/components/product/featured-grid.tsx`, `frontend/components/product/product-carousel.tsx`, `frontend/app/page.tsx`

**Estimated scope:** M (5 files)

## Task 23 — Build searchable grid/list catalog

**Description:** Add URL-driven query/category/sort controls and grid/list rendering.

**Acceptance criteria:**
- [x] Filters are shareable in the URL and server-render the correct results.
- [x] Grid/list toggle preserves filters and both modes show price/stock.
- [x] Empty/search-error states are actionable.

**Verification:**
- [x] `cd frontend; npm test -- catalog-view` — 2 component tests passed.
- [x] `cd frontend; npm run test:e2e -- --grep "catalog search"` — desktop and 390px mobile flows passed on Edge.

**Dependencies:** 21, 22

**Files likely touched:** `frontend/app/search/page.tsx`, `frontend/components/product/catalog-filters.tsx`, `frontend/components/product/product-grid.tsx`, `frontend/components/product/product-row.tsx`, `frontend/e2e/catalog.spec.ts`

**Estimated scope:** M (5 files)

## Task 24 — Build product detail page

**Description:** Adapt Vercel gallery/description for digital stock, warranty and wallet purchase placeholder.

**Acceptance criteria:**
- [x] Metadata/JSON-LD, gallery, VND price, stock and related products render.
- [x] No variant/shipping/tax UI remains.
- [x] Missing product uses the real not-found boundary.

**Verification:**
- [x] `cd frontend; npm test -- product-detail; npm run build` — 3 component tests and production build passed.

**Dependencies:** 21

**Files likely touched:** `frontend/app/product/[slug]/page.tsx`, `frontend/components/product/gallery.tsx`, `frontend/components/product/product-description.tsx`, `frontend/components/product/price.tsx`, `frontend/components/product/product-detail.test.tsx`

**Estimated scope:** M (5 files)

## Checkpoint C — Read-only storefront

- [x] Catalog backend tests pass.
- [x] Storefront/search/product browser tests pass at required breakpoints.
- [x] Axe reports no serious/critical issue.
- [x] Commit: `feat: add product catalog storefront`

## Phase 4 — Encrypted inventory

## Task 25 — Add inventory schema and status model

**Description:** Create inventory rows, encrypted fields, state constraints and one-sale linkage.

**Acceptance criteria:**
- [x] Status is constrained to approved values.
- [x] Cipher fields/key version are required; plaintext column does not exist.
- [x] Product/status lookup is indexed.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=InventoryMigrationIT test` — 3 PostgreSQL migration cases passed with Docker Engine available.

**Dependencies:** 16

**Files likely touched:** `backend/src/main/resources/db/migration/V9__add_inventory.sql`, `backend/src/main/java/com/github/yildizmy/inventory/InventoryItem.java`, `backend/src/main/java/com/github/yildizmy/inventory/InventoryStatus.java`, `backend/src/test/java/com/github/yildizmy/inventory/InventoryMigrationIT.java`

**Estimated scope:** M (4 files)

Implementation note (2026-09-18): migration version `V8` is used because it is the next free Flyway version in this repository; the earlier `V9` filename estimate was stale.

## Task 26 — Implement authenticated inventory encryption

**Description:** Add AES-256-GCM encryption/decryption with random nonce, AAD and key version.

**Acceptance criteria:**
- [x] Same plaintext encrypts differently on repeated calls.
- [x] Wrong key/tag/AAD fails closed without leaking plaintext.
- [x] Missing/weak production key prevents startup.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=InventoryCryptoServiceTest test` — 6 crypto/configuration tests passed.

**Dependencies:** 25

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/inventory/InventoryCryptoProperties.java`, `backend/src/main/java/com/github/yildizmy/inventory/EncryptedPayload.java`, `backend/src/main/java/com/github/yildizmy/inventory/InventoryCryptoService.java`, `backend/src/test/java/com/github/yildizmy/inventory/InventoryCryptoServiceTest.java`

**Estimated scope:** M (4 files)

## Task 27 — Implement batch inventory import API

**Description:** Validate, deduplicate, encrypt and store admin-imported items with per-row results.

**Acceptance criteria:**
- [x] Import is admin-only, size-limited and never logs request payload.
- [x] Duplicate/blank/oversized rows return sanitized row errors.
- [x] Stored rows decrypt to originals only through the crypto service.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=InventoryImportServiceIT,AdminInventoryControllerIT test` — 5 service/security tests passed.

**Dependencies:** 19, 26

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/inventory/InventoryRepository.java`, `backend/src/main/java/com/github/yildizmy/inventory/InventoryImportService.java`, `backend/src/main/java/com/github/yildizmy/inventory/AdminInventoryController.java`, `backend/src/test/java/com/github/yildizmy/inventory/InventoryImportServiceIT.java`, `backend/src/test/java/com/github/yildizmy/inventory/AdminInventoryControllerIT.java`

**Estimated scope:** M (5 files)

## Task 28 — Build masked inventory admin UI

**Description:** Add inventory list/import/status UI without ever requesting plaintext listings.

**Acceptance criteria:**
- [x] Admin sees counts/status/checksum suffix only.
- [x] Batch import reports success/failure per row and clears plaintext input after submit.
- [x] Customer route access is denied.

**Verification:**
- [x] `cd frontend; npm test -- admin-inventory` — 2 component tests passed.
- [x] `cd frontend; npm run test:e2e -- --grep "inventory import"` — admin import and customer denial passed on Edge.

**Dependencies:** 27

**Files likely touched:** `frontend/lib/api/admin-inventory.ts`, `frontend/app/admin/inventory/page.tsx`, `frontend/components/admin/inventory-import-form.tsx`, `frontend/components/admin/inventory-table.tsx`, `frontend/e2e/admin-inventory.spec.ts`

**Estimated scope:** M (5 files)

## Task 29 — Prove inventory secrecy and rotation format

**Description:** Add plaintext leak tests and document key-version rotation steps.

**Acceptance criteria:**
- [x] Known fixture secret is absent from the disposable PostgreSQL dump, application log and API errors in the focused tests.
- [x] Rotation procedure supports reading old key versions while writing the active version.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=InventorySecretLeakIT test` — 2 cases passed, including the automated `pg_dump` assertion.
- [x] `rg -n "KNOWN_SECRET_FIXTURE" backend/target logs` returns no match.

**Dependencies:** 27

**Files likely touched:** `backend/src/test/java/com/github/yildizmy/inventory/InventorySecretLeakIT.java`, `docs/runbooks/inventory-key-rotation.md`

**Estimated scope:** S (2 files)

## Checkpoint D — Inventory vault

- [x] Crypto/import/leak tests pass, including the disposable PostgreSQL dump case.
- [x] Admin inventory browser flow passes.
- [x] A disposable sample DB export does not contain the known plaintext fixture; `pg_dump` assertion passed.
- [x] Commit: `feat: encrypt digital inventory`

## Phase 5 — Wallet ledger and locking

## Task 30 — Add immutable wallet ledger schema

**Description:** Create wallet entries, references and wallet lock/version fields.

**Acceptance criteria:**
- [x] Entry amount is positive and type/direction constrained.
- [x] Business reference is unique and entries have no update/delete service path.
- [x] Wallet balance cannot be negative.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=WalletLedgerMigrationIT test` — 2 PostgreSQL migration cases passed.

**Dependencies:** 05

**Files likely touched:** Implemented as `V9__add_wallet_ledger.sql` because V9 was the next free Flyway version, plus the wallet ledger model and tests.

**Estimated scope:** M (4 files)

## Task 31 — Backfill and reconcile legacy balances

**Description:** Add deterministic opening entries without silently collapsing multiple legacy wallets.

**Acceptance criteria:**
- [x] Backfill is idempotent and creates one opening entry per retained funded wallet; zero-balance wallets retain an empty ledger so entries remain positive.
- [x] Sum of entries equals cached balances after migration.
- [x] Multiple-wallet conflict produces an explicit audit/report, not data loss.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=WalletLedgerBackfillIT test` — PostgreSQL backfill/replay case passed, including a wallet funded between executions.

**Dependencies:** 30, Task 01 data classification

**Files likely touched:** Implemented as `V10__backfill_wallet_ledger.sql` because V10 was the next free Flyway version, plus the backfill tests and migration runbook.

**Estimated scope:** M (3 files)

## Task 32 — Implement locked credit/debit service

**Description:** Centralize balance mutation behind pessimistic wallet locking and immutable entries.

**Acceptance criteria:**
- [x] Credit/debit entry and cached balance commit atomically.
- [x] Duplicate reference is idempotent.
- [x] Insufficient funds never writes an entry or negative balance.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=WalletLedgerServiceIT test` — PostgreSQL service case passed; focused unit cases also pass.

**Dependencies:** 31

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/repository/WalletRepository.java`, `backend/src/main/java/com/github/yildizmy/wallet/WalletEntryRepository.java`, `backend/src/main/java/com/github/yildizmy/wallet/WalletLedgerService.java`, `backend/src/test/java/com/github/yildizmy/wallet/WalletLedgerServiceIT.java`

**Estimated scope:** M (4 files)

## Task 33 — Retire unsafe public balance mutations

**Description:** Remove or block customer-controlled add-funds/withdraw routes and route internal mutations through the ledger.

**Acceptance criteria:**
- [x] Client cannot submit arbitrary credit/withdraw requests.
- [x] Legacy transfer behavior is explicitly disabled; customer wallet mutations now have no controller or service path.
- [x] OpenAPI no longer advertises fake top-up because all mutation mappings were removed from `WalletController`.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=WalletMutationSecurityIT test` — 2 tests passed.

**Dependencies:** 32

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/controller/WalletController.java`, `backend/src/main/java/com/github/yildizmy/service/WalletService.java`, `backend/src/test/java/com/github/yildizmy/wallet/WalletMutationSecurityIT.java`

**Estimated scope:** M (3 files)

## Task 34 — Expose wallet balance and ledger API

**Description:** Add authenticated current-wallet and paginated entry endpoints.

**Acceptance criteria:**
- [x] User identity comes from the session, never request `userId`.
- [x] Cross-user access is impossible through the current-wallet API; legacy ID-based reads are admin-only.
- [x] Response shows sanitized business labels and VND values.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=WalletQueryControllerIT test` — 3 HTTP contract tests passed; 4 query-service tests passed.

**Dependencies:** 32, 13

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/wallet/WalletQueryController.java`, `backend/src/main/java/com/github/yildizmy/wallet/WalletView.java`, `backend/src/main/java/com/github/yildizmy/wallet/WalletEntryView.java`, `backend/src/test/java/com/github/yildizmy/wallet/WalletQueryControllerIT.java`

**Estimated scope:** M (4 files)

## Task 34A — Provision an empty wallet for every user

**Description:** New signup creates an internal zero-balance wallet; V11 migrates legacy users with no wallet without touching existing wallets.

**Acceptance criteria:**
- [x] Signup cannot accept a caller-chosen balance and creates one zero-balance wallet atomically.
- [x] Legacy users without a wallet receive one empty wallet; migration is idempotent.
- [x] Existing wallets and ledger entries are preserved.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=AuthServiceTest,WalletProvisioningMigrationContractTest test` — unit/contract tests pass.
- [x] `cd backend; .\mvnw.cmd -Dtest=WalletProvisioningMigrationIT test` — PostgreSQL migration case passed.

## Task 35 — Replace cart modal with wallet drawer

**Description:** Adapt Vercel cart sheet into balance, top-up entry point and recent ledger view.

**Acceptance criteria:**
- [x] Header shows current balance and opens accessible drawer, including a compact amount on mobile.
- [x] Drawer has loading/empty/error states and no shipping/tax/quantity UI.
- [x] Logout/expired session closes private wallet data.

**Verification:**
- [x] `cd frontend; npm test -- wallet-drawer` — five drawer tests passed. Wallet API contract test and browser checks also passed with mocked API responses.

**Dependencies:** 22, 34

**Files touched:** `frontend/lib/api/wallet.ts`, `frontend/components/wallet/wallet-drawer.tsx`, `frontend/components/auth/session-navigation.tsx`, `frontend/src/app/globals.css`, `frontend/src/app/top-up/page.tsx`, `frontend/test/wallet-drawer.test.tsx`, `frontend/test/wallet-api.test.ts`

**Estimated scope:** M (5 files)

## Task 36 — Prove wallet concurrency

**Description:** Stress concurrent debits/duplicate references against PostgreSQL.

**Acceptance criteria:**
- [x] Repeated PostgreSQL race-test runs did not create negative balances.
- [x] The duplicate-reference race test created exactly one entry per business reference.
- [x] The tested single-wallet races completed within the bounded deadline without deadlocks; multi-resource checkout ordering remains a separate Task 50/51 gate.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=WalletConcurrencyIT test` — 6 PostgreSQL race cases passed with Docker available, with no Surefire rerun masking.

**Dependencies:** 32

**Files touched:** `backend/src/test/java/com/github/yildizmy/wallet/WalletConcurrencyIT.java` (three repetitions each for overdraw and duplicate-reference races). A retry policy is not added unless PostgreSQL tests demonstrate one is needed.

**Scope:** The test coordinates eight workers on one wallet and enforces a bounded deadline. It does not prove absence of multi-resource deadlocks; checkout wallet→inventory ordering must be verified separately in Task 50/51.

**Estimated scope:** S (2 files)

## Checkpoint E — Wallet core

- [x] Ledger/backfill/concurrency tests passed in focused and full PostgreSQL integration runs.
- [x] No customer-controlled credit endpoint exists (`WalletMutationSecurityIT` passed).
- [x] Wallet drawer works in a real browser — Edge desktop/375px with mocked wallet API, including 401 privacy behavior; a live end-to-end browser purchase remains untested.
- [ ] Commit: `feat: add immutable wallet ledger`

## Phase 6 — Deposit core and MoMo

## Task 37 — Add deposit and webhook inbox schema

**Description:** Create provider-neutral deposit state and unique webhook event records.

**Acceptance criteria:**
- [x] Provider transaction/event identities are unique in the V12 schema.
- [x] Deposit transitions and positive amount constraints are represented in the schema/model.
- [x] Raw merchant secrets are not stored in the deposit/webhook models.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=DepositMigrationIT test` — 3 PostgreSQL cases passed on a disposable database.

**Progress:** V12 schema and deposit/webhook models pass their contract and PostgreSQL migration tests. This accepts the schema only: no deposit endpoint, provider adapter, webhook handler, or wallet credit is active.

**Dependencies:** 30

**Files likely touched:** `backend/src/main/resources/db/migration/V12__add_deposits.sql`, `backend/src/main/java/com/github/yildizmy/payment/Deposit.java`, `backend/src/main/java/com/github/yildizmy/payment/DepositStatus.java`, `backend/src/main/java/com/github/yildizmy/payment/WebhookEvent.java`, `backend/src/test/java/com/github/yildizmy/payment/DepositMigrationIT.java`

**Estimated scope:** M (5 files)

## Task 38 — Define provider ports and idempotent webhook processor

**Description:** Add payment-provider contracts and inbox-first processing.

**Provider contract gate:** V12 reserves both provider event ID and provider transaction ID per provider. Before an adapter writes events, confirm whether its provider sends multiple distinct lifecycle callbacks for one transaction. If so, define which verified callbacks enter the canonical inbox or revise the delivery/canonical-transaction model before enabling money movement.

**Acceptance criteria:**
- [x] Provider port returns a validated, normalized verified notification for concrete adapters.
- [x] Duplicate event/transaction is a no-op.
- [x] Verified deposit transition and wallet credit occur in one transaction.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=WebhookProcessorIT test` — 6 PostgreSQL cases passed, including replay, identity conflict, unknown reference, terminal out-of-order notification and transaction rollback.

**Progress:** The provider-neutral port validates normalized notifications before they reach money movement. The processor inserts the immutable inbox identity first, locks the referenced deposit, credits through the wallet ledger and settles the deposit in one transaction. Event/transaction replays do not credit twice; unknown or suspicious callbacks are retained for review. The concrete MoMo adapter is implemented; bank adapters were removed from the first-release scope.

**Dependencies:** 32, 37

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/payment/PaymentProvider.java`, `backend/src/main/java/com/github/yildizmy/payment/VerifiedNotification.java`, `backend/src/main/java/com/github/yildizmy/payment/WebhookEventRepository.java`, `backend/src/main/java/com/github/yildizmy/payment/WebhookProcessor.java`, `backend/src/test/java/com/github/yildizmy/payment/WebhookProcessorIT.java`

**Estimated scope:** M (5 files)

## Task 39 — Expose deposit create/status API

**Description:** Create authenticated deposits and poll their safe status.

**Acceptance criteria:**
- [x] Amount/channel validation and per-user ownership are enforced.
- [x] Response contains only provider-safe payment instructions.
- [x] Expired/failed transitions cannot later be client-forced to paid.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=DepositControllerIT test` — 3 PostgreSQL-backed API cases passed; provider-instruction boundary tests and the full backend verification suite also passed.

**Progress:** Authenticated users can create a positive VND MoMo deposit and poll only their own deposit. The record commits before MoMo prepares HTTPS instructions, every response is `no-store`, and no customer endpoint can mutate payment status. The API rejects unsupported channel names and fails closed with `503 PAYMENT_CHANNEL_UNAVAILABLE` when MoMo is not configured.

**Dependencies:** 38, 13

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/payment/DepositController.java`, `backend/src/main/java/com/github/yildizmy/payment/DepositService.java`, `backend/src/main/java/com/github/yildizmy/payment/DepositResponse.java`, `backend/src/test/java/com/github/yildizmy/payment/DepositControllerIT.java`

**Estimated scope:** M (4 files)

## Task 40 — Build top-up UI and status polling

**Description:** Add MoMo amount form, payment instructions and verified status states.

**Acceptance criteria:**
- [x] Form supports MoMo and validates positive VND amounts.
- [x] Redirect never displays success before backend status becomes paid.
- [x] Polling stops on terminal state/unmount and handles expiry.

**Verification:**
- [x] `cd frontend; npm test -- deposit-flow`

**Dependencies:** 35, 39

**Files likely touched:** `frontend/lib/api/deposits.ts`, `frontend/app/wallet/top-up/page.tsx`, `frontend/components/payment/top-up-form.tsx`, `frontend/components/payment/deposit-status.tsx`, `frontend/components/payment/deposit-flow.test.tsx`

**Estimated scope:** M (5 files)

## Task 41 — Implement MoMo create-payment client

**Description:** Build canonical HMAC request signing and sandbox HTTP client.

**Acceptance criteria:**
- [x] Canonical field order matches official MoMo contract fixtures.
- [x] Credentials/base URLs come from validated environment properties.
- [x] Timeouts and sanitized provider errors are explicit.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=MomoSignatureTest,MomoClientTest test`

**Dependencies:** 38

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/payment/momo/MomoProperties.java`, `backend/src/main/java/com/github/yildizmy/payment/momo/MomoSignature.java`, `backend/src/main/java/com/github/yildizmy/payment/momo/MomoClient.java`, `backend/src/test/java/com/github/yildizmy/payment/momo/MomoSignatureTest.java`, `backend/src/test/java/com/github/yildizmy/payment/momo/MomoClientTest.java`

**Estimated scope:** M (5 files)

## Task 42 — Process and verify MoMo IPN

**Description:** Verify raw IPN, match stored deposit and return provider-required status.

**Acceptance criteria:**
- [x] Signature, partnerCode, orderId, amount, resultCode and transaction ID are checked.
- [x] Valid success credits exactly once; invalid/mismatched events never credit.
- [x] Response is `204` within the processing budget.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=MomoWebhookControllerIT test`

**Dependencies:** 39, 41

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/payment/momo/MomoPaymentProvider.java`, `backend/src/main/java/com/github/yildizmy/payment/momo/MomoWebhookController.java`, `backend/src/main/java/com/github/yildizmy/payment/momo/MomoNotification.java`, `backend/src/test/java/com/github/yildizmy/payment/momo/MomoWebhookControllerIT.java`

**Estimated scope:** M (4 files)

## Task 43 — Prove MoMo sandbox journey

**Description:** Add signed fixtures and browser coverage from deposit creation to updated wallet.

**Acceptance criteria:**
- [x] Valid fixture credits once even when submitted ten times.
- [x] Forged/mismatched/out-of-order fixtures do not credit.
- [x] UI moves pending → paid from backend state.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=MomoReplayIT test`
- [x] `cd frontend; npm run test:e2e -- --grep "MoMo top-up"`

**Dependencies:** 40, 42

**Files likely touched:** `backend/src/test/java/com/github/yildizmy/payment/momo/MomoReplayIT.java`, `backend/src/test/resources/momo/ipn-fixtures.json`, `frontend/e2e/momo-topup.spec.ts`

**Estimated scope:** M (3 files)

## Checkpoint F — MoMo sandbox

- [x] MoMo request/signature/IPN/replay suites pass.
- [ ] Sandbox journey updates wallet exactly once.
- [x] Credential/log scan passes.
- [x] Commits: `ec314b3`, `9312ef9`, `4864b99`

Local signed-fixture and browser journeys pass. The live MoMo sandbox journey remains gated on sandbox
credentials and a publicly reachable HTTPS redirect/IPN host; no secret value is committed.

## Phase 7 — Removed from scope: Bank/VietQR

Scope decision (2026-09-26): the first production release supports one external payment method, MoMo.
Former Tasks 44–47 and the bank sandbox checkpoint are no longer required. The persisted domain may retain
legacy `BANK` values for migration compatibility, but the customer deposit API and UI do not create them.

## Phase 8 — Atomic checkout and delivery

## Task 48 — Add order and order-item schema

**Description:** Store order totals/product snapshots and one-to-one sold inventory linkage.

**Acceptance criteria:**
- [x] `(user_id, idempotency_key)` is unique.
- [x] Order item preserves name/price snapshot.
- [x] Inventory item cannot belong to multiple order items.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=OrderMigrationIT test`

**Progress:** V13 schema and JPA models passed the PostgreSQL migration test. The schema reserves each inventory item for at most one order item; no checkout endpoint, stock transition, or wallet debit is enabled yet.

**Dependencies:** 25, 30

**Files likely touched:** `backend/src/main/resources/db/migration/V13__add_orders.sql`, `backend/src/main/java/com/github/yildizmy/order/Order.java`, `backend/src/main/java/com/github/yildizmy/order/OrderItem.java`, `backend/src/main/java/com/github/yildizmy/order/OrderStatus.java`, `backend/src/test/java/com/github/yildizmy/order/OrderMigrationIT.java`

**Estimated scope:** M (5 files)

## Task 49 — Write failing checkout concurrency tests

**Description:** Define expected last-item, insufficient-funds, retry and rollback behavior before service code.

**Acceptance criteria:**
- [x] Test proves two buyers cannot acquire one item.
- [x] Same idempotency key cannot debit twice.
- [x] Injected failure requires all wallet/order/inventory changes to roll back.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=CheckoutConcurrencyIT test` failed for the expected missing checkout service before Task 50.

**Progress:** The RED step covered final-item contention, concurrent duplicate retries and a late injected inventory-sale failure.

**Dependencies:** 32, 48

**Files likely touched:** `backend/src/test/java/com/github/yildizmy/order/CheckoutConcurrencyIT.java`, `backend/src/test/java/com/github/yildizmy/order/CheckoutFixtures.java`

**Estimated scope:** S (2 files)

## Task 50 — Implement atomic checkout service

**Description:** Lock wallet then inventory, snapshot product, debit and sell in one transaction.

**Acceptance criteria:**
- [x] Fixed lock order is explicit and only one buyer wins the final item.
- [x] Insufficient funds/out-of-stock leave no partial writes.
- [x] Idempotent retry returns the original order.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=CheckoutConcurrencyIT,CheckoutServiceIT test`

**Progress:** Checkout locks the user's wallet before selecting stock with `FOR UPDATE SKIP LOCKED`, writes the order, ledger debit and sold inventory in one transaction, and rejects reuse of an idempotency key for another product.

**Dependencies:** 49

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/inventory/InventoryRepository.java`, `backend/src/main/java/com/github/yildizmy/order/OrderRepository.java`, `backend/src/main/java/com/github/yildizmy/order/OrderItemRepository.java`, `backend/src/main/java/com/github/yildizmy/order/CheckoutService.java`, `backend/src/test/java/com/github/yildizmy/order/CheckoutServiceIT.java`

**Estimated scope:** M (5 files)

## Task 51 — Expose order and secret-reveal API

**Description:** Add create/list/detail and audited owner-only reveal endpoints.

**Acceptance criteria:**
- [x] Checkout requires `Idempotency-Key` and derives user from session.
- [x] Cross-user reveal is denied without confirming resource existence.
- [x] Reveal response is `no-store` and plaintext is absent from logs/errors.

**Verification:**
- [x] `cd backend; .\mvnw.cmd '-Dtest=OrderControllerIT,OrderQueryServiceIT,SecretRevealSecurityIT' test` — 8 tests passed.

Full backend verification (2026-09-23): `cd backend; .\mvnw.cmd clean verify -q` passed with 183 tests, 0 failures/errors/skips. Audit review found no remaining P1/P2 issues after V14 made reveal events append-only and secret-free.

**Dependencies:** 26, 50

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/order/OrderController.java`, `backend/src/main/java/com/github/yildizmy/order/OrderQueryService.java`, `backend/src/main/java/com/github/yildizmy/order/SecretRevealService.java`, `backend/src/test/java/com/github/yildizmy/order/OrderControllerIT.java`, `backend/src/test/java/com/github/yildizmy/order/SecretRevealSecurityIT.java`

**Estimated scope:** M (5 files)

## Task 52 — Replace Add to Cart with wallet purchase

**Description:** Build Vercel-styled buy button and confirmation dialog with idempotent submission.

**Acceptance criteria:**
- [x] Dialog shows item, price and balance before/after.
- [x] Double click/retry sends one stable idempotency key.
- [x] Out-of-stock/insufficient-funds errors are actionable.

**Verification:**
- [x] `cd frontend; npm test -- buy-with-wallet` — 11 wallet-checkout tests passed; full frontend suite (44 tests), lint, typecheck and production build also passed.

**Dependencies:** 24, 35, 51

**Files likely touched:** `frontend/lib/api/orders.ts`, `frontend/components/order/buy-button.tsx`, `frontend/components/order/buy-dialog.tsx`, `frontend/components/product/product-description.tsx`, `frontend/components/order/buy-dialog.test.tsx`

**Estimated scope:** M (5 files)

## Task 53 — Build order history and protected reveal UI

**Description:** Add responsive order list/detail with masked, explicit reveal/copy behavior.

**Acceptance criteria:**
- [x] Secret is masked initially, fetched only on reveal and cleared from component state.
- [x] Secret is not persisted in query cache, storage or analytics.
- [x] Copy/re-hide feedback is accessible.

**Verification:**
- [x] `cd frontend; npm test -- order-history` — 6 customer UI cases passed; API contract coverage also passed (5 cases).

**Dependencies:** 51

**Files likely touched:** `frontend/app/orders/page.tsx`, `frontend/app/orders/[id]/page.tsx`, `frontend/components/order/order-list.tsx`, `frontend/components/order/secret-reveal.tsx`, `frontend/components/order/secret-reveal.test.tsx`

**Estimated scope:** M (5 files)

## Task 54 — Prove complete customer purchase journey

**Description:** Cover login, funded wallet, purchase, duplicate submit, order history and reveal in browser.

**Acceptance criteria:**
- [x] One item/debit/order is created after double submission.
- [x] Cross-user order/reveal is blocked.
- [x] Secret is absent from browser storage and page after re-hide/navigation.

**Verification:**
- [x] `cd frontend; npm run test:e2e -- --grep "digital purchase"` — 1 browser journey passed.
- [x] `cd backend; .\mvnw.cmd -Dtest=CheckoutConcurrencyIT,SecretRevealSecurityIT test` — 3 PostgreSQL integration tests passed.

**Progress:** The browser journey now proves two duplicate checkout requests share one idempotency key, exercises order history and reveal, compares foreign and missing reveal responses, and scans the DOM plus both browser stores after re-hide and navigation. PostgreSQL integration tests separately prove the single order/debit/sold-item transaction and owner-only reveal. A clock-skew regression found during verification was fixed by keeping inventory timestamps monotonic; full backend `mvnw verify` passed with 178 tests and the frontend passed 54 unit tests, lint, typecheck and production build.

**Dependencies:** 43, 52, 53

**Files likely touched:** `frontend/e2e/purchase.spec.ts`, `frontend/e2e/fixtures/purchase.ts`

**Estimated scope:** S (2 files)

## Checkpoint H — Customer journey

- [ ] Register → top-up → browse → buy → reveal passes.
- [x] Checkout concurrency suite passes repeatedly.
- [x] Browser/log/storage secret scans pass.
- [x] Commit: `feat: complete atomic wallet checkout`

## Phase 9 — Admin operations

## Task 55 — Build admin catalog screens

**Description:** Add Vercel-styled category/product list and edit forms.

**Acceptance criteria:**
- [x] Admin can create/edit/status products with field-level validation.
- [x] Customer gets denied and forms do not trust client price formatting.

**Verification:**
- [x] `cd frontend; npm test -- admin-catalog` — 5 catalog administration cases passed.

**Progress:** Added paginated admin catalog reads that include inactive records, server-enforced admin authorization, runtime validation for API responses, and responsive product/category management screens. Product prices accept only positive unformatted integer VND values before sending numeric payloads. The focused suite covers product and category create/edit/status flows, stale category-name synchronization, validation, and customer denial; the complete frontend suite, lint, typecheck, and production build also pass.

**Dependencies:** 19, 28

**Files likely touched:** `frontend/lib/api/admin-catalog.ts`, `frontend/app/admin/products/page.tsx`, `frontend/app/admin/products/[id]/page.tsx`, `frontend/components/admin/product-form.tsx`, `frontend/components/admin/product-form.test.tsx`

**Estimated scope:** M (5 files)

## Task 56 — Build admin order and deposit screens

**Description:** Add paginated order/deposit investigation with review-state evidence.

**Acceptance criteria:**
- [x] Filters/pagination are URL-addressable.
- [x] Payment/order views contain no merchant key or product plaintext.
- [x] Customer access is denied.

**Verification:**
- [x] `cd frontend; npm test -- admin-operations` — 6 admin operations cases passed.

**Progress:** Added admin-only, no-store order and deposit list endpoints with validated status/channel/user filters and stable pagination metadata. Responses are allow-listed to operational fields and omit merchant references, provider transaction IDs, payment instructions, product names, and inventory secrets. The responsive frontend reads filters from deep links, writes filter and pagination changes back to the URL, highlights reconciliation review state, handles empty/error/denied states, and passes focused API/UI coverage.

**Dependencies:** 39, 51

**Files likely touched:** `frontend/lib/api/admin-operations.ts`, `frontend/app/admin/orders/page.tsx`, `frontend/app/admin/deposits/page.tsx`, `frontend/components/admin/order-table.tsx`, `frontend/components/admin/deposit-table.tsx`

**Estimated scope:** M (5 files)

## Task 57 — Add audit and reconciliation operations

**Description:** Expose sanitized audit/reconciliation reads and controlled provider re-query where supported.

**Acceptance criteria:**
- [x] Sensitive actions have actor/resource/request-id records without secrets.
- [x] Reconciliation detects deposit/ledger mismatch and never silently edits ledger.
- [x] Provider retry/re-query is authorized, rate-limited and audited.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=AuditControllerIT,ReconciliationServiceIT test` — focused audit/reconciliation tests passed; full backend verify also passed (225 tests, 0 failures/errors/skips).

**Progress:** Audit projections are allow-listed and retain actor/resource/request-id metadata only. Reconciliation marks terminal mismatches for review, never credits or edits the wallet ledger, and provider re-query is admin-only, per-deposit rate-limited, and append-only audited.

**Dependencies:** 38, 51

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/audit/AuditController.java`, `backend/src/main/java/com/github/yildizmy/audit/AuditService.java`, `backend/src/main/java/com/github/yildizmy/payment/ReconciliationService.java`, `backend/src/test/java/com/github/yildizmy/audit/AuditControllerIT.java`, `backend/src/test/java/com/github/yildizmy/payment/ReconciliationServiceIT.java`

**Estimated scope:** M (5 files)

## Checkpoint I — Admin operations

- [x] Admin catalog/inventory/order/deposit/audit flows pass.
- [x] Authorization matrix denies every customer/admin boundary violation.
- [ ] Keyboard and axe checks pass.
- [ ] Commit: `feat: add storefront operations console`

## Phase 10 — Production hardening

## Task 58 — Add rate limits and response security headers

**Description:** Protect auth, checkout, reveal and webhook routes and configure browser headers.

**Acceptance criteria:**
- [x] Rate limits are scoped by endpoint and trusted client identity strategy.
- [x] CSP, frame, content-type, referrer and HSTS policies are environment-correct.
- [x] Limits do not log tokens/signatures/bodies.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=RateLimitIT,SecurityHeadersIT test` — focused tests passed; full backend verify also passed (225 tests, 0 failures/errors/skips).

**Progress:** Auth, checkout, reveal and webhook limits are endpoint-scoped and use bounded client identity keys. Security headers are disabled/relaxed only through explicit environment configuration; HSTS remains opt-in until the TLS proxy is authoritative.

**Dependencies:** 15, 42, 51

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/config/RateLimitFilter.java`, `backend/src/main/java/com/github/yildizmy/config/SecurityHeadersConfig.java`, `backend/src/main/resources/application.yml`, `backend/src/test/java/com/github/yildizmy/config/RateLimitIT.java`, `backend/src/test/java/com/github/yildizmy/config/SecurityHeadersIT.java`

**Estimated scope:** M (5 files)

## Task 59 — Add production observability

**Description:** Add health/readiness, business metrics and sanitized structured log configuration.

**Acceptance criteria:**
- [x] Metrics cover webhook invalid/duplicate, deposit latency, checkout results and low stock.
- [x] Health endpoints expose no secret/config values.
- [x] Logs contain request IDs and redact token/cookie/signature/secret fields.

**Verification:**
- [x] `cd backend; .\mvnw.cmd -Dtest=ObservabilityIT test` — health/Prometheus HTTP checks passed; full backend verify also passed (225 tests, 0 failures/errors/skips).

**Progress:** Actuator exposes health/readiness and Prometheus only, with details hidden. Business counters/timers use bounded tags, the low-stock gauge is sanitized, and request IDs plus log redaction apply to console/file appenders.

**Dependencies:** 57

**Files likely touched:** `backend/src/main/java/com/github/yildizmy/config/ObservabilityConfig.java`, `backend/src/main/java/com/github/yildizmy/config/BusinessMetrics.java`, `backend/src/main/resources/logback-spring.xml`, `backend/src/main/resources/application.yml`, `backend/src/test/java/com/github/yildizmy/config/ObservabilityIT.java`

**Estimated scope:** M (5 files)

## Task 60 — Harden production containers and proxy

**Description:** Build non-root images and expose only the TLS reverse proxy in production.

**Acceptance criteria:**
- [x] DB/backend have no host port in production Compose.
- [x] Images run as non-root with health checks and pinned major/minor bases.
- [x] Proxy routes `/` to Next and `/api` to Spring with body/time/security limits.

**Verification:**
- [x] `docker compose --env-file .env.production -f docker-compose.yml -f docker-compose.prod.yml config` (validated with disposable staging env and immutable image placeholders; only proxy publishes 80/443).
- [ ] `docker compose --env-file .env.staging -f docker-compose.yml -f docker-compose.prod.yml up --build --wait` (requires registry images, TLS domain and staging secrets).

**Dependencies:** 58, 59

**Files likely touched:** `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`, `docker-compose.prod.yml`, `deploy/Caddyfile`

**Estimated scope:** M (5 files)

## Task 61 — Add CI quality and security gates

**Description:** Run backend/frontend/E2E plus dependency, secret and image scans in CI.

**Acceptance criteria:**
- [x] Pull requests fail on test/build/type/security regression.
- [x] Caches never include `.env` or secret files.
- [x] Dependency findings are archived for triage; no force-fix step exists.

**Verification:**
- [x] Workflow reviewed for valid GitHub Actions structure; equivalent frontend checks and Compose config run locally.

**Dependencies:** 60

**Files likely touched:** `.github/workflows/ci.yml`, `.github/dependabot.yml`, `frontend/package.json`, `backend/pom.xml`

**Estimated scope:** M (4 files)

## Task 62 — Write backup, restore and rollback runbooks

**Description:** Document executable database backup/restore, Flyway forward repair and application rollback.

**Acceptance criteria:**
- [x] Runbooks specify encrypted storage, retention and restore verification.
- [x] No command contains a real credential or destructive broad path.
- [ ] Staging restore drill is recorded (operator must record the first real drill in the change ticket).

**Verification:**
- [ ] Execute the documented backup/restore against staging/local disposable data (runbook is executable; actual staging credential/provider approval remains required).

**Dependencies:** 60

**Files likely touched:** `docs/runbooks/backup-restore.md`, `docs/runbooks/deploy-rollback.md`, `docs/runbooks/incident-response.md`

**Estimated scope:** M (3 files)

## Task 63 — Triage dependency and secret scans

**Description:** Produce a reviewed finding list and remediate actionable vulnerabilities without breaking upgrades.

**Acceptance criteria:**
- [ ] Every critical/high result is fixed, proven unreachable with evidence or explicitly accepted by owner.
- [x] No `npm audit fix --force` is used.
- [ ] Secret scan reports no real secret.

**Verification:**
- [x] `cd frontend; npm audit --json` — 604 dependency entries with 0 info/low/moderate/high/critical findings.
- [x] `cd backend; .\mvnw.cmd clean verify` — 225 tests, 0 failures/errors/skips.
- [ ] Run configured repository secret scanner.

**Progress:** No automatic dependency rewrite was run. The workstation has no `gitleaks` executable, so the final secret-scan checkbox remains open; CI is configured to run gitleaks and Trivy on the release commit, and a conservative tracked-file scan found no credential material.

**Dependencies:** 61

**Files likely touched:** `docs/security/dependency-audit.md`, `frontend/package.json`, `frontend/package-lock.json`, `backend/pom.xml`

**Estimated scope:** M (4 files)

## Task 64 — Release-candidate verification `[GATE]`

**Description:** Run all acceptance criteria in staging and prepare controlled live enablement.

**Acceptance criteria:**
- [ ] All backend, frontend, E2E, security and accessibility suites pass.
- [ ] Clean-machine Compose, migration rehearsal, backup restore and rollback pass.
- [ ] Provider certification and small-value live transaction require explicit human approval.
- [ ] Spec acceptance criteria are checked with evidence links.

**Verification:**
- [ ] `cd backend; .\mvnw.cmd verify`
- [ ] `cd frontend; npm ci; npm run lint; npm run typecheck; npm test; npm run build; npm run test:e2e`
- [ ] `docker compose --env-file .env.staging -f docker-compose.yml -f docker-compose.prod.yml up --build --wait`

**Dependencies:** 54, 57, 58, 59, 60, 61, 62, 63; provider/production approvals

**Files likely touched:** `docs/release-checklist.md`, `CHANGELOG.md`

**Estimated scope:** S (2 files)

**Progress:** Local implementation gates are green and the release checklist records their evidence. Staging Compose, image-digest recording, restore/rollback rehearsal, provider certification, production secret injection and the live small-value MoMo transaction remain intentionally operator-owned gates.

## Checkpoint J — Ready for launch review

- [ ] All 64 tasks and spec success criteria have evidence.
- [ ] No unresolved critical/high security finding remains without explicit acceptance.
- [ ] Monitoring, backup/restore, key rotation and rollback owners are identified.
- [ ] Human approves production credentials, webhook enablement and deployment.
- [ ] Release commit/tag is created only after approval.

## Approval gate

Implementation begins after this checklist is reviewed and approved. At that point, execute one task at a time, update its checkbox/evidence, run the checkpoint suite and commit the verified increment before continuing.
