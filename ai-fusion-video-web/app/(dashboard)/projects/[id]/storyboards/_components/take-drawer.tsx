'use client';

import { useState } from 'react';
import { Check, Loader2, ShieldCheck } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useConfirm } from '@/components/ui/confirm-dialog';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { resolveMediaUrl } from '@/lib/api/client';
import { parseTakeMetadata, type ProductionTake, type ProductionTakeQcStatus } from '@/lib/api/production';
import { cn } from '@/lib/utils';

interface TakeDrawerProps {
  itemId: number;
  takes: ProductionTake[];
  selectedTakeId: number | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (takeId: number) => Promise<void>;
  onEvaluateQc: (takeId: number) => Promise<void>;
}

const QC_BADGE_CLASS: Record<ProductionTakeQcStatus, string> = {
  PASS: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  FAIL: 'border-destructive/30 bg-destructive/10 text-destructive',
  REVIEW_REQUIRED: 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400',
  PENDING: 'border-border/30 bg-muted/40 text-muted-foreground',
};

const QC_LABEL: Record<ProductionTakeQcStatus, string> = {
  PASS: '质检通过',
  FAIL: '质检失败',
  REVIEW_REQUIRED: '待复核',
  PENDING: '未质检',
};

export function TakeDrawer({
  itemId,
  takes,
  selectedTakeId,
  open,
  onOpenChange,
  onSelect,
  onEvaluateQc,
}: TakeDrawerProps) {
  const { confirm } = useConfirm();
  const [busyTakeId, setBusyTakeId] = useState<number | null>(null);

  const run = async (takeId: number, action: () => Promise<void>) => {
    setBusyTakeId(takeId);
    try {
      await action();
    } finally {
      setBusyTakeId(null);
    }
  };

  const handleSelect = async (takeId: number) => {
    const ok = await confirm({
      title: '选定候选镜头',
      description: `将把镜头 #${itemId} 的成片指向候选 #${takeId}，原有选定会被覆盖。`,
      confirmText: '确定选定',
    });
    if (!ok) return;
    await run(takeId, () => onSelect(takeId));
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[440px] p-0 sm:w-[440px] sm:max-w-none">
        <SheetHeader className="shrink-0 border-b border-border/30">
          <SheetTitle>候选镜头</SheetTitle>
          <SheetDescription>镜头 #{itemId} · 共 {takes.length} 个候选产物</SheetDescription>
        </SheetHeader>

        <div className="min-h-0 flex-1 space-y-3 overflow-y-auto p-4">
          {takes.length === 0 && (
            <div className="flex h-40 items-center justify-center text-sm text-muted-foreground">
              暂无候选镜头
            </div>
          )}

          {takes.map((take) => {
            const metadata = parseTakeMetadata(take.metadataJson);
            const videoSrc = resolveMediaUrl(metadata.videoUrl);
            const isSelected = take.id === selectedTakeId;
            const isQcBlocked = take.qcStatus === 'FAIL';
            const isBusy = busyTakeId === take.id;

            return (
              <div
                key={take.id}
                className={cn(
                  'overflow-hidden rounded-lg border border-border/20 bg-background/70',
                  isSelected && 'border-primary/50',
                  isQcBlocked && !isSelected && 'border-destructive/30'
                )}
              >
                {videoSrc ? (
                  <video
                    src={videoSrc}
                    controls
                    preload="metadata"
                    className="aspect-video w-full bg-black"
                  />
                ) : (
                  <div className="flex aspect-video items-center justify-center bg-muted/20 text-sm text-muted-foreground">
                    暂无可预览视频
                  </div>
                )}

                <div className="space-y-3 p-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-medium">候选 #{take.id}</p>
                      <p className="text-xs text-muted-foreground">
                        {take.modelId || '未知模型'} · 来源 {take.sourceType} #{take.sourceItemId}
                      </p>
                    </div>
                    <Badge variant="outline" className={QC_BADGE_CLASS[take.qcStatus]}>
                      {QC_LABEL[take.qcStatus]}
                    </Badge>
                  </div>

                  <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-muted-foreground">
                    <dt>种子</dt>
                    <dd className="text-foreground/80">{take.seed ?? '—'}</dd>
                    <dt>工作流版本</dt>
                    <dd className="text-foreground/80">{take.workflowVersionId ?? '—'}</dd>
                    {metadata.duration != null && (
                      <>
                        <dt>时长</dt>
                        <dd className="text-foreground/80">{metadata.duration}s</dd>
                      </>
                    )}
                  </dl>

                  {isQcBlocked && (
                    <p className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">
                      质检未通过的候选不可选定，需先重跑质检或人工复核。
                    </p>
                  )}

                  <div className="flex gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      className="flex-1"
                      disabled={isBusy}
                      onClick={() => run(take.id, () => onEvaluateQc(take.id))}
                    >
                      {isBusy ? <Loader2 className="animate-spin" /> : <ShieldCheck />}
                      质检
                    </Button>
                    {isSelected ? (
                      <Button size="sm" variant="secondary" className="flex-1" disabled>
                        <Check />
                        已选定
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        className="flex-1"
                        disabled={isBusy || isQcBlocked}
                        onClick={() => handleSelect(take.id)}
                      >
                        选定
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </SheetContent>
    </Sheet>
  );
}
