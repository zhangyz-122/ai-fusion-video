"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { Loader2, RefreshCw } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { toastApiError } from "@/lib/api/toast-api-error";
import { productionApi, type ProductionRun, type ProductionRunStatus } from "@/lib/api/production";
import { cn } from "@/lib/utils";

const statusLabels: Record<ProductionRunStatus, string> = {
  CREATED: "准备中",
  WAITING_GENERATION: "生成中",
  QC_PENDING: "待质检",
  SELECTED: "已选定",
  FAILED: "失败",
};

export default function ProjectProductionPage() {
  const params = useParams();
  const projectId = Number(params.id);
  const [runs, setRuns] = useState<ProductionRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await productionApi.list({ pageNo: 1, pageSize: 50 });
      setRuns((data.list ?? []).filter((run) => run.projectId === projectId));
    } catch (error) {
      toastApiError(error, "无法读取本剧生产运行");
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load, revision]);

  const waiting = useMemo(
    () => runs.filter((run) => run.status === "QC_PENDING" || run.status === "WAITING_GENERATION"),
    [runs]
  );

  const ordered = useMemo(() => {
    const rank: Record<ProductionRunStatus, number> = {
      QC_PENDING: 0,
      WAITING_GENERATION: 1,
      FAILED: 2,
      CREATED: 3,
      SELECTED: 4,
    };
    return [...runs].sort((a, b) => (rank[a.status] - rank[b.status]) || (b.id - a.id));
  }, [runs]);

  return (
    <div className="mx-auto w-full max-w-4xl space-y-6 px-6 py-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">拍 · 生产</p>
          <h1 className="text-2xl font-semibold tracking-tight">这一剧的镜头生产</h1>
          <p className="text-sm text-muted-foreground">在分镜里锁定首帧后，才能开三候选。待质检的镜头优先处理。</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="icon-sm" onClick={() => setRevision((v) => v + 1)} disabled={loading} title="刷新">
            <RefreshCw className={loading ? "animate-spin" : ""} />
          </Button>
          <Link className={buttonVariants({ variant: "video" })} href={`/projects/${projectId}/storyboards`}>
            去分镜拍这一镜
          </Link>
        </div>
      </header>

      {loading ? (
        <div className="flex justify-center py-16"><Loader2 className="animate-spin text-muted-foreground" /></div>
      ) : runs.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border/40 px-6 py-16 text-center">
          <p className="text-sm text-muted-foreground">还没有生产运行。</p>
          <Link className={cn(buttonVariants({ variant: "video" }), "mt-4 inline-flex")} href={`/projects/${projectId}/storyboards`}>
            打开分镜
          </Link>
        </div>
      ) : (
        <div className="space-y-3">
          {waiting.length > 0 && (
            <p className="text-xs text-muted-foreground">{waiting.length} 条待处理</p>
          )}
          {ordered.map((run) => (
            <article key={run.id} className="rounded-xl border border-border/30 bg-card p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm font-medium">Run #{run.id} · 镜头 {run.storyboardItemId}</span>
                <span className="text-xs text-muted-foreground">{statusLabels[run.status]}</span>
              </div>
              {run.failureMessage && (
                <p className="mt-2 text-xs text-rose-600 dark:text-rose-300">{run.failureMessage}</p>
              )}
              <Link href={`/projects/${projectId}/storyboards`} className="mt-3 inline-block text-xs text-muted-foreground hover:underline">
                打开分镜处理 →
              </Link>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
