"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { AgentConversation } from "@/lib/api/ai-assistant";
import { listMessages } from "@/lib/api/ai-assistant";
import {
  getTaskStreamStatus,
  isTerminalTaskStreamStatus,
  listRunningTaskStreams,
  type TaskStreamStatus,
} from "@/lib/api/task-stream";

/**
 * 通知面板内的 category=task 实时任务视图模型。
 * taskId 即任务流会话的 conversationId（后端 TaskStreamService 约定）。
 */
export interface LiveTaskStreamItem {
  taskId: string;
  dbId: number;
  title: string;
  agentType?: string;
  projectId: number | null;
  contextType?: string;
  contextId?: number;
  createTime?: string;
  status: "running" | "done" | "error";
  /** 最近一次 publishContent 的进度文本。 */
  progressText: string;
  /** 终态摘要（DONE 内容或失败信息）。 */
  summary?: string;
}

const LIST_REFRESH_INTERVAL_MS = 15000;

function toLiveItem(conversation: AgentConversation): LiveTaskStreamItem {
  return {
    taskId: conversation.conversationId,
    dbId: conversation.id,
    title: conversation.title || "后台任务",
    agentType: conversation.agentType,
    projectId: conversation.projectId,
    contextType: conversation.contextType,
    contextId: conversation.contextId,
    createTime: conversation.createTime,
    status: "running",
    progressText: "",
  };
}

/** 取最后一条 assistant 消息：后端 publishContent/complete/fail 都会落一条消息。 */
export async function fetchTaskStreamLastMessage(taskId: string): Promise<string> {
  const messages = await listMessages(taskId);
  for (let index = messages.length - 1; index >= 0; index--) {
    const message = messages[index];
    if (message.role === "assistant" && message.content) {
      return message.content;
    }
  }
  return "";
}

/** 查询任务流 Redis 状态；失败返回 null（视为暂时不可知，不改变本地状态）。 */
export async function probeTaskStreamStatus(
  taskId: string
): Promise<TaskStreamStatus | null> {
  try {
    return (await getTaskStreamStatus(taskId)) as TaskStreamStatus;
  } catch (err) {
    console.warn("[TaskStreamPanel] 查询任务状态失败:", taskId, err);
    return null;
  }
}

/**
 * 面板打开期间的 category=task 运行中任务列表：
 * - 挂载即拉取 /api/task-stream/running，之后周期兜底刷新，覆盖面板关闭期间错过的状态变化；
 * - 新任务先探测 Redis 状态：NONE（僵尸记录，后端任务已不存在）不展示；
 *   COMPLETED/ERROR 直接以终态展示完成摘要；
 * - 已消失的本地运行中任务同样按状态补一次终态归位。
 */
export function useLiveTaskStreams(excludeConversationIds: string): {
  items: LiveTaskStreamItem[];
  updateProgress: (taskId: string, text: string) => void;
  settleItem: (taskId: string, status: "done" | "error", summary?: string) => void;
  removeItem: (taskId: string) => void;
} {
  const [items, setItems] = useState<LiveTaskStreamItem[]>([]);
  const itemsRef = useRef<LiveTaskStreamItem[]>([]);
  const excludeRef = useRef(excludeConversationIds);

  // 刷新定时器在 effect 外读取最新值；ref 同步放在 effect 中完成。
  useEffect(() => {
    itemsRef.current = items;
  }, [items]);
  useEffect(() => {
    excludeRef.current = excludeConversationIds;
  }, [excludeConversationIds]);

  const settleItem = useCallback(
    (taskId: string, status: "done" | "error", summary?: string) => {
      let settled = false;
      setItems((current) =>
        current.map((item) => {
          if (item.taskId !== taskId || item.status !== "running") return item;
          settled = true;
          return {
            ...item,
            status,
            summary: summary ?? item.summary,
            progressText: "",
          };
        })
      );
      if (settled && summary === undefined) {
        // 状态轮询归位时拿不到 DONE 内容，补拉最后一条消息作为摘要。
        void fetchTaskStreamLastMessage(taskId)
          .then((text) => {
            if (!text) return;
            setItems((current) =>
              current.map((item) =>
                item.taskId === taskId && !item.summary
                  ? { ...item, summary: text }
                  : item
              )
            );
          })
          .catch(() => {});
      }
    },
    []
  );

  const updateProgress = useCallback((taskId: string, text: string) => {
    setItems((current) =>
      current.map((item) =>
        item.taskId === taskId && item.status === "running"
          ? { ...item, progressText: text }
          : item
      )
    );
  }, []);

  const removeItem = useCallback((taskId: string) => {
    setItems((current) => current.filter((item) => item.taskId !== taskId));
  }, []);

  const refresh = useCallback(async () => {
    let running: AgentConversation[];
    try {
      running = await listRunningTaskStreams();
    } catch (err) {
      console.warn("[TaskStreamPanel] 拉取运行中任务流失败:", err);
      return;
    }

    const excluded = new Set(
      excludeRef.current ? excludeRef.current.split("|") : []
    );
    const candidates = running.filter(
      (conversation) =>
        conversation.category === "task" &&
        conversation.conversationId &&
        !excluded.has(conversation.conversationId)
    );
    const candidateIds = new Set(candidates.map((item) => item.conversationId));

    // 本地运行中但服务端列表已消失：按状态补终态或移除。
    const disappeared = itemsRef.current.filter(
      (item) => item.status === "running" && !candidateIds.has(item.taskId)
    );
    for (const item of disappeared) {
      const status = await probeTaskStreamStatus(item.taskId);
      if (status === "COMPLETED" || status === "ERROR") {
        settleItem(item.taskId, status === "COMPLETED" ? "done" : "error");
      } else if (status === "NONE") {
        removeItem(item.taskId);
      }
    }

    // 新出现的任务：先探测状态，避免展示僵尸记录或错过已完成任务。
    const knownIds = new Set(itemsRef.current.map((item) => item.taskId));
    const fresh = candidates.filter(
      (conversation) => !knownIds.has(conversation.conversationId)
    );
    for (const conversation of fresh) {
      const status = await probeTaskStreamStatus(conversation.conversationId);
      if (!status || status === "NONE") continue;
      if (isTerminalTaskStreamStatus(status)) {
        const summary = await fetchTaskStreamLastMessage(
          conversation.conversationId
        ).catch(() => "");
        setItems((current) =>
          current.some((item) => item.taskId === conversation.conversationId)
            ? current
            : [
                ...current,
                {
                  ...toLiveItem(conversation),
                  status: status === "COMPLETED" ? "done" : "error",
                  summary,
                },
              ]
        );
      } else {
        setItems((current) =>
          current.some((item) => item.taskId === conversation.conversationId)
            ? current
            : [...current, toLiveItem(conversation)]
        );
      }
    }
  }, [removeItem, settleItem]);

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => {
      void refresh();
    }, LIST_REFRESH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  return { items, updateProgress, settleItem, removeItem };
}
