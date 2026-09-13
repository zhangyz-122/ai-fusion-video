"use client";

import { useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Loader2,
  RotateCcw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { resolveMediaUrl } from "@/lib/api/client";
import type { ProductionRepairAttempt, ProductionTake } from "@/lib/api/production";
import { cn } from "@/lib/utils";
import {
  qcLabels,
  qcStatusStyles,
  repairRouteLabels,
  repairStatusLabels,
} from "./production-status";

const FAILURE_LONG_THRESHOLD = 120;

export function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3 text-xs">
      <span className="shrink-0 text-muted-foreground">{label}</span>
      <span className="truncate text-right font-medium">{value}</span>
    </div>
  );
}

/** 失败原因完整展示：长文本默认收起，可展开阅读全部内容 */
export function FailureReasonPanel({
  code,
  message,
  stepMessage,
}: {
  code: string | null;
  message: string | null;
  stepMessage: string | null;
}) {
  const [expanded, setExpanded] = useState(false);
  if (!code && !message && !stepMessage) return null;

  const isLong = !!message && (message.length > FAILURE_LONG_THRESHOLD || message.includes("\n"));

  return (
    <div className="rounded-xl border border-rose-500/20 bg-rose-500/5 px-4 py-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2 text-xs font-medium text-rose-700 dark:text-rose-300">
          <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
          <span className="shrink-0">失败原因</span>
          {code && (
            <span className="truncate rounded-full border border-rose-500/30 bg-rose-500/10 px-2 py-0.5 font-mono text-[10px]">
              {code}
            </span>
          )}
        </div>
        {isLong && (
          <button
            type="button"
            onClick={() => setExpanded(v => !v)}
            className="flex shrink-0 items-center gap-1 rounded-md text-xs text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
          >
            {expanded ? (
              <>
                <ChevronUp className="h-3 w-3" /> 收起
              </>
            ) : (
              <>
                <ChevronDown className="h-3 w-3" /> 展开全部
              </>
            )}
          </button>
        )}
      </div>
      {message && (
        <p
          className={cn(
            "mt-2 whitespace-pre-wrap break-all text-xs leading-relaxed text-rose-700 dark:text-rose-300",
            !expanded && isLong && "line-clamp-3"
          )}
        >
          {message}
        </p>
      )}
      {stepMessage && stepMessage !== message && (
        <p className="mt-1.5 break-all text-[11px] leading-relaxed text-muted-foreground">
          生成步骤：{stepMessage}
        </p>
      )}
    </div>
  );
}

export function RepairAttemptPanel({
  attempt,
  historyCount,
  canRepair,
  repairing,
  onRepair,
}: {
  attempt: ProductionRepairAttempt;
  historyCount: number;
  canRepair: boolean;
  repairing: boolean;
  onRepair: () => void;
}) {
  return (
    <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 px-4 py-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2 text-xs font-medium text-amber-700 dark:text-amber-300">
          <RotateCcw className="h-3.5 w-3.5 shrink-0" />
          <span className="shrink-0">修复计划 #{attempt.attemptNo}</span>
          <span className="truncate rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[10px]">
            {repairStatusLabels[attempt.status] ?? attempt.status}
          </span>
        </div>
        {canRepair && (
          <Button variant="outline" size="xs" onClick={onRepair} disabled={repairing}>
            {repairing ? <Loader2 className="animate-spin" /> : <RotateCcw />} 重试
          </Button>
        )}
      </div>
      <p className="mt-2 text-[11px] text-muted-foreground">
        路线：{repairRouteLabels[attempt.route] ?? attempt.route}
      </p>
      {attempt.reason && (
        <p className="mt-1 whitespace-pre-wrap break-all text-[11px] leading-relaxed text-muted-foreground">
          {attempt.reason}
        </p>
      )}
      {historyCount > 1 && (
        <p className="mt-1 text-[11px] text-muted-foreground">共记录 {historyCount} 次修复尝试</p>
      )}
    </div>
  );
}

export function TakeCard({ take, selected }: { take: ProductionTake; selected: boolean }) {
  return (
    <article
      className={cn(
        "space-y-3 rounded-xl border p-3",
        selected ? "border-violet-500/50 bg-violet-500/5" : "border-border/30"
      )}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2 text-sm font-medium">
          {selected && <CheckCircle2 className="h-4 w-4 text-violet-500" />}
          Take {take.takeIndex}
        </div>
        <span className={`rounded-full border px-2 py-0.5 text-[10px] ${qcStatusStyles[take.qcStatus]}`}>
          {qcLabels[take.qcStatus]}
        </span>
      </div>
      {take.videoUrl ? (
        <video
          controls
          preload="metadata"
          src={resolveMediaUrl(take.videoUrl) ?? undefined}
          poster={resolveMediaUrl(take.coverUrl) ?? undefined}
          className="h-40 w-full rounded-lg border border-border/20 bg-background/70"
        />
      ) : (
        <div className="flex h-14 items-center rounded-lg border border-border/20 bg-muted/30 px-3 text-xs text-muted-foreground">
          {take.videoErrorMsg ? "该候选生成失败" : "视频尚未生成"}
        </div>
      )}
      {take.videoErrorMsg && (
        <p className="whitespace-pre-wrap break-all text-[11px] leading-relaxed text-rose-600 dark:text-rose-300">
          {take.videoErrorMsg}
        </p>
      )}
      {take.qcNote && (
        <p className="break-all text-[11px] leading-relaxed text-muted-foreground">{take.qcNote}</p>
      )}
    </article>
  );
}
