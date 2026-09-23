-- AI Drama OS PR-005: StoryboardItem production fields
-- All nullable/default-safe; old data requires no backfill

ALTER TABLE afv_storyboard_item
    ADD COLUMN production_status VARCHAR(32) DEFAULT 'NONE' COMMENT '镜头在生产流程中的状态，NONE 表示尚未进入生产，其余由生产步骤推进',
    ADD COLUMN selected_take_id BIGINT DEFAULT NULL COMMENT '已选定的镜头产物标识，选片唯一真相来源',
    ADD COLUMN workflow_profile_id BIGINT DEFAULT NULL COMMENT '该镜头使用的工作流方案标识';

CREATE INDEX idx_storyboard_item_production_status ON afv_storyboard_item (production_status);
CREATE INDEX idx_storyboard_item_selected_take ON afv_storyboard_item (selected_take_id);
