CREATE TABLE orders
(
    id              UUID                     NOT NULL,
    user_id         BIGINT                   NOT NULL,
    wallet_id       BIGINT                   NOT NULL,
    idempotency_key VARCHAR(100)             NOT NULL,
    status          VARCHAR(16)              NOT NULL,
    total_vnd       BIGINT                   NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_order PRIMARY KEY (id),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES public."user" (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_owned_wallet FOREIGN KEY (wallet_id, user_id)
        REFERENCES wallet (id, user_id) ON DELETE RESTRICT,
    CONSTRAINT uc_order_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_order_idempotency_key CHECK (
        idempotency_key <> '' AND idempotency_key = btrim(idempotency_key)
    ),
    CONSTRAINT ck_order_status CHECK (status IN ('COMPLETED', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_order_total_positive CHECK (total_vnd > 0),
    CONSTRAINT ck_order_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_order_user_created ON orders (user_id, created_at DESC, id);

CREATE TABLE order_items
(
    id                UUID                     NOT NULL,
    order_id          UUID                     NOT NULL,
    product_id        UUID                     NOT NULL,
    inventory_item_id UUID                     NOT NULL,
    product_name      VARCHAR(160)             NOT NULL,
    unit_price_vnd    BIGINT                   NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_item PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_item_inventory FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT uc_order_item_inventory UNIQUE (inventory_item_id),
    CONSTRAINT ck_order_item_name CHECK (product_name <> '' AND product_name = btrim(product_name)),
    CONSTRAINT ck_order_item_price_positive CHECK (unit_price_vnd > 0)
);

CREATE INDEX idx_order_item_order ON order_items (order_id, id);

ALTER TABLE inventory_items
    ADD CONSTRAINT uc_inventory_id_product UNIQUE (id, product_id);

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_item_matching_product FOREIGN KEY (inventory_item_id, product_id)
        REFERENCES inventory_items (id, product_id) ON DELETE RESTRICT;

ALTER TABLE inventory_items
    ADD CONSTRAINT uc_inventory_sold_pair UNIQUE (order_item_id, id);

ALTER TABLE order_items
    ADD CONSTRAINT uc_order_item_stock_pair UNIQUE (id, inventory_item_id);

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_item_sold_stock FOREIGN KEY (id, inventory_item_id)
        REFERENCES inventory_items (order_item_id, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE inventory_items
    ADD CONSTRAINT fk_inventory_sold_order_item FOREIGN KEY (order_item_id, id)
        REFERENCES order_items (id, inventory_item_id) DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION reject_order_item_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'sold order items cannot be changed or deleted';
END
$$;

CREATE TRIGGER trg_order_item_immutable
BEFORE UPDATE OR DELETE ON order_items
FOR EACH ROW EXECUTE FUNCTION reject_order_item_change();
