"use client";

import { Loader2, RotateCcw, Trash2, Info } from "lucide-react";
import { cn } from "@/lib/utils";
import { resolveMediaUrl } from "@/lib/api/client";
import { SafeImage } from "@/components/ui/safe-image";
import { Button } from "@/components/ui/button";
import { useConfirm } from "@/components/ui/confirm-dialog";
import type { DeletedAssetRecord } from "./recycle-bin-store";
import { getTypeMeta } from "./constants";
import { formatDate } from "./utils";

interface RecycleBinViewProps {
  records: DeletedAssetRecord[];
  /** 正在恢复中的记录 id */
  restoringId: number | null;
  onRestore: (record: DeletedAssetRecord) => void;
  onPurge: (record: DeletedAssetRecord) => void;
}

/** 回收站视图：展示从本页删除的资产，支持恢复与彻底删除 */
export function RecycleBinView({
  records,
  restoringId,
  onRestore,
  onPurge,
}: RecycleBinViewProps) {
  const { confirm } = useConfirm();

  const handlePurge = async (record: DeletedAssetRecord) => {
    const ok = await confirm({
      title: "彻底删除",
      description: `「${record.snapshot.name}」将从回收站中移除，之后无法再恢复。`,
      confirmText: "彻底删除",
      variant: "destructive",
    });
    if (ok) onPurge(record);
  };

  if (records.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <div className="h-16 w-16 rounded-2xl bg-muted/40 flex items-center justify-center mb-4">
          <Trash2 className="h-8 w-8 text-muted-foreground/40" />
        </div>
        <h3 className="text-lg font-semibold mb-1">回收站为空</h3>
        <p className="text-sm text-muted-foreground max-w-sm">
          在上方素材列表中删除的资产会先进入回收站，可随时恢复
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Info className="h-3.5 w-3.5 shrink-0" />
        恢复会以新资产的形式重新创建；彻底删除后无法再恢复
      </p>

      <div className="flex flex-col gap-2">
        {records.map((record) => {
          const meta = getTypeMeta(record.snapshot.type);
          const TypeIcon = meta.icon;
          const coverSrc = resolveMediaUrl(record.snapshot.coverUrl);
          const restoring = restoringId === record.assetId;

          return (
            <div
              key={record.assetId}
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
                  alt={record.snapshot.name}
                  className="w-full h-full object-cover"
                />
              </div>

              {/* 信息 */}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{record.snapshot.name}</p>
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
                    删除于 {formatDate(record.deletedAt)}
                  </span>
                </div>
              </div>

              {/* 操作 */}
              <div className="flex items-center gap-2 shrink-0">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={restoring}
                  onClick={() => onRestore(record)}
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
                  aria-label={`彻底删除 ${record.snapshot.name}`}
                  disabled={restoring}
                  onClick={() => void handlePurge(record)}
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
