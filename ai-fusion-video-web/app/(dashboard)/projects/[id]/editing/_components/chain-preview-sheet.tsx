"use client";

import { useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  ListVideo,
  VideoOff,
} from "lucide-react";
import { resolveMediaUrl } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent } from "@/components/ui/sheet";
import type { EditingShotRow } from "./editing-types";

export interface ChainPreviewShot {
  row: EditingShotRow;
  videoUrl: string | null;
}

interface ChainPreviewSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 已启用镜头，按当前播放顺序排列 */
  shots: ChainPreviewShot[];
}

/**
 * 串联预览抽屉：按当前顺序依次自动播放各镜头视频。
 */
export function ChainPreviewSheet({
  open,
  onOpenChange,
  shots,
}: ChainPreviewSheetProps) {
  const [index, setIndex] = useState(0);
  const [lastOpen, setLastOpen] = useState(open);

  // 打开抽屉时回到第一个镜头（渲染期调整状态，避免多余的 effect）
  if (open !== lastOpen) {
    setLastOpen(open);
    if (open) {
      setIndex(0);
    } else {
      setIndex((prev) => Math.min(prev, Math.max(shots.length - 1, 0)));
    }
  }

  const current = shots[index] ?? null;
  const currentShotLabel = current
    ? current.row.item.shotNumber ||
      current.row.item.autoShotNumber ||
      String(current.row.item.id)
    : "";
  const resolvedUrl = current ? resolveMediaUrl(current.videoUrl) : null;
  const hasPrev = index > 0;
  const hasNext = index < shots.length - 1;

  const goPrev = () => {
    if (hasPrev) setIndex((prev) => prev - 1);
  };
  const goNext = () => {
    if (hasNext) setIndex((prev) => prev + 1);
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        className="max-h-[calc(100vh-3rem)] overflow-hidden"
      >
        <div className="mx-auto flex h-full w-full max-w-4xl flex-col gap-3 px-4 pt-12 pb-4 md:px-5 md:pb-5">
          {/* 顶部信息 */}
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="flex items-center gap-2 text-sm font-semibold">
              <ListVideo className="h-4 w-4 text-primary" />
              串联预览
            </h3>
            {current && (
              <span className="text-xs text-muted-foreground">
                第 {index + 1} / {shots.length} 镜 · 镜头 {currentShotLabel} ·{" "}
                {current.row.episodeLabel} · {current.row.sceneLabel}
              </span>
            )}
            <div className="ml-auto flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={goPrev}
                disabled={!hasPrev}
              >
                <ChevronLeft />
                上一镜
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={goNext}
                disabled={!hasNext}
              >
                下一镜
                <ChevronRight />
              </Button>
            </div>
          </div>

          {/* 播放区 */}
          <div className="flex min-h-0 flex-1 items-center justify-center">
            {current && resolvedUrl ? (
              <video
                key={current.row.item.id}
                src={resolvedUrl}
                controls
                autoPlay
                playsInline
                onEnded={goNext}
                className="max-h-[calc(100vh-14rem)] w-full rounded-xl border border-border/30 bg-black"
              />
            ) : current ? (
              <div className="flex w-full flex-col items-center justify-center gap-2 rounded-xl border border-border/20 bg-background/70 py-16 text-muted-foreground">
                <VideoOff className="h-8 w-8 text-muted-foreground/40" />
                <p className="text-sm">该镜头暂无可用视频，已自动跳过播放</p>
                {hasNext && (
                  <Button variant="outline" size="sm" onClick={goNext}>
                    下一镜
                    <ChevronRight />
                  </Button>
                )}
              </div>
            ) : null}
          </div>

          <p className="text-center text-[10px] text-muted-foreground/70">
            播放结束后自动接续下一镜；已排除镜头不参与串联预览
          </p>
        </div>
      </SheetContent>
    </Sheet>
  );
}
