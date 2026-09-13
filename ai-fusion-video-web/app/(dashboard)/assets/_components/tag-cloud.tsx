"use client";

import { cn } from "@/lib/utils";

export interface TagCloudItem {
  tag: string;
  count: number;
}

interface TagCloudProps {
  tags: TagCloudItem[];
  selectedTags: string[];
  onToggle: (tag: string) => void;
}

/** 标签云快捷筛选：按频次展示，点击切换选中（可多选，取交集） */
export function TagCloud({ tags, selectedTags, onToggle }: TagCloudProps) {
  if (tags.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-xs text-muted-foreground shrink-0">标签</span>
      {tags.map(({ tag, count }) => {
        const selected = selectedTags.includes(tag);
        return (
          <button
            key={tag}
            type="button"
            aria-pressed={selected}
            onClick={() => onToggle(tag)}
            className={cn(
              "inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs transition-colors",
              "focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
              selected
                ? "border-primary/40 bg-primary/10 text-primary font-medium"
                : "border-border/30 text-muted-foreground hover:border-border/50 hover:text-foreground"
            )}
          >
            {tag}
            <span
              className={cn(
                "text-[10px]",
                selected ? "text-primary/70" : "text-muted-foreground/50"
              )}
            >
              {count}
            </span>
          </button>
        );
      })}
    </div>
  );
}
