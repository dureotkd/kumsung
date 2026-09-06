ALTER TABLE customer_media_posts
    DROP CONSTRAINT customer_media_posts_post_type_check;

ALTER TABLE customer_media_posts
    ADD CONSTRAINT customer_media_posts_post_type_check CHECK(post_type IN (
        'SNS_CHANNEL',
        'NEW_DEVELOPMENT_PROGRAM',
        'COMPANY_NEWS',
        'CONSTRUCTION_CASE',
        'OTHER_INQUIRY'
    ));

ALTER TABLE customer_media_posts
    ALTER COLUMN image_key DROP NOT NULL,
    ALTER COLUMN image_original_name DROP NOT NULL,
    ALTER COLUMN image_content_type DROP NOT NULL,
    ALTER COLUMN image_size DROP NOT NULL,
    ADD COLUMN link_url VARCHAR(1000),
    ADD COLUMN file_key VARCHAR(500),
    ADD COLUMN file_original_name VARCHAR(255),
    ADD COLUMN file_content_type VARCHAR(120),
    ADD COLUMN file_size BIGINT;
