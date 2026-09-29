# Inventory encryption key rotation

This runbook rotates the AES-256-GCM write key without making inventory encrypted with older versions unreadable. It never requires plaintext exports.

## Safety rules

- Generate keys in the production secret manager or another cryptographically secure environment. Never paste them into Git, tickets, chat, logs or image layers.
- `INVENTORY_KEYRING` contains encryption keys as `version:base64`, separated by commas. Every decoded key must be exactly 32 random bytes.
- `INVENTORY_CHECKSUM_KEY` is separate and stable. Do not rotate it as part of an encryption-key rotation; changing it breaks duplicate detection across imports.
- Keep every encryption key version referenced by `inventory_items.key_version`. Removing a referenced version makes those items fail closed on reveal.

## Rotate the active write key

1. Record the current active version and count rows by version:

   ```sql
   SELECT key_version, status, count(*)
   FROM inventory_items
   GROUP BY key_version, status
   ORDER BY key_version, status;
   ```

2. Generate a new 32-byte key inside the secret manager. For a secure local rehearsal only, `openssl rand -base64 32` produces the required format.
3. Add the new version to `INVENTORY_KEYRING` while retaining every old entry. For example, change `1:<old>` to `1:<old>,2:<new>`.
4. Set `INVENTORY_ACTIVE_KEY_VERSION=2`. Do not change the checksum key.
5. Deploy one instance first. Startup must fail if the active version is absent, a key is malformed or a key is weak.
6. Import a disposable inventory item and confirm its metadata reports `keyVersion: 2`. Confirm an older item can still be decrypted through an authorized reveal test.
7. Roll out the same keyring and active version to every backend instance. Mixed active versions are safe for reads but create avoidable operational ambiguity, so keep the rollout short.
8. Re-run the version-count query and record the deployment in the change log without including key material.

## Rollback

Keep both versions in the keyring and set `INVENTORY_ACTIVE_KEY_VERSION` back to the previous version, then redeploy. Items written with the new version remain readable because its key stays present. Never roll back by deleting a key entry.

## Retiring an old key

This release supports reading old versions and writing the configured active version. It does not perform automatic bulk re-encryption. Before retiring an old key, use a separately reviewed rotation job to decrypt and re-encrypt each eligible row with its UUID/product UUID AAD, verify the result, and update the envelope atomically. Remove an old key only after the database reports zero rows for that version and a backup/restore rehearsal succeeds.

## Incident checks

- Stop the rotation if any decrypt operation fails; retain all key versions while investigating.
- Search sanitized application logs and database exports for the controlled leak-test fixture. A match is a release blocker.
- If key material itself may have leaked, treat it as a security incident: restrict reveal/import operations, preserve audit evidence and rotate to a new version before resuming sales.
