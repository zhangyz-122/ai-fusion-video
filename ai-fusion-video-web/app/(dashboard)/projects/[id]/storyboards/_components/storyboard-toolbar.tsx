"use client";

import type { ReactNode } from "react";
import {
  Film,
  Loader2,
  Sparkles,
  Table2,
  LayoutGrid,
  Menu,
  Info,
  Clapperboard,
  PlayCircle,
  AlertCircle,
} from "lucide-react";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { StoryboardEpisode } from "@/lib/api/storyboard";
import type { ViewMode } from "./storyboard-utils";

interface StoryboardToolbarProps {
  projectName?: string;
  sceneCount: number;
  itemCount: number;
  /** AI 主操作文案:已有内容时为"AI 补全",否则为"AI 生成" */
  aiButtonLabel: string;
  onOpenAiDialog: () => void;
  // 左侧移动端目录
  leftSheetOpen: boolean;
  onLeftSheetOpenChange: (open: boolean) => void;
  mobileSidebar: ReactNode;
  // 本集合成状态
  currentEpisodeId: number | null;
  currentEpisode: StoryboardEpisode | null;
  submittingComposeEpisodeIds: number[];
  runningComposeEpisodeIds: number[];
  onComposeEpisode: () => void;
  onPreviewComposedVideo: (url: string) => void;
  // 视图切换
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
  // 右侧移动端引用面板
  rightSheetOpen: boolean;
  onRightSheetOpenChange: (open: boolean) => void;
  refPanel: ReactNode;
}

/** 分镜页中栏工具栏:目录入口、标题、AI 主操作、本集合成与视图切换 */
export function StoryboardToolbar({
  projectName,
  sceneCount,
  itemCount,
  aiButtonLabel,
  onOpenAiDialog,
  leftSheetOpen,
  onLeftSheetOpenChange,
  mobileSidebar,
  currentEpisodeId,
  currentEpisode,
  submittingComposeEpisodeIds,
  runningComposeEpisodeIds,
  onComposeEpisode,
  onPreviewComposedVideo,
  viewMode,
  onViewModeChange,
  rightSheetOpen,
  onRightSheetOpenChange,
  refPanel,
}: StoryboardToolbarProps) {
  return (
    <div className="px-4 md:px-5 py-3 border-b border-border/20 flex items-center justify-between shrink-0">
      <div className="flex items-center gap-2 max-w-[60%]">
        <Sheet open={leftSheetOpen} onOpenChange={onLeftSheetOpenChange}>
          <SheetTrigger
            render={
              <button
                className="xl:hidden flex h-11 w-11 -ml-2 items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors shrink-0"
                aria-label="打开分镜目录"
              >
                <Menu className="h-5 w-5" />
              </button>
            }
          />
          <SheetContent side="left" className="w-full sm:w-[300px] p-0 border-r-0 flex flex-col pt-12">
            {mobileSidebar}
          </SheetContent>
        </Sheet>
        <h2 className="text-base font-semibold flex items-center gap-2 overflow-hidden">
          <Film className="h-4 w-4 text-primary shrink-0" />
          <span className="truncate">{projectName || "未命名项目"}</span>
          <span className="hidden sm:inline text-xs text-muted-foreground font-normal ml-1 shrink-0">
            · {sceneCount} 场次 · {itemCount} 镜头
          </span>
        </h2>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="ai" size="sm" onClick={onOpenAiDialog}>
          <Sparkles className="h-4 w-4" />
          {aiButtonLabel}
        </Button>
        {/* 合成本集视频(窄屏压缩为图标按钮,标签由 title 承载) */}
        {currentEpisodeId && currentEpisode && (() => {
          const cs = currentEpisode.composeStatus;
          const isSubmitting = submittingComposeEpisodeIds.includes(currentEpisodeId);
          const isRunning = runningComposeEpisodeIds.includes(currentEpisodeId) || cs === 1;
          if (isSubmitting) {
            return (
              <button
                disabled
                className="flex items-center gap-1.5 px-2.5 sm:px-3 sm:py-1.5 min-h-11 sm:min-h-0 rounded-lg text-xs font-medium border border-border/30 bg-muted/20 text-muted-foreground shrink-0 cursor-not-allowed"
                title="正在提交合成任务"
              >
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                <span className="hidden sm:inline">提交中…</span>
              </button>
            );
          }
          if (isRunning) {
            return (
              <button
                disabled
                className="flex items-center gap-1.5 px-2.5 sm:px-3 sm:py-1.5 min-h-11 sm:min-h-0 rounded-lg text-xs font-medium border border-border/30 bg-muted/20 text-muted-foreground shrink-0 cursor-not-allowed"
                title="正在合成本集视频，预计 30s - 3min"
              >
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                <span className="hidden sm:inline">合成中…</span>
              </button>
            );
          }
          if (cs === 2 && currentEpisode.composedVideoUrl) {
            return (
              <div className="flex items-center gap-1 shrink-0">
                <button
                  onClick={() => onPreviewComposedVideo(currentEpisode.composedVideoUrl!)}
                  className="flex items-center gap-1.5 px-2.5 sm:px-3 sm:py-1.5 min-h-11 sm:min-h-0 rounded-lg text-xs font-medium border border-emerald-500/30 bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20 transition-colors"
                  title="查看本集合成视频"
                >
                  <PlayCircle className="h-3.5 w-3.5" />
                  <span className="hidden sm:inline">查看本集视频</span>
                </button>
                <button
                  onClick={onComposeEpisode}
                  className="flex h-11 w-11 sm:h-8 sm:w-8 items-center justify-center rounded-lg border border-border/30 bg-muted/20 text-muted-foreground hover:text-foreground hover:bg-muted/40 transition-colors"
                  title="重新合成"
                  aria-label="重新合成本集视频"
                >
                  <Clapperboard className="h-3.5 w-3.5" />
                </button>
              </div>
            );
          }
          if (cs === 3) {
            return (
              <button
                onClick={onComposeEpisode}
                className="flex items-center gap-1.5 px-2.5 sm:px-3 sm:py-1.5 min-h-11 sm:min-h-0 rounded-lg text-xs font-medium border border-amber-500/30 bg-amber-500/10 text-amber-600 hover:bg-amber-500/20 transition-colors shrink-0"
                title={`上次失败：${currentEpisode.composeErrorMsg || "未知错误"}\n点击重试`}
              >
                <AlertCircle className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">重试合成</span>
              </button>
            );
          }
          return (
            <button
              onClick={onComposeEpisode}
              className="flex items-center gap-1.5 px-2.5 sm:px-3 sm:py-1.5 min-h-11 sm:min-h-0 rounded-lg text-xs font-medium border border-primary/30 bg-primary/10 text-primary hover:bg-primary/20 transition-colors shrink-0"
              title="将本集所有镜头视频按顺序拼接成一个完整视频"
            >
              <Clapperboard className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">合成本集视频</span>
            </button>
          );
        })()}

        {/* 视图切换(窄屏强制卡片视图,隐藏切换) */}
        <div className="hidden sm:flex items-center rounded-lg border border-border/30 bg-muted/20 p-0.5 shrink-0">
          <button
            onClick={() => onViewModeChange("table")}
            className={cn(
              "flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-all",
              viewMode === "table"
                ? "bg-background shadow-sm text-foreground"
                : "text-muted-foreground hover:text-foreground"
            )}
            title="表格视图"
          >
            <Table2 className="h-3.5 w-3.5" />
            表格
          </button>
          <button
            onClick={() => onViewModeChange("card")}
            className={cn(
              "flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium transition-all",
              viewMode === "card"
                ? "bg-background shadow-sm text-foreground"
                : "text-muted-foreground hover:text-foreground"
            )}
            title="卡片视图"
          >
            <LayoutGrid className="h-3.5 w-3.5" />
            卡片
          </button>
        </div>
        {/* 右侧边栏触发器 */}
        <Sheet open={rightSheetOpen} onOpenChange={onRightSheetOpenChange}>
          <SheetTrigger
            render={
              <button
                className="2xl:hidden flex h-11 w-11 -mr-2 items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors shrink-0"
                aria-label="打开引用信息"
              >
                <Info className="h-5 w-5" />
              </button>
            }
          />
          <SheetContent side="right" className="w-full sm:w-[300px] p-0 border-l-0 flex flex-col pt-12 overflow-y-auto">
            {refPanel}
          </SheetContent>
        </Sheet>
      </div>
    </div>
  );
}
