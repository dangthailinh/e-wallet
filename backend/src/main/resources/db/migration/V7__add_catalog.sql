CREATE TABLE categories
(
    id            UUID                     NOT NULL,
    name          VARCHAR(100)             NOT NULL,
    slug          VARCHAR(120)             NOT NULL,
    display_order INTEGER                  NOT NULL DEFAULT 0,
    status        VARCHAR(16)              NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uc_category_slug UNIQUE (slug),
    CONSTRAINT ck_category_name CHECK (name <> '' AND name = btrim(name)),
    CONSTRAINT ck_category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_category_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_category_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_category_status_order ON categories (status, display_order, id);

CREATE TABLE products
(
    id          UUID                     NOT NULL,
    category_id UUID                     NOT NULL,
    name        VARCHAR(160)             NOT NULL,
    slug        VARCHAR(180)             NOT NULL,
    description TEXT                     NOT NULL,
    price_vnd   BIGINT                   NOT NULL,
    status      VARCHAR(16)              NOT NULL,
    image_url   VARCHAR(2048),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uc_product_slug UNIQUE (slug),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_name CHECK (name <> '' AND name = btrim(name)),
    CONSTRAINT ck_product_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_product_description CHECK (
        description <> '' AND description = btrim(description) AND char_length(description) <= 10000
    ),
    CONSTRAINT ck_product_price_vnd_positive CHECK (price_vnd > 0),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_product_image_url CHECK (image_url IS NULL OR (image_url <> '' AND image_url = btrim(image_url))),
    CONSTRAINT ck_product_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_product_category_status ON products (category_id, status, id);
CREATE INDEX idx_product_status_created_at ON products (status, created_at DESC, id);
