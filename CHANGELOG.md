# Changelog

## Unreleased — Digital Storefront + Wallet hardening

### Added

- Cookie-based JWT access/refresh session lifecycle with CSRF protection and
  refresh-token rotation/reuse detection.
- Catalog, encrypted inventory, wallet ledger, MoMo deposit webhook,
  idempotent atomic checkout, order history and owner-only secret reveal.
- Admin catalog/inventory/order/deposit/audit and reconciliation operations with
  sanitized responses and append-only audit records.
- Rate limits, security headers, health/readiness, Prometheus business metrics,
  request IDs and log redaction.
- Non-root backend/frontend images, private production Compose networking, Caddy
  reverse proxy, CI quality/security gates and operational runbooks.

### Changed

- The first production payment release intentionally supports MoMo only. Legacy
  bank values remain migration-compatible but are not offered by the customer
  deposit API or UI.
- Development catalog smoke data uses synthetic, non-decryptable inventory. Real
  stock must be imported through the authorized encrypted admin API.
- Legacy documentation and login examples no longer contain pre-filled sample
  credentials; local operators create disposable accounts or inject test data.

### Release note

This version is ready for staging review. Provider certification, a real
small-value transaction, final secret injection and the first staging restore
drill remain explicit human approval gates; no local test can substitute for
those approvals.
