package com.stonewu.fusion.service.ai.run;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.ai.AgentConversation;
import com.stonewu.fusion.entity.ai.AgentMessage;
import com.stonewu.fusion.mapper.ai.AgentConversationMapper;
import com.stonewu.fusion.mapper.ai.AgentMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Serializes message-order allocation on the owning conversation row.
 * <p>Duplicate projections resolve idempotently: a unique-key conflict is retried
 * once against a refreshed counter, and a persistent conflict is treated as an
 * already-persisted message instead of failing the caller.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentMessageAllocator {

    /** Initial insert plus one retry after re-reading the conversation counters. */
    private static final int MAX_INSERT_ATTEMPTS = 2;

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;

    @Transactional
    public long append(String conversationId, AgentMessage message) {
        if (StrUtil.isBlank(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        Objects.requireNonNull(message, "message must not be null");

        AgentConversation conversation = lockConversation(conversationId);
        Integer messageCount = requireValidCount(conversation, conversationId);

        long order = resolveInsertOrder(conversation, conversationId);
        for (int attempt = 1; ; attempt++) {
            message.setConversationId(conversationId);
            message.setMessageOrder(order);
            try {
                if (messageMapper.insert(message) != 1) {
                    throw new IllegalStateException(
                            "Agent message insert did not affect exactly one row");
                }
                break;
            } catch (DataIntegrityViolationException conflict) {
                // (conversation_id, message_order) 或者 projection_key 撞唯一键:
                // 先重新锁定会话行并读取最新计数后重试一次; 若仍冲突, 说明同内容消息
                // 已经落库(如投影恢复与在线写入重复投递), 跳过插入并返回已有顺序,
                // 保证投递幂等且维护调度不会因重复投影反复失败。
                if (attempt >= MAX_INSERT_ATTEMPTS) {
                    return skipDuplicatedMessage(message, order, conflict);
                }
                conversation = lockConversation(conversationId);
                messageCount = requireValidCount(conversation, conversationId);
                order = Math.max(
                        resolveInsertOrder(conversation, conversationId),
                        message.getMessageOrder() + 1);
            }
        }

        conversation.setNextMessageOrder(order + 1);
        conversation.setMessageCount(messageCount + 1);
        conversation.setLastMessageTime(LocalDateTime.now());
        if (conversationMapper.updateById(conversation) != 1) {
            throw new IllegalStateException("Agent conversation counter update did not affect exactly one row");
        }
        return order;
    }

    /**
     * Treats a persistent unique-key conflict as an already-persisted message:
     * keeps the conversation counters untouched and returns the conflicting order.
     */
    private long skipDuplicatedMessage(AgentMessage message,
                                       long order,
                                       DataIntegrityViolationException conflict) {
        log.warn(
                "Agent message insert still conflicts after retry; skipping as already "
                        + "persisted: conversationId={}, attemptedOrder={}, runId={}, projectionKey={}, role={}",
                message.getConversationId(), order, message.getRunId(),
                message.getProjectionKey(), message.getRole(), conflict);
        return order;
    }

    private AgentConversation lockConversation(String conversationId) {
        AgentConversation conversation =
                conversationMapper.selectByConversationIdForUpdate(conversationId);
        if (conversation == null) {
            throw new IllegalStateException("Agent conversation does not exist: " + conversationId);
        }
        return conversation;
    }

    private Integer requireValidCount(AgentConversation conversation, String conversationId) {
        Integer messageCount = conversation.getMessageCount();
        if (messageCount == null || messageCount < 0) {
            throw new IllegalStateException(
                    "Agent conversation has an invalid message count: " + conversationId);
        }
        return messageCount;
    }

    /**
     * Resolves the insert order as the maximum of the conversation counter and the
     * actual persisted rows, healing counters that fell behind existing messages.
     */
    private long resolveInsertOrder(AgentConversation conversation, String conversationId) {
        Long nextMessageOrder = conversation.getNextMessageOrder();
        if (nextMessageOrder == null || nextMessageOrder < 1) {
            throw new IllegalStateException(
                    "Agent conversation has an invalid next message order: " + conversationId);
        }
        AgentMessage latest = messageMapper.selectOne(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getConversationId, conversationId)
                .orderByDesc(AgentMessage::getMessageOrder)
                .last("LIMIT 1"));
        long persistedMaxOrder = latest == null || latest.getMessageOrder() == null
                ? 0L
                : latest.getMessageOrder();
        return Math.max(nextMessageOrder, persistedMaxOrder + 1);
    }
}
