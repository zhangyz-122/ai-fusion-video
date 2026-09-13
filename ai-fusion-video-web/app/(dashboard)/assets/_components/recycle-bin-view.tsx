"use client";

import { Loader2, RotateCcw, Trash2, Info } from "lucide-react";
import { cn } from "@/lib/utils";
import { resolveMediaUrl } from "@/lib/api/client";
import { SafeImage } from "@/components/ui/safe-image";
import { Button } from "@/components/ui/button";
import { useConfirm } from "@/components/ui/confirm-dialog";
import type { Asset } from "@/lib/api/asset";
import { getTypeMeta } from "./constants";
import { formatDate } from "./utils";

interface RecycleBinViewProps {
  records: Asset[];
  loading: boolean;
  /** 正在恢复中的资产 id */
  restoringId: number | null;
  onRestore: (asset: Asset) => void;
  onPurge: (asset: Asset) => void;
  onRetry: () => void;
}

/**
 * 回收站视图：展示服务端已逻辑删除（deleted = 1）的资产，
 * 支持恢复（置 deleted = 0，保留原 id）与彻底删除（物理删除）。
 * 删除时间取 update_time（软删 UPDATE 会经 ON UPDATE CURRENT_TIMESTAMP 刷新）。
 */
export function RecycleBinView({
  records,
  loading,
  restoringId,
  onRestore,
  onPurge,
  onRetry,
}: RecycleBinViewProps) {
  const { confirm } = useConfirm();

  const handlePurge = async (asset: Asset) => {
    const ok = await confirm({
      title: "彻底删除",
      description: `「${asset.name}」将从数据库中物理删除，之后无法再恢复。`,
      confirmText: "彻底删除",
      variant: "destructive",
    });
    if (ok) onPurge(asset);
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
        <Loader2 className="h-8 w-8 animate-spin mb-3 text-muted-foreground/40" />
        <p className="text-sm">加载回收站中...</p>
      </div>
    );
  }

  if (records.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="h-16 w-16 rounded-2xl bg-muted/40 flex items-center justify-center mb-4">
          <Trash2 className="h-8 w-8 text-muted-foreground/40" />
        </div>
        <h3 className="text-lg font-semibold mb-1">回收站为空</h3>
        <p className="text-sm text-muted-foreground max-w-sm">
          删除的资产会先进入回收站，可随时恢复或彻底删除
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <Info className="h-3.5 w-3.5 shrink-0" />
          恢复后资产回到素材列表（保留原 id 与子资产）；彻底删除将物理删除，无法恢复
        </p>
        <Button variant="ghost" size="icon-sm" title="刷新回收站" aria-label="刷新回收站" onClick={onRetry}>
          <RotateCcw className="h-3.5 w-3.5" />
        </Button>
      </div>

      <div className="flex flex-col gap-2">
        {records.map((asset) => {
          const meta = getTypeMeta(asset.type);
          const TypeIcon = meta.icon;
          const coverSrc = resolveMediaUrl(asset.coverUrl);
          const restoring = restoringId === asset.id;

          return (
            <div
              key={asset.id}
              className={cn(
                "flex items-center gap-4 px-4 py-3 rounded-xl",
                "border border-border/30 bg-card/50"
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
                    删除于 {formatDate(asset.updateTime)}
                  </span>
                </div>
              </div>

              {/* 操作 */}
              <div className="flex items-center gap-2 shrink-0">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={restoring}
                  onClick={() => onRestore(asset)}
                >
                  {restoring ? (
                    <Loader2 data-icon="inline-start" className="animate-spin" />
                  ) : (
                    <RotateCcw data-icon="inline-start" />
                  )}
                  恢复
                </Button>
                <Button
                  variant="destructive-ghost"
                  size="icon-sm"
                  title="彻底删除"
                  aria-label={`彻底删除 ${asset.name}`}
                  disabled={restoring}
                  onClick={() => void handlePurge(asset)}
                >
                  <Trash2 />
                </Button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
