"use client";

import { AlertCircle, Check, Loader2, RefreshCw, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import type { UploadTask } from "./use-asset-upload";
import { formatBytes } from "./utils";

interface UploadPanelProps {
  tasks: UploadTask[];
  onRetry: (id: string) => void;
  onDismiss: (id: string) => void;
}

/** 上传任务面板：展示进行中/成功/失败的文件，失败可重试（整文件重传） */
export function UploadPanel({ tasks, onRetry, onDismiss }: UploadPanelProps) {
  if (tasks.length === 0) return null;

  const failedCount = tasks.filter((t) => t.status === "failed").length;

  return (
    <div className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-3">
      <div className="flex items-center justify-between gap-3 mb-2">
        <p className="text-xs font-medium text-muted-foreground">
          上传任务（{tasks.length}）
        </p>
        {failedCount > 0 && (
          <p className="text-xs text-muted-foreground/70">
            失败后可重试，重试将重新上传整个文件
          </p>
        )}
      </div>
      <ul className="flex flex-col gap-1.5">
        {tasks.map((task) => (
          <li
            key={task.id}
            className="flex items-center gap-3 rounded-lg border border-border/20 bg-background/70 px-3 py-2"
          >
            {task.status === "uploading" && (
              <Loader2 className="h-4 w-4 animate-spin text-muted-foreground shrink-0" />
            )}
            {task.status === "success" && (
              <Check className="h-4 w-4 text-emerald-500 shrink-0" />
            )}
            {task.status === "failed" && (
              <AlertCircle className="h-4 w-4 text-destructive shrink-0" />
            )}

            <div className="min-w-0 flex-1">
              <p className="truncate text-sm">{task.name}</p>
              {task.status === "failed" && task.error && (
                <p className="truncate text-xs text-destructive/90">{task.error}</p>
              )}
            </div>

            <span
              className={cn(
                "text-xs whitespace-nowrap shrink-0",
                task.status === "failed" ? "text-destructive/80" : "text-muted-foreground"
              )}
            >
              {task.status === "uploading" && "上传中..."}
              {task.status === "success" && "已完成"}
              {task.status === "failed" && "失败"}
            </span>

            <span className="text-xs text-muted-foreground/60 whitespace-nowrap shrink-0">
              {formatBytes(task.size)}
            </span>

            {task.status === "failed" ? (
              <div className="flex items-center gap-1 shrink-0">
                <Button size="xs" variant="secondary" onClick={() => onRetry(task.id)}>
                  <RefreshCw data-icon="inline-start" />
                  重试
                </Button>
                <Button
                  size="icon-xs"
                  variant="ghost"
                  title="移除"
                  aria-label={`移除上传任务 ${task.name}`}
                  onClick={() => onDismiss(task.id)}
                >
                  <X />
                </Button>
              </div>
            ) : (
              <div className="w-[52px] shrink-0" aria-hidden="true" />
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
