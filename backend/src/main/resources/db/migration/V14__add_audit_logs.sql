CREATE TABLE audit_logs
(
    id                 UUID                     NOT NULL,
    actor_user_id      BIGINT                   NOT NULL,
    action             VARCHAR(80)              NOT NULL,
    resource_type      VARCHAR(80)              NOT NULL,
    resource_id        UUID                     NOT NULL,
    parent_resource_id UUID,
    request_id         VARCHAR(128)             NOT NULL,
    metadata           JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id)
        REFERENCES public."user" (id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_log_action CHECK (action ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT ck_audit_log_resource_type CHECK (resource_type ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT ck_audit_log_request_id CHECK (
        request_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$'
    ),
    CONSTRAINT ck_audit_log_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_audit_log_actor_created
    ON audit_logs (actor_user_id, created_at DESC, id);

CREATE INDEX idx_audit_log_resource_created
    ON audit_logs (resource_type, resource_id, created_at DESC, id);

CREATE FUNCTION reject_audit_log_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit logs cannot be changed or deleted';
END
$$;

CREATE TRIGGER trg_audit_log_immutable
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION reject_audit_log_change();
