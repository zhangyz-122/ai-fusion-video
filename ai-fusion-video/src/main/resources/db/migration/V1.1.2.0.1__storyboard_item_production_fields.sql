-- AI Drama OS PR-005: StoryboardItem production fields
-- All nullable/default-safe; old data requires no backfill

ALTER TABLE afv_storyboard_item
    ADD COLUMN production_status VARCHAR(32) DEFAULT 'NONE',
    ADD COLUMN selected_take_id BIGINT DEFAULT NULL,
    ADD COLUMN workflow_profile_id BIGINT DEFAULT NULL;

CREATE INDEX idx_storyboard_item_production_status (production_status);
CREATE INDEX idx_storyboard_item_selected_take (selected_take_id);
