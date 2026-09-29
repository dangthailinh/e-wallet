-- Inventory is introduced in a later migration. This stable function is the
-- catalog/inventory boundary: until inventory exists, every product truthfully
-- has zero sellable items. The inventory migration replaces only its body.
CREATE FUNCTION catalog_available_count(product_id UUID)
RETURNS BIGINT
LANGUAGE SQL
STABLE
PARALLEL SAFE
AS $$
    SELECT 0::BIGINT
$$;
