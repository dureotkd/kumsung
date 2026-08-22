-- The accessory rows in the fixed SMART SHOP catalog are now directly purchasable.
UPDATE shop_products
SET price = CASE code
        WHEN 'ACCESSORY_HANDLE' THEN 1000
        WHEN 'ACCESSORY_PIN' THEN 500
        WHEN 'ACCESSORY_PUSH_BUTTON' THEN 2500
        WHEN 'OTHER_1' THEN 10000
        WHEN 'OTHER_2' THEN 10000
    END,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE code IN (
    'ACCESSORY_HANDLE',
    'ACCESSORY_PIN',
    'ACCESSORY_PUSH_BUTTON',
    'OTHER_1',
    'OTHER_2'
);
