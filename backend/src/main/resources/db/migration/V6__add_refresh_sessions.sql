CREATE TABLE refresh_session
(
    id                     UUID                     NOT NULL,
    user_id                BIGINT                   NOT NULL,
    family_id              UUID                     NOT NULL,
    token_hash             CHAR(64)                 NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    rotated_at             TIMESTAMP WITH TIME ZONE,
    replaced_by_session_id UUID,
    revoked_at             TIMESTAMP WITH TIME ZONE,
    revocation_reason      VARCHAR(64),
    version                BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_session PRIMARY KEY (id),
    CONSTRAINT uc_refresh_session_token_hash UNIQUE (token_hash),
    CONSTRAINT uc_refresh_session_identity UNIQUE (id, user_id, family_id),
    CONSTRAINT uc_refresh_session_replacement UNIQUE (replaced_by_session_id),
    CONSTRAINT fk_refresh_session_user FOREIGN KEY (user_id) REFERENCES public."user" (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_session_replacement FOREIGN KEY (replaced_by_session_id, user_id, family_id)
        REFERENCES refresh_session (id, user_id, family_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_refresh_session_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_refresh_session_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_refresh_session_rotation_time CHECK (
        rotated_at IS NULL OR (rotated_at > created_at AND rotated_at < expires_at)
    ),
    CONSTRAINT ck_refresh_session_no_self_replacement CHECK (replaced_by_session_id IS NULL OR replaced_by_session_id <> id),
    CONSTRAINT ck_refresh_session_rotation_pair CHECK (
        (rotated_at IS NULL AND replaced_by_session_id IS NULL)
        OR (rotated_at IS NOT NULL AND replaced_by_session_id IS NOT NULL)
    ),
    CONSTRAINT ck_refresh_session_revocation_time CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_refresh_session_revocation_pair CHECK (
        (revoked_at IS NULL AND revocation_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
    )
);

CREATE INDEX idx_refresh_session_family_id ON refresh_session (family_id);
CREATE INDEX idx_refresh_session_expires_at ON refresh_session (expires_at);
CREATE INDEX idx_refresh_session_active_hash ON refresh_session (token_hash, expires_at)
    WHERE revoked_at IS NULL AND rotated_at IS NULL;

CREATE FUNCTION enforce_refresh_session_immutable_state()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id
        OR NEW.family_id IS DISTINCT FROM OLD.family_id
        OR NEW.token_hash IS DISTINCT FROM OLD.token_hash
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
        RAISE EXCEPTION 'refresh session identity and lifetime are immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_refresh_session_immutable_identity';
    END IF;

    IF OLD.rotated_at IS NOT NULL
        AND (NEW.rotated_at IS DISTINCT FROM OLD.rotated_at
            OR NEW.replaced_by_session_id IS DISTINCT FROM OLD.replaced_by_session_id) THEN
        RAISE EXCEPTION 'refresh session rotation is immutable once recorded'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_refresh_session_immutable_rotation';
    END IF;

    IF OLD.revoked_at IS NOT NULL
        AND (NEW.revoked_at IS DISTINCT FROM OLD.revoked_at
            OR NEW.revocation_reason IS DISTINCT FROM OLD.revocation_reason) THEN
        RAISE EXCEPTION 'refresh session revocation is immutable once recorded'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_refresh_session_immutable_revocation';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER ck_refresh_session_immutable_identity
    BEFORE UPDATE ON refresh_session
    FOR EACH ROW
    EXECUTE FUNCTION enforce_refresh_session_immutable_state();

CREATE FUNCTION enforce_refresh_session_replacement_time()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    successor_created_at TIMESTAMP WITH TIME ZONE;
BEGIN
    IF NEW.replaced_by_session_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT created_at
    INTO successor_created_at
    FROM refresh_session
    WHERE id = NEW.replaced_by_session_id
      AND user_id = NEW.user_id
      AND family_id = NEW.family_id;

    IF successor_created_at IS DISTINCT FROM NEW.rotated_at THEN
        RAISE EXCEPTION 'refresh session successor must start at the rotation time'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_refresh_session_replacement_time';
    END IF;

    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER ck_refresh_session_replacement_time
    AFTER INSERT OR UPDATE OF replaced_by_session_id, rotated_at ON refresh_session
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION enforce_refresh_session_replacement_time();
