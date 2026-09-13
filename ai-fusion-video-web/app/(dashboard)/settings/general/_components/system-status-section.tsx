"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { Activity, Database, FolderTree, HardDrive, ListVideo, Loader2, RefreshCw, Server } from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { getApiErrorMessage } from "@/lib/api/api-error";
import { Button } from "@/components/ui/button";
import {
  formatBytes,
  getMediaStorageStats,
  getSystemHealth,
  getVideoQueueStatus,
  shortQueueName,
  type MediaStorageStats,
  type SystemHealth,
  type VideoQueueStatus,
} from "./system-status-api";
import { itemVariants, settingsTypography } from "../../_shared";

const POLL_INTERVAL_MS = 30_000;

type LoadState = "loading" | "ready" | "error";

interface StatusSlice<T> {
  state: LoadState;
  data: T | null;
  error: string | null;
}

function emptySlice<T>(): StatusSlice<T> {
  return { state: "loading", data: null, error: null };
}

function statusChip(status?: string) {
  if (status === "UP") {
    return { label: "正常", className: "border-emerald-500/30 bg-emerald-500/10 text-emerald-600" };
  }
  if (status === "DOWN") {
    return { label: "异常", className: "border-rose-500/30 bg-rose-500/10 text-rose-600" };
  }
  return { label: "未知", className: "border-border/30 bg-muted/20 text-muted-foreground" };
}

function Chip({ status }: { status?: string }) {
  const chip = statusChip(status);
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium border",
        chip.className,
      )}
    >
      {chip.label}
    </span>
  );
}

function ComponentRow({ label, status }: { label: string; status?: string }) {
  const up = status === "UP";
  const down = status === "DOWN";
  return (
    <div className="flex items-center justify-between">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="flex items-center gap-1.5">
        <span
          className={cn(
            "size-1.5 rounded-full",
            up && "bg-emerald-500",
            down && "bg-rose-500",
            !up && !down && "bg-muted-foreground/40",
          )}
        />
        <span className="text-xs font-medium">{statusChip(status).label}</span>
      </span>
    </div>
  );
}

function StatCard({
  icon,
  title,
  children,
}: {
  icon: ReactNode;
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="rounded-lg border border-border/20 bg-background/70 p-4">
      <div className="flex items-center gap-1.5 mb-3">
        {icon}
        <p className="text-xs font-medium text-foreground/80">{title}</p>
      </div>
      {children}
    </div>
  );
}

export function SystemStatusSection() {
  const [health, setHealth] = useState<StatusSlice<SystemHealth>>(emptySlice);
  const [storage, setStorage] = useState<StatusSlice<MediaStorageStats>>(emptySlice);
  const [queue, setQueue] = useState<StatusSlice<VideoQueueStatus>>(emptySlice);
  const [refreshing, setRefreshing] = useState(false);
  const inFlightRef = useRef(false);
  const mountedRef = useRef(true);

  const fetchAll = useCallback(async () => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    setRefreshing(true);
    const [healthResult, storageResult, queueResult] = await Promise.allSettled([
      getSystemHealth(),
      getMediaStorageStats(),
      getVideoQueueStatus(),
    ]);

    if (!mountedRef.current) return;
    const toSlice = <T,>(result: PromiseSettledResult<T>): StatusSlice<T> =>
      result.status === "fulfilled"
        ? { state: "ready", data: result.value, error: null }
        : { state: "error", data: null, error: getApiErrorMessage(result.reason) };
    setHealth(toSlice(healthResult));
    setStorage(toSlice(storageResult));
    setQueue(toSlice(queueResult));
    inFlightRef.current = false;
    setRefreshing(false);
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    // 延后一拍发起首次加载，避免在 effect 体内同步 setState
    const kickoff = window.setTimeout(() => void fetchAll(), 0);
    const timer = window.setInterval(() => {
      // 页面不可见时跳过轮询，回到前台后下一轮立即补齐
      if (!document.hidden) void fetchAll();
    }, POLL_INTERVAL_MS);
    return () => {
      mountedRef.current = false;
      window.clearTimeout(kickoff);
      window.clearInterval(timer);
    };
  }, [fetchAll]);

  const overallDown = health.state === "ready" && health.data?.status === "DOWN";

  return (
    <motion.div
      className="mt-6 rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-6"
      variants={itemVariants}
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Activity className="h-4 w-4 text-primary" />
            <h3 className={settingsTypography.sectionTitle}>系统状态</h3>
            <Chip status={health.data?.status} />
          </div>
          <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
            实时探测后端服务、数据库、媒体存储与视频队列，每 30 秒自动刷新。
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => void fetchAll()} disabled={refreshing}>
          {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
          刷新
        </Button>
      </div>

      <div className="mt-5 grid gap-4 lg:grid-cols-3">
        {/* 服务健康 */}
        <StatCard icon={<Server className="h-3.5 w-3.5 text-muted-foreground" />} title="服务健康">
          {health.state === "loading" ? (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> 检测中…
            </div>
          ) : health.state === "error" ? (
            <p className="text-xs text-rose-600" role="alert">{health.error}</p>
          ) : (
            <div className="space-y-2">
              <ComponentRow label="后端服务" status={health.data?.components.backend.status} />
              <ComponentRow label="数据库" status={health.data?.components.database.status} />
              <p className="pt-1 text-[11px] text-muted-foreground">
                检查时间：{health.data ? new Date(health.data.checkedAt).toLocaleString("zh-CN") : "--"}
              </p>
            </div>
          )}
        </StatCard>

        {/* 存储占用 */}
        <StatCard icon={<HardDrive className="h-3.5 w-3.5 text-muted-foreground" />} title="存储占用">
          {storage.state === "loading" ? (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> 统计中…
            </div>
          ) : storage.state === "error" ? (
            <p className="text-xs text-rose-600" role="alert">{storage.error}</p>
          ) : (
            <div className="space-y-2">
              <div className="flex items-baseline gap-2">
                <span className={settingsTypography.metric}>{formatBytes(storage.data?.totalBytes)}</span>
                <span className="text-xs text-muted-foreground">
                  {storage.data?.totalFiles ?? 0} 个文件
                </span>
              </div>
              {(
                [
                  ["图片 images", storage.data?.categories.images],
                  ["视频 videos", storage.data?.categories.videos],
                  ["合成 composed", storage.data?.categories.composed],
                ] as const
              ).map(([label, stat]) => (
                <div key={label} className="flex items-center justify-between">
                  <span className="text-xs text-muted-foreground">{label}</span>
                  <span className="text-xs font-medium tabular-nums">
                    {stat ? `${stat.fileCount} 个 · ${formatBytes(stat.totalBytes)}` : "--"}
                  </span>
                </div>
              ))}
              <p className="flex items-center gap-1 pt-1 text-[11px] text-muted-foreground" title={storage.data?.basePath}>
                <FolderTree className="h-3 w-3 shrink-0" />
                <span className="truncate">{storage.data?.basePath ?? "--"}</span>
              </p>
            </div>
          )}
        </StatCard>

        {/* 视频队列 */}
        <StatCard icon={<ListVideo className="h-3.5 w-3.5 text-muted-foreground" />} title="视频队列">
          {queue.state === "loading" ? (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> 读取中…
            </div>
          ) : queue.state === "error" ? (
            <p className="text-xs text-rose-600" role="alert">{queue.error}</p>
          ) : queue.data && !queue.data.available ? (
            <p className="text-xs text-amber-600" role="alert">{queue.data.error}</p>
          ) : (
            <div className="space-y-2">
              <div className="flex items-baseline gap-2">
                <span className={settingsTypography.metric}>{queue.data?.totalPending ?? 0}</span>
                <span className="text-xs text-muted-foreground">
                  待处理 · {queue.data?.totalRunning ?? 0} 执行中
                </span>
              </div>
              {(queue.data?.queues ?? []).length === 0 ? (
                <p className="text-xs text-muted-foreground">当前没有注册的视频队列。</p>
              ) : (
                (queue.data?.queues ?? []).map((q) => (
                  <div key={q.name} className="flex items-center justify-between">
                    <span className="truncate text-xs text-muted-foreground">{shortQueueName(q.name)}</span>
                    <span className="ml-2 shrink-0 text-xs font-medium tabular-nums">
                      {q.pending} 待处理 · {q.running}/{q.maxConcurrent}
                    </span>
                  </div>
                ))
              )}
              <p className="flex items-center gap-1 pt-1 text-[11px] text-muted-foreground">
                <Database className="h-3 w-3 shrink-0" />
                来源：Redis 队列（只读）
              </p>
            </div>
          )}
        </StatCard>
      </div>

      {overallDown ? (
        <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/5 p-3 text-xs text-rose-600">
          部分组件异常，请检查后端服务与数据库连接状态。
        </p>
      ) : null}
    </motion.div>
  );
}
