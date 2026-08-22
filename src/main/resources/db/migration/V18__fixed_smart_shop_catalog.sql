-- The public SMART SHOP uses the approved three-section catalog.
-- Existing managed products stay available to administrators, but only this catalog is public by default.
UPDATE shop_products
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO shop_products(code,name,category,description,price,active,display_order,image_url)
VALUES
    ('DRY_PD','건식PD 표준형','표준 규격 제품','300×300 · STEEL 2.3T',128000,TRUE,10,'/images/product-dry-pd-v1.png'),
    ('FIRE_HYDRANT_BOX','소화전함 표준형','표준 규격 제품','매립형 · STEEL 1.6T',96000,TRUE,20,'/images/product-fire-hydrant-v1.png'),
    ('WATERPROOF_EQUIPMENT_BOX','방수기구함 표준형','표준 규격 제품','STEEL 1.6T',89000,TRUE,30,'/images/product-waterproof-box-v1.png'),
    ('SEISMIC_FRAME','내진다이 표준형','표준 규격 제품','STEEL 2.3T',145000,TRUE,40,'/images/product-seismic-frame-v1.png'),
    ('SITE_GANGNAM','강남 현장','현장별 개별 결제','견적 확정 완료',1000000,TRUE,110,NULL),
    ('SITE_SEOCHO','서초 현장','현장별 개별 결제','견적 확정 완료',500000,TRUE,120,NULL),
    ('SITE_BANPO','반포 현장','현장별 개별 결제','견적 확정 완료',100000,TRUE,130,NULL),
    ('ACCESSORY_HANDLE','손잡이','기타','필요한 규격과 수량을 남겨 주세요.',NULL,TRUE,210,NULL),
    ('ACCESSORY_PIN','핀','기타','필요한 규격과 수량을 남겨 주세요.',NULL,TRUE,220,NULL),
    ('ACCESSORY_PUSH_BUTTON','푸시버튼','기타','필요한 규격과 수량을 남겨 주세요.',NULL,TRUE,230,NULL),
    ('OTHER_1','기타1','기타','필요한 항목을 문의해 주세요.',NULL,TRUE,240,NULL),
    ('OTHER_2','기타2','기타','필요한 항목을 문의해 주세요.',NULL,TRUE,250,NULL)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    price = EXCLUDED.price,
    active = EXCLUDED.active,
    display_order = EXCLUDED.display_order,
    image_url = COALESCE(shop_products.image_url, EXCLUDED.image_url),
    updated_at = CURRENT_TIMESTAMP;
