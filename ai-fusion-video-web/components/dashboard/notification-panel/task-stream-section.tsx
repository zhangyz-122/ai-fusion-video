"use client";

import { useEffect, useMemo, useRef } from "react";
import { usePipelineStore } from "@/lib/store/pipeline-store";
import { TaskStreamItemCard } from "./task-stream-item-card";
import { useLiveTaskStreams, type LiveTaskStreamItem } from "./task-streams";

/**
 * 通知面板中的 category=task 实时任务区块：
 * 运行中的任务流卡片（SSE 实时进度 + 断线轮询兜底）与本次面板会话内的终态结果。
 * 任务已由 pipeline-store 跟踪（如分镜页合成的 attachTaskStream）时自动去重。
 * 无任务时渲染 null。
 */
export function TaskStreamSection({
  onItemsChange,
  className,
}: {
  onItemsChange?: (items: LiveTaskStreamItem[]) => void;
  /** 有内容时的根容器样式（由宿主面板决定留白）。 */
  className?: string;
}) {
  // 仅订阅 pipeline-store 任务跟踪的会话 ID 串（原始值），避免时间线高频更新引发重渲染。
  const trackedConversationKey = usePipelineStore((state) =>
    state.tasks.map((task) => task.state.conversationId ?? "").join("|")
  );
  const { items, updateProgress, settleItem, removeItem } = useLiveTaskStreams(
    trackedConversationKey
  );

  const running = items.filter((item) => item.status === "running");
  const settled = items.filter((item) => item.status !== "running");

  const signature = useMemo(
    () => items.map((item) => `${item.taskId}:${item.status}`).join("|"),
    [items]
  );
  const lastSignatureRef = useRef<string | null>(null);
  useEffect(() => {
    if (lastSignatureRef.current === signature) return;
    lastSignatureRef.current = signature;
    onItemsChange?.(items);
  }, [signature, items, onItemsChange]);

  if (items.length === 0) return null;

  return (
    <div className={className}>
      {running.length > 0 && (
        <div>
          <p className="text-[10px] font-medium text-muted-foreground px-2 py-1 uppercase tracking-wider">
            实时任务 ({running.length})
          </p>
          <div className="space-y-1.5 px-1">
            {running.map((item) => (
              <TaskStreamItemCard
                key={item.taskId}
                item={item}
                updateProgress={updateProgress}
                settleItem={settleItem}
                removeItem={removeItem}
              />
            ))}
          </div>
        </div>
      )}
      {settled.length > 0 && (
        <div className={running.length > 0 ? "pt-2" : undefined}>
          <p className="text-[10px] font-medium text-muted-foreground px-2 py-1 uppercase tracking-wider">
            任务结果 ({settled.length})
          </p>
          <div className="space-y-1.5 px-1">
            {settled.map((item) => (
              <TaskStreamItemCard
                key={item.taskId}
                item={item}
                updateProgress={updateProgress}
                settleItem={settleItem}
                removeItem={removeItem}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
