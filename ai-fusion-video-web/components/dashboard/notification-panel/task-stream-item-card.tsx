"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { reconnectTaskStream } from "@/lib/api/task-stream";
import { cn } from "@/lib/utils";
import { formatElapsed, parseTaskContent } from "./utils";
import {
  fetchTaskStreamLastMessage,
  probeTaskStreamStatus,
  type LiveTaskStreamItem,
} from "./task-streams";

/** SSE 断开后的状态轮询间隔。 */
const POLL_INTERVAL_MS = 5000;
/** 流异常结束后允许的重订次数，超过后转入纯轮询兜底。 */
const MAX_STREAM_RESUBSCRIBE = 2;

const CONTEXT_LINKS: Record<
  string,
  { label: string; path: (projectId: number) => string }
> = {
  script: {
    label: "查看剧本",
    path: (projectId) => `/projects/${projectId}/scripts`,
  },
  storyboard_episode: {
    label: "查看分镜",
    path: (projectId) => `/projects/${projectId}/storyboards`,
  },
};

function useNowTicker(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [active]);
  return now;
}

function ElapsedBadge({ item }: { item: LiveTaskStreamItem }) {
  const now = useNowTicker(item.status === "running");
  if (!item.createTime) return null;
  const start = new Date(item.createTime).getTime();
  if (!Number.isFinite(start)) return null;
  return (
    <span className="text-[10px] text-muted-foreground/70 shrink-0 tabular-nums">
      {formatElapsed(Math.max(0, now - start))}
    </span>
  );
}

function TaskStreamSummary({ item }: { item: LiveTaskStreamItem }) {
  if (!item.summary) {
    return item.status === "error" ? (
      <p className="mt-1 pl-[22px] text-[11px] text-muted-foreground">
        任务失败，可稍后在历史记录中查看详情
      </p>
    ) : null;
  }

  const { markdownContent, mediaLinks } = parseTaskContent(item.summary);
  const contextLink =
    item.projectId != null && item.contextType
      ? CONTEXT_LINKS[item.contextType]
      : undefined;

  return (
    <>
      {markdownContent && (
        <p className="mt-1 pl-[22px] text-[11px] text-muted-foreground whitespace-pre-line break-words">
          {markdownContent}
        </p>
      )}
      {(mediaLinks.length > 0 || contextLink) && (
        <div className="mt-1.5 pl-[22px] flex flex-wrap items-center gap-x-3 gap-y-1">
          {contextLink && (
            <Link
              href={contextLink.path(item.projectId!)}
              className="text-[11px] text-primary/90 hover:text-primary hover:underline underline-offset-2"
            >
              {contextLink.label}
            </Link>
          )}
          {mediaLinks.map((media) => (
            <a
              key={media.rawUrl}
              href={media.resolvedUrl}
              target="_blank"
              rel="noreferrer"
              className="text-[11px] text-primary/90 hover:text-primary hover:underline underline-offset-2"
            >
              打开{media.label}
            </a>
          ))}
        </div>
      )}
    </>
  );
}

/**
 * category=task 任务流卡片：
 * - 运行中：订阅 reconnect SSE 实时渲染 publishContent 进度文本；
 *   SSE 断开或流未带终态结束时回退为状态轮询（5s），持续回填最新进度；
 * - 终态：展示完成/失败摘要与产物链接（摘要中的媒体地址、剧本/分镜入口）。
 */
export function TaskStreamItemCard({
  item,
  updateProgress,
  settleItem,
  removeItem,
}: {
  item: LiveTaskStreamItem;
  updateProgress: (taskId: string, text: string) => void;
  settleItem: (taskId: string, status: "done" | "error", summary?: string) => void;
  removeItem: (taskId: string) => void;
}) {
  const isRunning = item.status === "running";

  useEffect(() => {
    if (!isRunning) return;
    let disposed = false;
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let controller: AbortController | null = null;
    let resubscribeCount = 0;

    const stopPolling = () => {
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
    };

    const pollTick = async () => {
      const status = await probeTaskStreamStatus(item.taskId);
      if (disposed) return;
      if (status === "COMPLETED" || status === "ERROR") {
        stopPolling();
        settleItem(item.taskId, status === "COMPLETED" ? "done" : "error");
      } else if (status === "NONE") {
        stopPolling();
        removeItem(item.taskId);
      } else if (status === "ACTIVE") {
        const latest = await fetchTaskStreamLastMessage(item.taskId).catch(() => "");
        if (!disposed && latest) {
          updateProgress(item.taskId, latest);
        }
      }
    };

    const startPolling = () => {
      if (disposed || pollTimer) return;
      void pollTick();
      pollTimer = setInterval(() => {
        void pollTick();
      }, POLL_INTERVAL_MS);
    };

    const probeAfterStreamEnd = async () => {
      const status = await probeTaskStreamStatus(item.taskId);
      if (disposed) return;
      if (status === "COMPLETED" || status === "ERROR") {
        settleItem(item.taskId, status === "COMPLETED" ? "done" : "error");
        return;
      }
      if (status === "NONE") {
        removeItem(item.taskId);
        return;
      }
      if (resubscribeCount < MAX_STREAM_RESUBSCRIBE) {
        resubscribeCount += 1;
        subscribe();
      } else {
        startPolling();
      }
    };

    const subscribe = () => {
      controller = reconnectTaskStream(item.taskId, {
        onEvent: (event) => {
          if (event.outputType === "CONTENT" && event.content) {
            updateProgress(item.taskId, event.content);
          } else if (event.outputType === "DONE") {
            settleItem(item.taskId, "done", event.content || undefined);
          } else if (event.outputType === "ERROR") {
            settleItem(item.taskId, "error", event.error || undefined);
          }
        },
        onError: () => {
          if (disposed) return;
          startPolling();
        },
        onComplete: () => {
          if (disposed) return;
          // 流结束但未携带终态事件：探测状态后决定重订或转轮询。
          void probeAfterStreamEnd();
        },
      });
    };

    subscribe();

    return () => {
      disposed = true;
      stopPolling();
      controller?.abort();
    };
  }, [isRunning, item.taskId, removeItem, settleItem, updateProgress]);

  return (
    <div
      className={cn(
        "rounded-xl border px-3 py-2.5",
        isRunning
          ? "border-blue-500/20 bg-blue-500/5"
          : item.status === "done"
            ? "border-green-500/20 bg-green-500/5"
            : "border-destructive/20 bg-destructive/5"
      )}
    >
      <div className="flex items-center gap-2">
        {isRunning ? (
          <Loader2 className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none text-blue-400 shrink-0" />
        ) : item.status === "done" ? (
          <CheckCircle2 className="h-3.5 w-3.5 text-green-400 shrink-0" />
        ) : (
          <XCircle className="h-3.5 w-3.5 text-destructive shrink-0" />
        )}
        <p className="text-xs font-medium truncate flex-1 min-w-0" title={item.title}>
          {item.title}
        </p>
        <ElapsedBadge item={item} />
      </div>

      {isRunning ? (
        <p
          className="mt-1 pl-[22px] text-[11px] text-muted-foreground truncate"
          title={item.progressText || undefined}
        >
          {item.progressText || "正在连接实时进度…"}
        </p>
      ) : (
        <TaskStreamSummary item={item} />
      )}
    </div>
  );
}
