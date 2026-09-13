"use client";

import { ExternalLink, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Asset } from "@/lib/api/asset";
import { resolveMediaUrl } from "@/lib/api/client";
import { SafeImage } from "@/components/ui/safe-image";
import { Button } from "@/components/ui/button";
import { getTypeMeta } from "./constants";
import { formatDate } from "./utils";

interface AssetListViewProps {
  assets: Asset[];
  projectMap: Record<number, string>;
  onOpen: (asset: Asset) => void;
  onDelete: (asset: Asset) => void;
}

/** 列表视图 */
export function AssetListView({ assets, projectMap, onOpen, onDelete }: AssetListViewProps) {
  return (
    <div className="flex flex-col gap-2">
      {assets.map((asset) => {
        const meta = getTypeMeta(asset.type);
        const TypeIcon = meta.icon;
        const coverSrc = resolveMediaUrl(asset.coverUrl);

        return (
          <div
            key={asset.id}
            onClick={() => onOpen(asset)}
            className={cn(
              "group flex items-center gap-4 px-4 py-3 rounded-xl cursor-pointer",
              "border border-border/30 bg-card/50",
              "hover:border-border/50 hover:bg-card/80 transition-colors"
            )}
          >
            {/* 缩略图 */}
            <div className="h-12 w-12 rounded-lg overflow-hidden bg-muted/10 shrink-0">
              <SafeImage
                src={coverSrc}
                fallbackType={meta.fallback}
                alt={asset.name}
                className="w-full h-full object-cover"
              />
            </div>

            {/* 信息 */}
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate">{asset.name}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <span
                  className={cn(
                    "px-1.5 py-0.5 rounded-md text-[10px] font-medium",
                    meta.bg,
                    meta.color
                  )}
                >
                  <TypeIcon className="h-2.5 w-2.5 inline-block align-[-1px] mr-0.5" />
                  {meta.label}
                </span>
                <span className="text-xs text-muted-foreground truncate">
                  {projectMap[asset.projectId] || `项目 ${asset.projectId}`}
                </span>
              </div>
            </div>

            {/* 时间 */}
            <span className="text-xs text-muted-foreground whitespace-nowrap shrink-0">
              {formatDate(asset.updateTime)}
            </span>

            {/* 移入回收站 */}
            <Button
              variant="destructive-ghost"
              size="icon-sm"
              title="移入回收站"
              aria-label={`将 ${asset.name} 移入回收站`}
              onClick={(e) => {
                e.stopPropagation();
                onDelete(asset);
              }}
            >
              <Trash2 />
            </Button>

            {/* 跳转指示 */}
            <ExternalLink className="h-3.5 w-3.5 text-muted-foreground/30 group-hover:text-muted-foreground/70 transition-colors shrink-0" />
          </div>
        );
      })}
    </div>
  );
}
