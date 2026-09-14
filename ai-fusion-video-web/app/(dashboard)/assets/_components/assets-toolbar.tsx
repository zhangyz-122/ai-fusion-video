"use client";

import { useRef } from "react";
import { Search, Grid3X3, List, ImagePlus } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { touchHitArea } from "@/components/dashboard/mobile-touch-area";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { UPLOAD_ACCEPT } from "./constants";

export type AssetsViewMode = "grid" | "list";

interface AssetsToolbarProps {
  projectOptions: { value: string; label: string }[];
  selectedProjectId: string;
  onSelectProject: (value: string) => void;
  keyword: string;
  onKeywordChange: (value: string) => void;
  viewMode: AssetsViewMode;
  onViewModeChange: (mode: AssetsViewMode) => void;
  onUploadFiles: (files: FileList) => void;
}

/** 筛选工具栏：项目选择、搜索、上传入口、视图切换 */
export function AssetsToolbar({
  projectOptions,
  selectedProjectId,
  onSelectProject,
  keyword,
  onKeywordChange,
  viewMode,
  onViewModeChange,
  onUploadFiles,
}: AssetsToolbarProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  return (
    <div className="flex items-center gap-3 flex-wrap">
      {/* 项目选择器(窄屏独占一行) */}
      <div className="w-full sm:w-44 sm:shrink-0">
        <Select
          value={selectedProjectId}
          onValueChange={(v) => onSelectProject(v ?? "all")}
          items={projectOptions}
        >
          <SelectTrigger className="w-full text-xs">
            <SelectValue placeholder="全部项目" />
          </SelectTrigger>
          <SelectContent className="text-xs">
            <SelectGroup>
              {projectOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value} className="text-xs">
                  {opt.label}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>

      {/* 搜索框（匹配名称或标签） */}
      <div
        className={cn(
          "flex-1 min-w-0 sm:min-w-[200px] flex items-center gap-2.5 px-3.5 py-2.5 sm:py-2 rounded-xl",
          "border border-border/30 bg-card/50 backdrop-blur-sm",
          "transition-[border-color,box-shadow] duration-150 focus-within:border-ring focus-within:ring-[3px] focus-within:ring-ring/50 motion-reduce:transition-none"
        )}
      >
        <Search className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
        <input
          type="text"
          placeholder="搜索名称或标签..."
          value={keyword}
          onChange={(e) => onKeywordChange(e.target.value)}
          className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground/50"
        />
      </div>

      {/* 上传 + 视图切换 */}
      <div className="flex items-center gap-2 shrink-0">
        <input
          ref={fileInputRef}
          type="file"
          accept={UPLOAD_ACCEPT}
          multiple
          className="hidden"
          onChange={(e) => {
            if (e.target.files?.length) onUploadFiles(e.target.files);
            e.target.value = "";
          }}
        />
        <Button size="sm" onClick={() => fileInputRef.current?.click()}>
          <ImagePlus data-icon="inline-start" />
          上传图片
        </Button>

        <div
          className="flex items-center gap-1 rounded-xl border border-border/30 bg-card/50 p-1"
          role="group"
          aria-label="视图切换"
        >
          <button
            type="button"
            title="网格视图"
            aria-label="网格视图"
            aria-pressed={viewMode === "grid"}
            onClick={() => onViewModeChange("grid")}
            className={cn(
              "rounded-lg p-1.5 transition-colors",
              "focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
              touchHitArea.size28,
              viewMode === "grid"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            <Grid3X3 className="h-4 w-4" />
          </button>
          <button
            type="button"
            title="列表视图"
            aria-label="列表视图"
            aria-pressed={viewMode === "list"}
            onClick={() => onViewModeChange("list")}
            className={cn(
              "rounded-lg p-1.5 transition-colors",
              "focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
              touchHitArea.size28,
              viewMode === "list"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            <List className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
