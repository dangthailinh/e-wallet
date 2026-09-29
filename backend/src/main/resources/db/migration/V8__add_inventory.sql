CREATE TABLE inventory_items
(
    id              UUID                     NOT NULL,
    product_id      UUID                     NOT NULL,
    ciphertext      BYTEA                    NOT NULL,
    nonce           BYTEA                    NOT NULL,
    auth_tag        BYTEA                    NOT NULL,
    key_version     INTEGER                  NOT NULL,
    secret_checksum BYTEA                    NOT NULL,
    status          VARCHAR(16)              NOT NULL,
    order_item_id   UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_item PRIMARY KEY (id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT uc_inventory_product_checksum UNIQUE (product_id, secret_checksum),
    CONSTRAINT uc_inventory_order_item UNIQUE (order_item_id),
    CONSTRAINT ck_inventory_ciphertext_length CHECK (octet_length(ciphertext) BETWEEN 1 AND 65536),
    CONSTRAINT ck_inventory_nonce_length CHECK (octet_length(nonce) = 12),
    CONSTRAINT ck_inventory_auth_tag_length CHECK (octet_length(auth_tag) = 16),
    CONSTRAINT ck_inventory_checksum_length CHECK (octet_length(secret_checksum) = 32),
    CONSTRAINT ck_inventory_key_version CHECK (key_version > 0),
    CONSTRAINT ck_inventory_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD', 'DISABLED')),
    CONSTRAINT ck_inventory_sale_link CHECK (
        (status = 'SOLD' AND order_item_id IS NOT NULL)
        OR (status <> 'SOLD' AND order_item_id IS NULL)
    ),
    CONSTRAINT ck_inventory_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_inventory_product_status ON inventory_items (product_id, status, id);
CREATE INDEX idx_inventory_status_updated_at ON inventory_items (status, updated_at, id);

CREATE OR REPLACE FUNCTION catalog_available_count(product_id UUID)
RETURNS BIGINT
LANGUAGE SQL
STABLE
PARALLEL SAFE
AS $$
    SELECT count(*)::BIGINT
    FROM inventory_items AS item
    WHERE item.product_id = $1
      AND status = 'AVAILABLE'
$$;
