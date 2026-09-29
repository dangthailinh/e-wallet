ALTER TABLE wallet
    ADD CONSTRAINT uc_wallet_id_user UNIQUE (id, user_id);

CREATE TABLE payment_deposits
(
    id                      UUID                     NOT NULL,
    user_id                 BIGINT                   NOT NULL,
    wallet_id               BIGINT                   NOT NULL,
    channel                 VARCHAR(8)               NOT NULL,
    provider_code           VARCHAR(40)              NOT NULL,
    merchant_reference      VARCHAR(100)             NOT NULL,
    provider_transaction_id VARCHAR(160),
    amount_vnd              BIGINT                   NOT NULL,
    currency                VARCHAR(3)               NOT NULL DEFAULT 'VND',
    status                  VARCHAR(16)              NOT NULL DEFAULT 'PENDING',
    needs_reconciliation_review BOOLEAN              NOT NULL DEFAULT FALSE,
    expires_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at                 TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment_deposit PRIMARY KEY (id),
    CONSTRAINT uc_deposit_id_provider UNIQUE (id, provider_code),
    CONSTRAINT fk_deposit_user FOREIGN KEY (user_id) REFERENCES public."user" (id) ON DELETE RESTRICT,
    CONSTRAINT fk_deposit_owned_wallet FOREIGN KEY (wallet_id, user_id)
        REFERENCES wallet (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT uc_deposit_merchant_reference UNIQUE (merchant_reference),
    CONSTRAINT uc_deposit_provider_transaction UNIQUE (provider_code, provider_transaction_id),
    CONSTRAINT ck_deposit_channel CHECK (channel IN ('BANK', 'MOMO')),
    CONSTRAINT ck_deposit_provider_code CHECK (
        provider_code ~ '^[A-Z0-9_]{2,40}$'
        AND ((channel = 'MOMO' AND provider_code = 'MOMO')
            OR (channel = 'BANK' AND provider_code <> 'MOMO'))
    ),
    CONSTRAINT ck_deposit_merchant_reference CHECK (
        merchant_reference ~ '^[A-Za-z0-9_-]{8,100}$'
    ),
    CONSTRAINT ck_deposit_provider_transaction_text CHECK (
        provider_transaction_id IS NULL
        OR (provider_transaction_id <> '' AND provider_transaction_id = btrim(provider_transaction_id))
    ),
    CONSTRAINT ck_deposit_amount_positive CHECK (amount_vnd > 0),
    CONSTRAINT ck_deposit_currency_vnd CHECK (currency = 'VND'),
    CONSTRAINT ck_deposit_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'REVIEW')),
    CONSTRAINT ck_deposit_paid_fields CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL AND provider_transaction_id IS NOT NULL)
        OR (status <> 'PAID' AND paid_at IS NULL)
    ),
    CONSTRAINT ck_deposit_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_deposit_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_deposit_user_created ON payment_deposits (user_id, created_at DESC, id);
CREATE INDEX idx_deposit_status_expires ON payment_deposits (status, expires_at, id);

CREATE FUNCTION enforce_new_deposit_pending()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status <> 'PENDING' OR NEW.provider_transaction_id IS NOT NULL
        OR NEW.paid_at IS NOT NULL OR NEW.needs_reconciliation_review THEN
        RAISE EXCEPTION 'new deposit must start pending without a provider transaction';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_deposit_initial_state
BEFORE INSERT ON payment_deposits
FOR EACH ROW EXECUTE FUNCTION enforce_new_deposit_pending();

CREATE FUNCTION enforce_deposit_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IN ('PAID', 'FAILED', 'EXPIRED') THEN
        IF NOT OLD.needs_reconciliation_review AND NEW.needs_reconciliation_review
            AND NEW.id IS NOT DISTINCT FROM OLD.id
            AND NEW.user_id IS NOT DISTINCT FROM OLD.user_id
            AND NEW.wallet_id IS NOT DISTINCT FROM OLD.wallet_id
            AND NEW.channel IS NOT DISTINCT FROM OLD.channel
            AND NEW.provider_code IS NOT DISTINCT FROM OLD.provider_code
            AND NEW.merchant_reference IS NOT DISTINCT FROM OLD.merchant_reference
            AND NEW.provider_transaction_id IS NOT DISTINCT FROM OLD.provider_transaction_id
            AND NEW.amount_vnd IS NOT DISTINCT FROM OLD.amount_vnd
            AND NEW.currency IS NOT DISTINCT FROM OLD.currency
            AND NEW.status IS NOT DISTINCT FROM OLD.status
            AND NEW.expires_at IS NOT DISTINCT FROM OLD.expires_at
            AND NEW.paid_at IS NOT DISTINCT FROM OLD.paid_at
            AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at
            AND NEW.updated_at >= OLD.updated_at THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'terminal deposit cannot be updated';
    END IF;

    IF NEW.needs_reconciliation_review THEN
        RAISE EXCEPTION 'only terminal deposits can be flagged for reconciliation review';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.user_id IS DISTINCT FROM OLD.user_id
        OR NEW.wallet_id IS DISTINCT FROM OLD.wallet_id
        OR NEW.channel IS DISTINCT FROM OLD.channel
        OR NEW.provider_code IS DISTINCT FROM OLD.provider_code
        OR NEW.merchant_reference IS DISTINCT FROM OLD.merchant_reference
        OR NEW.amount_vnd IS DISTINCT FROM OLD.amount_vnd
        OR NEW.currency IS DISTINCT FROM OLD.currency
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
        RAISE EXCEPTION 'deposit identity and payment terms are immutable';
    END IF;

    IF OLD.provider_transaction_id IS NOT NULL
        AND NEW.provider_transaction_id IS DISTINCT FROM OLD.provider_transaction_id THEN
        RAISE EXCEPTION 'provider transaction identity is immutable';
    END IF;

    IF NEW.status <> OLD.status AND NOT (
        (OLD.status = 'PENDING' AND NEW.status IN ('PAID', 'FAILED', 'EXPIRED', 'REVIEW'))
        OR (OLD.status = 'REVIEW' AND NEW.status IN ('PAID', 'FAILED', 'EXPIRED'))
    ) THEN
        RAISE EXCEPTION 'invalid deposit status transition: % to %', OLD.status, NEW.status;
    END IF;

    RETURN NEW;
END
$$;

CREATE TRIGGER trg_deposit_status_transition
BEFORE UPDATE ON payment_deposits
FOR EACH ROW EXECUTE FUNCTION enforce_deposit_transition();

CREATE FUNCTION reject_payment_record_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'payment records cannot be deleted';
END
$$;

CREATE TRIGGER trg_deposit_no_delete
BEFORE DELETE ON payment_deposits
FOR EACH ROW EXECUTE FUNCTION reject_payment_record_delete();

CREATE TABLE webhook_events
(
    id                      UUID                     NOT NULL,
    deposit_id              UUID,
    provider_code           VARCHAR(40)              NOT NULL,
    provider_event_id       VARCHAR(160)             NOT NULL,
    provider_transaction_id VARCHAR(160),
    request_hash            BYTEA                    NOT NULL,
    result                  VARCHAR(16)              NOT NULL DEFAULT 'RECEIVED',
    error_code              VARCHAR(80),
    received_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at            TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_webhook_event PRIMARY KEY (id),
    CONSTRAINT fk_webhook_deposit_provider FOREIGN KEY (deposit_id, provider_code)
        REFERENCES payment_deposits (id, provider_code) ON DELETE RESTRICT,
    CONSTRAINT uc_webhook_provider_event UNIQUE (provider_code, provider_event_id),
    CONSTRAINT uc_webhook_provider_transaction UNIQUE (provider_code, provider_transaction_id),
    CONSTRAINT ck_webhook_provider_code CHECK (provider_code ~ '^[A-Z0-9_]{2,40}$'),
    CONSTRAINT ck_webhook_event_id CHECK (
        provider_event_id <> '' AND provider_event_id = btrim(provider_event_id)
    ),
    CONSTRAINT ck_webhook_transaction_id CHECK (
        provider_transaction_id IS NULL
        OR (provider_transaction_id <> '' AND provider_transaction_id = btrim(provider_transaction_id))
    ),
    CONSTRAINT ck_webhook_request_hash_length CHECK (octet_length(request_hash) = 32),
    CONSTRAINT ck_webhook_result CHECK (result IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'REVIEW', 'REJECTED')),
    CONSTRAINT ck_webhook_error_code CHECK (error_code IS NULL OR error_code ~ '^[A-Z0-9_]{1,80}$'),
    CONSTRAINT ck_webhook_processing_time CHECK (
        (result = 'RECEIVED' AND processed_at IS NULL)
        OR (result <> 'RECEIVED' AND processed_at IS NOT NULL AND processed_at >= received_at)
    )
);

CREATE INDEX idx_webhook_deposit_received ON webhook_events (deposit_id, received_at DESC, id);

CREATE FUNCTION enforce_new_webhook_received()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.result <> 'RECEIVED' OR NEW.processed_at IS NOT NULL THEN
        RAISE EXCEPTION 'new webhook event must start received';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_webhook_initial_state
BEFORE INSERT ON webhook_events
FOR EACH ROW EXECUTE FUNCTION enforce_new_webhook_received();

CREATE FUNCTION enforce_webhook_event_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.result IN ('PROCESSED', 'IGNORED', 'REJECTED') THEN
        RAISE EXCEPTION 'terminal webhook event cannot be updated';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.provider_code IS DISTINCT FROM OLD.provider_code
        OR NEW.provider_event_id IS DISTINCT FROM OLD.provider_event_id
        OR NEW.provider_transaction_id IS DISTINCT FROM OLD.provider_transaction_id
        OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
        OR NEW.received_at IS DISTINCT FROM OLD.received_at
        OR (OLD.deposit_id IS NOT NULL AND NEW.deposit_id IS DISTINCT FROM OLD.deposit_id) THEN
        RAISE EXCEPTION 'webhook identity and request digest are immutable';
    END IF;

    IF NEW.result <> OLD.result AND NOT (
        (OLD.result = 'RECEIVED' AND NEW.result IN ('PROCESSED', 'IGNORED', 'REVIEW', 'REJECTED'))
        OR (OLD.result = 'REVIEW' AND NEW.result IN ('PROCESSED', 'IGNORED', 'REJECTED'))
    ) THEN
        RAISE EXCEPTION 'invalid webhook result transition: % to %', OLD.result, NEW.result;
    END IF;

    IF OLD.processed_at IS NOT NULL AND NEW.processed_at < OLD.processed_at THEN
        RAISE EXCEPTION 'webhook processing time cannot move backwards';
    END IF;

    RETURN NEW;
END
$$;

CREATE TRIGGER trg_webhook_event_identity
BEFORE UPDATE ON webhook_events
FOR EACH ROW EXECUTE FUNCTION enforce_webhook_event_update();

CREATE TRIGGER trg_webhook_event_no_delete
BEFORE DELETE ON webhook_events
FOR EACH ROW EXECUTE FUNCTION reject_payment_record_delete();
