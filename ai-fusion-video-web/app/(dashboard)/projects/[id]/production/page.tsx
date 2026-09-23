'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { productionApi, type ProductionRun } from '@/lib/api/production';
import { toastApiError } from '@/lib/api/toast-api-error';

const STATUS_CLASS: Record<string, string> = {
  RUNNING: 'border-primary/30 bg-primary/10 text-primary',
  SUCCEEDED: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  FAILED: 'border-destructive/30 bg-destructive/10 text-destructive',
};

export default function ProductionOverviewPage() {
  const params = useParams<{ id: string }>();
  const projectId = Number(params.id);
  const [runs, setRuns] = useState<ProductionRun[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRuns(await productionApi.getRuns(projectId));
    } catch (error) {
      toastApiError(error, '获取生产批次失败');
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="space-y-4">
      <div className="flex items-baseline justify-between">
        <h1 className="text-xl font-semibold">生产批次</h1>
        {!loading && (
          <p className="text-xs text-muted-foreground">共 {runs.length} 次运行</p>
        )}
      </div>

      <div className="rounded-lg border border-border/20 bg-background/70">
        {runs.length === 0 && !loading && (
          <p className="px-4 py-12 text-center text-sm text-muted-foreground">
            暂无生产批次
          </p>
        )}
        {runs.map((run) => (
          <div
            key={run.id}
            className="flex items-center justify-between gap-3 border-b border-border/20 px-4 py-3 last:border-b-0"
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">批次 #{run.id}</p>
              <p className="text-xs text-muted-foreground">
                {run.runType} · 分镜 #{run.storyboardId}
                {run.startedAt ? ` · ${new Date(run.startedAt).toLocaleString()}` : ''}
              </p>
            </div>
            <span
              className={`shrink-0 rounded-full border px-2 py-0.5 text-xs font-medium ${
                STATUS_CLASS[run.status] ?? 'border-border/30 bg-muted/40 text-muted-foreground'
              }`}
            >
              {run.status}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
