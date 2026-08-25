ALTER TABLE shop_orders
    DROP CONSTRAINT IF EXISTS shop_orders_quantity_check;

ALTER TABLE shop_orders
    ADD CONSTRAINT shop_orders_quantity_check CHECK (quantity BETWEEN 1 AND 1000);

ALTER TABLE shop_inquiry_items
    DROP CONSTRAINT IF EXISTS shop_inquiry_items_quantity_check;

ALTER TABLE shop_inquiry_items
    ADD CONSTRAINT shop_inquiry_items_quantity_check CHECK (quantity BETWEEN 1 AND 1000);
