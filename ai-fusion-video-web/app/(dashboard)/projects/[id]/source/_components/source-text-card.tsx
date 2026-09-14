"use client";

import { FileText, Clock, Upload, PenLine } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/** 预览最多展示的字符数 */
const PREVIEW_LIMIT = 2000;

function formatTime(iso: string) {
  const d = new Date(iso);
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "刚刚";
  if (mins < 60) return `${mins} 分钟前`;
  if (mins < 60 * 24) return `${Math.floor(mins / 60)} 小时前`;
  if (mins < 60 * 24 * 30) return `${Math.floor(mins / (60 * 24))} 天前`;
  return d.toLocaleDateString("zh-CN");
}

/**
 * 原文卡片：只展示与管理原文本身（导入、编辑、预览），
 * 不承担任何解析职责。
 */
export function SourceTextCard({
  rawContent,
  updateTime,
  onEdit,
}: {
  rawContent: string | null;
  updateTime?: string;
  onEdit: () => void;
}) {
  const text = rawContent?.trim() ?? "";

  if (!text) {
    return (
      <button
        type="button"
        onClick={onEdit}
        className={cn(
          "w-full rounded-xl border-2 border-dashed border-border/40 p-10 text-center cursor-pointer",
          "flex flex-col items-center justify-center bg-card/20",
          "hover:border-primary/40 hover:bg-primary/2 transition-colors",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
        )}
      >
        <div className="h-14 w-14 rounded-xl bg-primary/10 flex items-center justify-center mb-4">
          <Upload className="h-7 w-7 text-primary" />
        </div>
        <p className="text-lg font-medium mb-1">导入小说 / 故事原文</p>
        <p className="text-muted-foreground text-sm">
          点击上传 TXT、DOCX、PDF 等文件，或直接粘贴文本；这里只保存原文
        </p>
      </button>
    );
  }

  return (
    <div className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-5 space-y-4">
      <div className="flex items-start justify-between gap-4 max-lg:flex-wrap">
        <div className="flex items-center gap-3 min-w-0">
          <div className="h-10 w-10 rounded-xl bg-primary/10 flex items-center justify-center shrink-0">
            <FileText className="h-5 w-5 text-primary" />
          </div>
          <div className="min-w-0">
            <h3 className="font-semibold">已导入原文</h3>
            <div className="flex items-center gap-3 mt-0.5 text-xs text-muted-foreground">
              <span>共 {text.length.toLocaleString()} 字</span>
              {updateTime && (
                <span className="flex items-center gap-1">
                  <Clock className="h-3 w-3" />
                  {formatTime(updateTime)}
                </span>
              )}
            </div>
          </div>
        </div>
        <Button variant="outline" size="sm" className="max-lg:ml-auto" onClick={onEdit}>
          <PenLine data-icon="inline-start" />
          编辑 / 替换
        </Button>
      </div>

      <div className="rounded-lg border border-border/20 bg-background/70 p-3">
        <p className="text-xs leading-relaxed text-muted-foreground whitespace-pre-wrap break-words line-clamp-10">
          {text.slice(0, PREVIEW_LIMIT)}
        </p>
        {text.length > PREVIEW_LIMIT && (
          <p className="mt-2 text-[11px] text-muted-foreground/70">
            仅展示前 {PREVIEW_LIMIT.toLocaleString()} 字，转换时 AI 会读取完整原文。
          </p>
        )}
      </div>
    </div>
  );
}
