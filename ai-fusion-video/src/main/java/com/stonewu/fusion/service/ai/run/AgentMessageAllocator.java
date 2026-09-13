package com.stonewu.fusion.service.ai.run;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.ai.AgentConversation;
import com.stonewu.fusion.entity.ai.AgentMessage;
import com.stonewu.fusion.mapper.ai.AgentConversationMapper;
import com.stonewu.fusion.mapper.ai.AgentMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Serializes message-order allocation on the owning conversation row.
 */
@Service
@RequiredArgsConstructor
public class AgentMessageAllocator {

    /** Bounded retries when a concurrent writer wins the unique-key race. */
    private static final int MAX_INSERT_ATTEMPTS = 3;

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
                // 重新锁定会话行并读取最新计数后重试,避免投影恢复与在线写入竞争时
                // 计数器落后于已落库消息导致 DataIntegrityViolation 反复失败。
                if (attempt >= MAX_INSERT_ATTEMPTS) {
                    throw conflict;
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
