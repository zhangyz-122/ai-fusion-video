"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Loader2, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { toastApiError } from "@/lib/api/toast-api-error";
import { productionApi, type ProductionRun, type ProductionRunStatus } from "@/lib/api/production";

const statusFilters: Array<{ label: string; value: ProductionRunStatus | "" }> = [
  { label: "全部", value: "" },
  { label: "准备中", value: "CREATED" },
  { label: "生成中", value: "WAITING_GENERATION" },
  { label: "待质检", value: "QC_PENDING" },
  { label: "已选定", value: "SELECTED" },
  { label: "失败", value: "FAILED" },
];

const statusLabels: Record<ProductionRunStatus, string> = {
  CREATED: "准备中",
  WAITING_GENERATION: "生成中",
  QC_PENDING: "待质检",
  SELECTED: "已选定",
  FAILED: "失败",
};

const statusStyles: Record<ProductionRunStatus, string> = {
  CREATED: "text-muted-foreground bg-muted/30 border-border/30",
  WAITING_GENERATION: "text-cyan-600 bg-cyan-500/10 border-cyan-500/20",
  QC_PENDING: "text-amber-600 bg-amber-500/10 border-amber-500/20",
  SELECTED: "text-violet-600 bg-violet-500/10 border-violet-500/20",
  FAILED: "text-rose-600 bg-rose-500/10 border-rose-500/20",
};

const PAGE_SIZE = 10;

export default function ProductionCenterPage() {
  const [status, setStatus] = useState<ProductionRunStatus | "">("");
  const [pageNo, setPageNo] = useState(1);
  const [runs, setRuns] = useState<ProductionRun[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await productionApi.list({ status: status || undefined, pageNo, pageSize: PAGE_SIZE });
      setRuns(data.list ?? []);
      setTotal(data.total ?? 0);
    } catch (error) {
      toastApiError(error, "无法读取生产运行列表");
    } finally {
      setLoading(false);
    }
  }, [status, pageNo]);

  useEffect(() => {
    void load();
  }, [load, revision]);

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="mx-auto w-full max-w-6xl space-y-6 px-6 py-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">生产中心</p>
          <h1 className="text-3xl font-semibold tracking-tight">生产运行</h1>
          <p className="text-muted-foreground">全部三候选生产运行的状态、失败原因与项目入口。</p>
        </div>
        <Button variant="outline" size="icon-sm" onClick={() => setRevision(v => v + 1)} disabled={loading} title="刷新">
          <RefreshCw className={loading ? "animate-spin" : ""} />
        </Button>
      </header>

      <div className="flex flex-wrap gap-2" role="tablist" aria-label="状态筛选">
        {statusFilters.map(f => (
          <button
            key={f.value}
            role="tab"
            aria-selected={status === f.value}
            onClick={() => { setStatus(f.value); setPageNo(1); }}
            className={`rounded-full border px-3 py-1 text-xs transition-colors ${
              status === f.value
                ? "border-violet-500/40 bg-violet-500/10 text-violet-600 dark:text-violet-300"
                : "border-border/30 bg-card/50 text-muted-foreground hover:border-border/50 hover:text-foreground"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex items-center justify-center rounded-xl border border-border/30 bg-card/50 py-16">
          <Loader2 className="animate-spin text-muted-foreground" />
        </div>
      ) : runs.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border/40 bg-card/30 py-16 text-center">
          <p className="text-sm text-muted-foreground">暂无生产运行。在项目分镜中启动三候选生产后，这里会显示全部运行。</p>
        </div>
      ) : (
        <div className="space-y-3">
          {runs.map(run => (
            <article key={run.id} className="rounded-xl border border-border/30 bg-card p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="text-sm font-semibold">Run #{run.id}</span>
                  <span className={`rounded-full border px-2 py-0.5 text-[10px] ${statusStyles[run.status]}`}>
                    {statusLabels[run.status]}
                  </span>
                  {run.selectedTakeId && (
                    <span className="text-xs text-muted-foreground">已选 Take #{run.selectedTakeId}</span>
                  )}
                </div>
                <span className="text-xs text-muted-foreground">
                  {new Date(run.createTime).toLocaleString("zh-CN", { hour12: false })}
                </span>
              </div>
              {run.failureMessage && (
                <p className="mt-2 truncate text-xs text-rose-600 dark:text-rose-300" title={run.failureMessage}>
                  {run.failureMessage}
                </p>
              )}
              {run.projectId && (
                <div className="mt-3">
                  <Link
                    href={`/projects/${run.projectId}`}
                    className="text-xs text-muted-foreground underline-offset-2 hover:underline"
                  >
                    打开项目工作区 →
                  </Link>
                </div>
              )}
            </article>
          ))}
        </div>
      )}

      {total > PAGE_SIZE && (
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <Button variant="outline" size="sm" disabled={pageNo <= 1} onClick={() => setPageNo(p => p - 1)}>
            上一页
          </Button>
          <span>第 {pageNo} / {totalPages} 页 · 共 {total} 条</span>
          <Button variant="outline" size="sm" disabled={pageNo >= totalPages} onClick={() => setPageNo(p => p + 1)}>
            下一页
          </Button>
        </div>
      )}
    </div>
  );
}
