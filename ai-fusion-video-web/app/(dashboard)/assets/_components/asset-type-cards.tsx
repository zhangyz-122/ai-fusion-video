"use client";

import { Package } from "lucide-react";
import { cn } from "@/lib/utils";
import { ASSET_TYPE_ORDER, getTypeMeta } from "./constants";

interface AssetTypeCardsProps {
  totalCount: number;
  typeCounts: Record<string, number>;
  selectedType: string;
  onSelectType: (type: string) => void;
}

/** 顶部统计卡片：全部 + 各类型，点击卡片切换类型筛选 */
export function AssetTypeCards({
  totalCount,
  typeCounts,
  selectedType,
  onSelectType,
}: AssetTypeCardsProps) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
      {/* 全部 */}
      <div
        className={cn(
          "col-span-2 sm:col-span-1 rounded-xl border border-border/30 p-4",
          "bg-card/50 backdrop-blur-sm"
        )}
      >
        <div className="flex items-center gap-2.5">
          <div className="h-9 w-9 rounded-lg bg-foreground/5 flex items-center justify-center shrink-0">
            <Package className="h-4.5 w-4.5 text-foreground/60" />
          </div>
          <div>
            <p className="text-xl font-bold">{totalCount}</p>
            <p className="text-[10px] text-muted-foreground">全部资产</p>
          </div>
        </div>
      </div>
      {/* 各类型 */}
      {ASSET_TYPE_ORDER.map((type) => {
        const meta = getTypeMeta(type);
        const Icon = meta.icon;
        const count = typeCounts[type] || 0;
        return (
          <button
            key={type}
            type="button"
            aria-pressed={selectedType === type}
            onClick={() => onSelectType(selectedType === type ? "all" : type)}
            className={cn(
              "rounded-xl border p-3.5 text-left transition-colors",
              "bg-card/50 backdrop-blur-sm",
              "focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
              selectedType === type
                ? "border-border/60 ring-1 ring-border/30"
                : "border-border/20 hover:border-border/40"
            )}
          >
            <div className="flex items-center gap-2">
              <div className={cn("h-7 w-7 rounded-lg flex items-center justify-center shrink-0", meta.bg)}>
                <Icon className={cn("h-3.5 w-3.5", meta.color)} />
              </div>
              <div className="min-w-0">
                <p className="text-lg font-bold leading-none">{count}</p>
                <p className="text-[10px] text-muted-foreground truncate">{meta.label}</p>
              </div>
            </div>
          </button>
        );
      })}
    </div>
  );
}
