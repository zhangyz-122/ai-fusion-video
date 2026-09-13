"use client";

import { ExternalLink, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Asset } from "@/lib/api/asset";
import { resolveMediaUrl } from "@/lib/api/client";
import { SafeImage } from "@/components/ui/safe-image";
import { getTypeMeta } from "./constants";
import { formatDate } from "./utils";

interface AssetGridProps {
  assets: Asset[];
  projectMap: Record<number, string>;
  onOpen: (asset: Asset) => void;
  onDelete: (asset: Asset) => void;
}

/** 网格视图 */
export function AssetGrid({ assets, projectMap, onOpen, onDelete }: AssetGridProps) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
      {assets.map((asset) => {
        const meta = getTypeMeta(asset.type);
        const TypeIcon = meta.icon;
        const coverSrc = resolveMediaUrl(asset.coverUrl);

        return (
          <div
            key={asset.id}
            onClick={() => onOpen(asset)}
            className={cn(
              "group relative rounded-xl border border-border/30 overflow-hidden cursor-pointer",
              "bg-card/50 backdrop-blur-sm",
              "hover:border-border/50 hover:shadow-md hover:shadow-black/5",
              "transition-[border-color,box-shadow,transform] duration-200 hover:-translate-y-1 motion-reduce:transition-none motion-reduce:hover:translate-y-0"
            )}
          >
            {/* 封面 / 占位 */}
            <div className="aspect-[4/3] relative overflow-hidden bg-muted/5">
              <SafeImage
                src={coverSrc}
                fallbackType={meta.fallback}
                alt={asset.name}
                className="w-full h-full object-cover"
              />

              {/* 类型标签 */}
              <div
                className={cn(
                  "absolute top-2 left-2 flex items-center gap-1 px-2 py-0.5 rounded-lg",
                  "bg-black/40 backdrop-blur-sm text-white/90 text-[10px] font-medium"
                )}
              >
                <TypeIcon className="h-3 w-3" />
                {meta.label}
              </div>

              {/* 删除（移入回收站）：触摸端常显,桌面保持 hover 显隐 */}
              <button
                type="button"
                title="移入回收站"
                aria-label={`将 ${asset.name} 移入回收站`}
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(asset);
                }}
                className={cn(
                  "absolute top-2 right-2 rounded-lg bg-black/40 p-2.5 lg:p-1.5 text-white/80 backdrop-blur-sm",
                  "opacity-100 lg:opacity-0 lg:group-hover:opacity-100 transition-opacity duration-150 hover:text-white",
                  "focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
                )}
              >
                <Trash2 className="h-4 w-4 lg:h-3.5 lg:w-3.5" />
              </button>

              {/* 悬浮跳转提示 */}
              <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center">
                <div className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-white/20 backdrop-blur text-white text-xs font-medium opacity-0 group-hover:opacity-100 transition-opacity">
                  <ExternalLink className="h-3 w-3" />
                  查看详情
                </div>
              </div>
            </div>

            {/* 信息 */}
            <div className="p-3.5">
              <p className="text-sm font-medium truncate mb-1">{asset.name}</p>
              <div className="flex items-center justify-between gap-2">
                <span className="text-[10px] text-muted-foreground truncate">
                  {projectMap[asset.projectId] || `项目 ${asset.projectId}`}
                </span>
                <span className="text-[10px] text-muted-foreground/60 whitespace-nowrap">
                  {formatDate(asset.updateTime)}
                </span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
