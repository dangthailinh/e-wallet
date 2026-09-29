-- Legacy users without a wallet need an empty wallet before the customer API can serve them.
-- Existing wallets and their ledger entries are left untouched.
INSERT INTO wallet (id, iban, name, balance, user_id, version)
SELECT nextval('wallet_seq'),
       'EW' || md5('e-wallet-user:' || u.id::TEXT),
       'Main wallet',
       0,
       u.id,
       0
FROM public."user" u
WHERE NOT EXISTS (
    SELECT 1
    FROM wallet w
    WHERE w.user_id = u.id
)
ORDER BY u.id;
