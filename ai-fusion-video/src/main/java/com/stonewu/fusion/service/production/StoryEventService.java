package com.stonewu.fusion.service.production;

import org.springframework.stereotype.Service;

/**
 * PR-021: Story Event Store — append-only 故事状态事件
 * 事件类型：CHARACTER_STATE_CHANGED / PROP_STATE_CHANGED / RELATION_CHANGED /
 *           LOCATION_CHANGED / FACT_REVEALED / OPEN_LOOP_CREATED / OPEN_LOOP_RESOLVED /
 *           WARDROBE_CHANGED / INJURY_CHANGED
 * 历史不可就地修改；idempotency 防重复。
 */
@Service
public class StoryEventService {
    // 需要 Flyway 迁移创建 story_event 表 + StoryEvent entity/mapper
    // 本 service 提供 append / replay / snapshot 方法
    // 具体实现在 PR-021 详细实施中完成
}
