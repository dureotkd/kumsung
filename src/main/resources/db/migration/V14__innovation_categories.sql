ALTER TABLE innovation_resources
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'TECHNICAL'
    CHECK(category IN ('RND','PATENT_CERT','TECHNICAL','KNOWLEDGE','SMART_FACTORY'));

CREATE INDEX idx_innovation_resources_category
    ON innovation_resources(category,published,display_order,created_at DESC,id DESC);
