"use client";

import { ArrowDown, ArrowUp, VideoOff } from "lucide-react";
import { resolveMediaUrl } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@/lib/utils";
import type { EditingShotVideo } from "@/lib/api/editing";
import type { EditingShotRow } from "./editing-types";

interface ShotCardProps {
  row: EditingShotRow;
  video: EditingShotVideo;
  /** 在启用镜头播放序列中的序号；已排除时为 null */
  orderNumber: number | null;
  /** 当前过滤视图内是否不能再移动 */
  moveUpDisabled: boolean;
  moveDownDisabled: boolean;
  onToggleEnabled: (itemId: number, enabled: boolean) => void;
  onMove: (itemId: number, direction: -1 | 1) => void;
}

export function ShotCard({
  row,
  video,
  orderNumber,
  moveUpDisabled,
  moveDownDisabled,
  onToggleEnabled,
  onMove,
}: ShotCardProps) {
  const item = row.item;
  const enabled = orderNumber != null;
  const shotLabel =
    item.shotNumber || item.autoShotNumber || String(item.id);
  const resolvedVideoUrl = resolveMediaUrl(video.videoUrl);
  const resolvedCoverUrl = resolveMediaUrl(video.coverUrl);

  return (
    <div
      id={`shot-${item.id}`}
      className={cn(
        "scroll-mt-4 rounded-lg border border-border/20 bg-background/70 p-3 transition-opacity motion-reduce:transition-none",
        !enabled && "opacity-55"
      )}
    >
      <div className="flex items-start gap-3">
        {/* 启用/排除 */}
        <div className="flex flex-col items-center gap-1 pt-1">
          <Checkbox
            checked={enabled}
            onCheckedChange={(checked) => onToggleEnabled(item.id, !!checked)}
            aria-label={enabled ? `排除镜头 ${shotLabel}` : `启用镜头 ${shotLabel}`}
          />
        </div>

        {/* 视频预览 */}
        <div className="relative aspect-video w-36 shrink-0 overflow-hidden rounded-md border border-border/20 bg-black sm:w-44">
          {resolvedVideoUrl ? (
            <video
              src={resolvedVideoUrl}
              poster={resolvedCoverUrl ?? undefined}
              controls
              preload="metadata"
              playsInline
              className="h-full w-full object-contain"
            />
          ) : (
            <div className="flex h-full w-full flex-col items-center justify-center gap-1 bg-muted/40 text-muted-foreground/60">
              <VideoOff className="h-5 w-5" />
              <span className="px-2 text-center text-[10px]">暂无可用视频</span>
            </div>
          )}
        </div>

        {/* 镜头信息 */}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            {enabled ? (
              <span className="rounded-md bg-primary/10 px-1.5 py-0.5 text-xs font-semibold text-primary">
                #{orderNumber}
              </span>
            ) : (
              <span className="rounded-md bg-muted/60 px-1.5 py-0.5 text-xs text-muted-foreground">
                已排除
              </span>
            )}
            <span className="text-sm font-medium text-foreground">
              镜头 {shotLabel}
            </span>
            <span className="truncate text-xs text-muted-foreground">
              {row.episodeLabel} · {row.sceneLabel}
            </span>
          </div>

          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            {item.shotType && (
              <span className="rounded-md bg-muted/50 px-1.5 py-0.5 text-[10px] text-muted-foreground">
                {item.shotType}
              </span>
            )}
            {item.duration != null && (
              <span className="rounded-md bg-muted/50 px-1.5 py-0.5 text-[10px] text-muted-foreground">
                {item.duration}s
              </span>
            )}
            <span
              className="rounded-md bg-muted/50 px-1.5 py-0.5 text-[10px] text-muted-foreground"
              title={
                video.source === "selectedTake"
                  ? "播放生产流程选中的候选视频"
                  : "播放镜头生成视频（未命中选中 Take）"
              }
            >
              {video.source === "selectedTake" ? "已选 Take" : "生成视频"}
            </span>
          </div>

          {item.content && (
            <p className="mt-1.5 line-clamp-2 text-xs text-muted-foreground">
              {item.content}
            </p>
          )}
          {item.dialogue && (
            <p className="mt-1 line-clamp-1 text-xs text-foreground/70">
              「{item.dialogue}」
            </p>
          )}
        </div>

        {/* 排序 */}
        <div className="flex shrink-0 flex-col gap-1">
          <Button
            variant="ghost"
            size="icon-xs"
            disabled={moveUpDisabled}
            onClick={() => onMove(item.id, -1)}
            aria-label={`上移镜头 ${shotLabel}`}
            title="上移"
          >
            <ArrowUp />
          </Button>
          <Button
            variant="ghost"
            size="icon-xs"
            disabled={moveDownDisabled}
            onClick={() => onMove(item.id, 1)}
            aria-label={`下移镜头 ${shotLabel}`}
            title="下移"
          >
            <ArrowDown />
          </Button>
        </div>
      </div>
    </div>
  );
}
