ALTER TABLE shop_products
    ADD COLUMN category VARCHAR(80),
    ADD COLUMN image_url VARCHAR(500),
    ADD COLUMN image_key VARCHAR(500),
    ADD COLUMN image_original_name VARCHAR(255),
    ADD COLUMN image_content_type VARCHAR(120),
    ADD COLUMN image_size BIGINT;

UPDATE shop_products SET category='PD', image_url='/images/product-dry-pd-v1.png' WHERE code='DRY_PD';
UPDATE shop_products SET category='소방', image_url='/images/product-fire-hydrant-v1.png' WHERE code='FIRE_HYDRANT_BOX';
UPDATE shop_products SET category='소방', image_url='/images/product-waterproof-box-v1.png' WHERE code='WATERPROOF_EQUIPMENT_BOX';
UPDATE shop_products SET category='내진', image_url='/images/product-seismic-frame-v1.png' WHERE code='SEISMIC_FRAME';

INSERT INTO shop_products(code,name,category,description,active,display_order) VALUES
('ELECTRIC_PANEL_BOX','전기 판넬 외함','전기','설치 환경과 도면에 맞춘 판넬 외함 주문 제작',TRUE,50),
('DISTRIBUTION_BOX','분전반 외함','전기','현장 규격에 맞춘 분전반 외함 제작',TRUE,60),
('CONTROL_PANEL_BOX','제어반 외함','전기','제어기기 구성과 설치 조건을 반영한 외함 제작',TRUE,70),
('MCC_PANEL_BOX','MCC반 외함','전기','모터 제어반 구성에 맞춘 외함 주문 제작',TRUE,80),
('COMMUNICATION_BOX','통신 단자함','통신','통신 설비 규격과 설치 위치에 맞춘 단자함',TRUE,90),
('METER_BOX','계량기함','전기','계량기 규격과 현장 조건을 반영한 제작',TRUE,100),
('EPS_INSPECTION_DOOR','EPS 점검함','건축설비','EPS 공간 점검과 유지보수를 위한 맞춤 제작',TRUE,110),
('TPS_INSPECTION_DOOR','TPS 점검함','건축설비','TPS 공간 규격에 맞춘 점검함 제작',TRUE,120),
('OUTDOOR_WATERPROOF_BOX','옥외 방수함','방수','옥외 환경과 방수 요구 조건을 반영한 외함',TRUE,130),
('STAINLESS_ENCLOSURE','스테인리스 외함','금속가공','내식성이 필요한 환경을 위한 스테인리스 외함',TRUE,140),
('PULL_BOX','풀박스','전기','배선 경로와 도면 규격에 맞춘 풀박스',TRUE,150),
('JUNCTION_BOX','정션박스','전기','배선 접속과 분기에 필요한 맞춤 정션박스',TRUE,160),
('GROUND_TERMINAL_BOX','접지 단자함','전기','접지 구성과 설치 조건에 맞춘 단자함',TRUE,170),
('CABLE_TRAY','케이블 트레이','금속가공','케이블 배선 경로와 하중을 고려한 주문 제작',TRUE,180),
('CABLE_DUCT','케이블 덕트','금속가공','현장 치수와 배선 용량에 맞춘 덕트 제작',TRUE,190),
('FIRE_EXTINGUISHER_BOX','소화기함','소방','소화기 규격과 설치 위치에 맞춘 함체 제작',TRUE,200),
('FIRE_CONTROL_BOX','방재함','소방','방재 설비 구성에 맞춘 금속 함체 제작',TRUE,210),
('EMERGENCY_EQUIPMENT_BOX','비상장비함','안전','비상장비 보관 용도에 맞춘 주문 제작',TRUE,220),
('SEISMIC_BASE_FRAME','내진 베이스 프레임','내진','장비 제원과 내진 요구사항을 반영한 베이스',TRUE,230),
('EQUIPMENT_SUPPORT','장비 받침대','금속가공','장비 크기와 하중에 맞춘 받침 구조물',TRUE,240),
('CUSTOM_METAL_BOX','주문제작 금속함','주문제작','도면·치수·재질에 맞춘 각종 금속함 제작 상담',TRUE,250)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE innovation_resources (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    image_key VARCHAR(500) NOT NULL,
    image_original_name VARCHAR(255) NOT NULL,
    image_content_type VARCHAR(120) NOT NULL,
    image_size BIGINT NOT NULL,
    file_key VARCHAR(500) NOT NULL,
    file_original_name VARCHAR(255) NOT NULL,
    file_content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_innovation_resources_public
    ON innovation_resources(published,display_order,created_at DESC,id DESC);

CREATE TABLE customer_media_posts (
    id BIGSERIAL PRIMARY KEY,
    post_type VARCHAR(30) NOT NULL CHECK(post_type IN ('COMPANY_NEWS','CONSTRUCTION_CASE')),
    title VARCHAR(180) NOT NULL,
    content TEXT,
    image_key VARCHAR(500) NOT NULL,
    image_original_name VARCHAR(255) NOT NULL,
    image_content_type VARCHAR(120) NOT NULL,
    image_size BIGINT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_media_posts_public
    ON customer_media_posts(post_type,published,pinned DESC,created_at DESC,id DESC);
