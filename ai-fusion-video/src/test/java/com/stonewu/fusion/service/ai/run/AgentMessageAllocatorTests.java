package com.stonewu.fusion.service.ai.run;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stonewu.fusion.entity.ai.AgentConversation;
import com.stonewu.fusion.entity.ai.AgentMessage;
import com.stonewu.fusion.mapper.ai.AgentConversationMapper;
import com.stonewu.fusion.mapper.ai.AgentMessageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentMessageAllocatorTests {

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentConversation.class);
        TableInfoHelper.initTableInfo(assistant, AgentMessage.class);
    }

    @Mock
    private AgentConversationMapper conversationMapper;

    @Mock
    private AgentMessageMapper messageMapper;

    private AgentMessageAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new AgentMessageAllocator(conversationMapper, messageMapper);
    }

    @Test
    void appendHealsCounterWhenPersistedMessagesAreAheadOfConversationCounter() {
        AgentConversation conversation = AgentConversation.builder()
                .conversationId("conv-1")
                .nextMessageOrder(3L)
                .messageCount(2)
                .build();
        when(conversationMapper.selectByConversationIdForUpdate("conv-1")).thenReturn(conversation);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(
                AgentMessage.builder().conversationId("conv-1").messageOrder(7L).build());
        when(messageMapper.insert(any(AgentMessage.class))).thenReturn(1);
        when(conversationMapper.updateById(any(AgentConversation.class))).thenReturn(1);

        long order = allocator.append("conv-1", AgentMessage.builder().role("assistant").build());

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper).insert(messageCaptor.capture());
        assertThat(order).isEqualTo(8L);
        assertThat(messageCaptor.getValue().getMessageOrder()).isEqualTo(8L);
        assertThat(messageCaptor.getValue().getConversationId()).isEqualTo("conv-1");

        ArgumentCaptor<AgentConversation> conversationCaptor =
                ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationMapper).updateById(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getNextMessageOrder()).isEqualTo(9L);
        assertThat(conversationCaptor.getValue().getMessageCount()).isEqualTo(3);
    }

    @Test
    void appendRetriesWithRefreshedOrderWhenUniqueKeyConflicts() {
        AgentConversation stale = AgentConversation.builder()
                .conversationId("conv-1")
                .nextMessageOrder(5L)
                .messageCount(4)
                .build();
        AgentConversation refreshed = AgentConversation.builder()
                .conversationId("conv-1")
                .nextMessageOrder(6L)
                .messageCount(5)
                .build();
        when(conversationMapper.selectByConversationIdForUpdate("conv-1"))
                .thenReturn(stale, refreshed);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // 记录每次插入时的 messageOrder：入参对象在重试中被复用，必须在调用时捕获
        List<Long> attemptedOrders = new ArrayList<>();
        when(messageMapper.insert(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage inserted = invocation.getArgument(0);
            attemptedOrders.add(inserted.getMessageOrder());
            if (attemptedOrders.size() == 1) {
                throw new DataIntegrityViolationException("uk_agent_message_conv_order");
            }
            return 1;
        });
        when(conversationMapper.updateById(any(AgentConversation.class))).thenReturn(1);

        long order = allocator.append("conv-1", AgentMessage.builder().role("tool").build());

        assertThat(attemptedOrders).containsExactly(5L, 6L);
        assertThat(order).isEqualTo(6L);
        verify(conversationMapper, times(2)).selectByConversationIdForUpdate("conv-1");

        ArgumentCaptor<AgentConversation> conversationCaptor =
                ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationMapper).updateById(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getNextMessageOrder()).isEqualTo(7L);
        assertThat(conversationCaptor.getValue().getMessageCount()).isEqualTo(6);
    }

    @Test
    void appendStopsAfterBoundedRetriesWhenConflictPersists() {
        AgentConversation conversation = AgentConversation.builder()
                .conversationId("conv-1")
                .nextMessageOrder(1L)
                .messageCount(0)
                .build();
        when(conversationMapper.selectByConversationIdForUpdate("conv-1")).thenReturn(conversation);
        when(messageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(messageMapper.insert(any(AgentMessage.class)))
                .thenThrow(new DataIntegrityViolationException("uk_agent_message_conv_order"));

        assertThatThrownBy(() -> allocator.append("conv-1", AgentMessage.builder().role("user").build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(messageMapper, times(3)).insert(any(AgentMessage.class));
        verify(conversationMapper, never()).updateById(any(AgentConversation.class));
    }
}
