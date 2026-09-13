"use client";

import { Sparkles } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface StoryboardAiConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 已有分镜内容时为补全模式,否则为全量生成模式 */
  hasScenes: boolean;
  onStart: () => void;
}

/** AI 生成分镜前的确认弹窗 */
export function StoryboardAiConfirmDialog({
  open,
  onOpenChange,
  hasScenes,
  onStart,
}: StoryboardAiConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-base">
            <Sparkles className="h-4 w-4 text-violet-500" />
            {hasScenes ? "确认 AI 补全分镜？" : "确认 AI 生成分镜？"}
          </DialogTitle>
          <DialogDescription className="text-xs text-muted-foreground pt-1">
            {hasScenes
              ? "系统将基于当前剧本内容，智能分析并补全尚未生成分镜的场次与镜头。"
              : "系统将基于当前剧本内容，自动分析剧本并生成全套分镜结构与镜头信息。"}
          </DialogDescription>
        </DialogHeader>

        <div className="rounded-xl border border-border/30 bg-muted/20 p-3 text-xs text-muted-foreground space-y-1">
          <p className="font-medium text-foreground">💡 提示</p>
          <p>
            生成任务启动后将在后台自动运行，您可在右上角任务中心实时查看进度。
          </p>
        </div>

        <DialogFooter className="gap-2">
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            variant="ai"
            size="sm"
            onClick={onStart}
          >
            <Sparkles className="h-3.5 w-3.5" />
            {hasScenes ? "开始 AI 补全" : "开始 AI 生成"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
