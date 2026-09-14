"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Clapperboard, Loader2, RefreshCcw, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useConfirm } from "@/components/ui/confirm-dialog";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { getApiErrorMessage } from "@/lib/api/api-error";
import {
  productionApi,
  type ProductionRunDetail,
} from "@/lib/api/production";
import { toastApiError } from "@/lib/api/toast-api-error";
import {
  formatRunTime,
  repairRouteLabels,
  runStatusLabels,
  runStatusStyles,
  stepStatusLabels,
} from "./production-status";
import { FailureReasonPanel, MetaRow, RepairAttemptPanel, TakeCard } from "./run-detail-sections";

/** 允许手动重新同步的状态：底层任务结果可能尚未落库到运行 */
const RECONCILABLE_STATUSES = new Set(["WAITING_GENERATION", "QC_PENDING", "FAILED"]);

export function RunDetailDrawer({
  open,
  runId,
  onOpenChange,
  onChanged,
}: {
  open: boolean;
  runId: number | null;
  onOpenChange: (open: boolean) => void;
  onChanged?: () => void;
}) {
  const { confirm } = useConfirm();
  const [detail, setDetail] = useState<ProductionRunDetail | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [repairing, setRepairing] = useState(false);

  const loadDetail = useCallback(async (id: number) => {
    setLoadingDetail(true);
    setLoadError(null);
    try {
      setDetail(await productionApi.detail(id));
    } catch (error) {
      setLoadError(getApiErrorMessage(error));
    } finally {
      setLoadingDetail(false);
    }
  }, []);

  useEffect(() => {
    if (!open || runId == null) {
      setDetail(null);
      setLoadError(null);
      return;
    }
    void loadDetail(runId);
  }, [open, runId, loadDetail]);

  const run = detail?.run ?? null;
  const step = detail?.step ?? null;
  const latestRepair = detail?.repairAttempts?.at(-1) ?? null;
  const canRepair = latestRepair?.status === "PLANNED";
  const canReconcile = !!run && !!step?.videoTaskId && RECONCILABLE_STATUSES.has(run.status);
  const actionBusy = syncing || repairing;

  const handleReconcile = async () => {
    if (!run) return;
    const ok = await confirm({
      title: "重新同步候选视频",
      description: "将检查底层视频任务的最新结果并同步候选列表，不会重新发起生成。",
      variant: "info",
      confirmText: "开始同步",
    });
    if (!ok) return;
    setSyncing(true);
    try {
      const next = await productionApi.reconcile(run.id);
      setDetail(next);
      onChanged?.();
      toast.success(`同步完成，运行状态：${runStatusLabels[next.run.status] ?? next.run.status}`);
    } catch (error) {
      toastApiError(error, "同步候选视频失败");
    } finally {
      setSyncing(false);
    }
  };

  const handleRepair = async () => {
    if (!run || !latestRepair) return;
    const ok = await confirm({
      title: `提交重试（修复计划 #${latestRepair.attemptNo}）`,
      description: `将按「${repairRouteLabels[latestRepair.route] ?? latestRepair.route}」重新发起视频生成，完成后可重新同步查看新的候选视频。`,
      variant: "warning",
      confirmText: "确认重试",
    });
    if (!ok) return;
    setRepairing(true);
    try {
      const next = await productionApi.repair(run.id);
      setDetail(next);
      onChanged?.();
      toast.success("已提交重试任务，等待新一轮生成结果");
    } catch (error) {
      toastApiError(error, "提交重试失败");
    } finally {
      setRepairing(false);
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="z-[11000] w-full gap-0 p-0 sm:max-w-lg">
        <SheetHeader className="border-b border-border/20 pb-4">
          <SheetTitle className="flex items-center gap-2">
            <Clapperboard className="h-4 w-4 text-violet-500" />
            {runId != null ? `Run #${runId} · 运行详情` : "运行详情"}
          </SheetTitle>
          <SheetDescription>查看运行状态、失败原因与候选视频，支持重新同步与重试。</SheetDescription>
        </SheetHeader>

        <div className="flex-1 space-y-5 overflow-y-auto p-5">
          {loadError ? (
            <div className="space-y-3 rounded-xl border border-border/30 bg-card/50 p-4 text-center">
              <p className="break-all text-xs text-muted-foreground">{loadError}</p>
              <Button
                variant="outline"
                size="xs"
                onClick={() => runId != null && void loadDetail(runId)}
              >
                <RefreshCw /> 重新加载
              </Button>
            </div>
          ) : loadingDetail || !detail || !run ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="animate-spin text-muted-foreground" />
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between rounded-xl border border-border/30 bg-muted/15 px-4 py-3">
                <div className="flex items-center gap-2">
                  <span
                    className={`rounded-full border px-2 py-0.5 text-[10px] ${runStatusStyles[run.status]}`}
                  >
                    {runStatusLabels[run.status]}
                  </span>
                  {run.status === "WAITING_GENERATION" && (
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-violet-500" />
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {canReconcile && (
                    <Button
                      variant="outline"
                      size="xs"
                      onClick={() => void handleReconcile()}
                      disabled={actionBusy}
                    >
                      {syncing ? <Loader2 className="animate-spin" /> : <RefreshCcw />} 重新同步
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => void loadDetail(run.id)}
                    disabled={loadingDetail || actionBusy}
                    title="刷新详情"
                  >
                    <RefreshCw className={loadingDetail ? "animate-spin" : ""} />
                  </Button>
                </div>
              </div>

              <div className="space-y-2 rounded-lg border border-border/20 bg-background/70 px-4 py-3">
                <MetaRow label="分镜条目" value={`#${run.storyboardItemId}`} />
                <MetaRow
                  label="视频任务"
                  value={step?.videoTaskId ? `#${step.videoTaskId}` : "未关联"}
                />
                <MetaRow
                  label="生成步骤"
                  value={
                    step
                      ? `${stepStatusLabels[step.status] ?? step.status}${
                          step.attempt > 1 ? ` · 第 ${step.attempt} 次尝试` : ""
                        }`
                      : "—"
                  }
                />
                <MetaRow label="创建时间" value={formatRunTime(run.createTime)} />
                <MetaRow label="更新时间" value={formatRunTime(run.updateTime)} />
              </div>

              <FailureReasonPanel
                code={run.failureCode}
                message={run.failureMessage}
                stepMessage={step?.errorMessage ?? null}
              />

              {latestRepair && (
                <RepairAttemptPanel
                  attempt={latestRepair}
                  historyCount={detail.repairAttempts?.length ?? 1}
                  canRepair={canRepair}
                  repairing={repairing}
                  onRepair={() => void handleRepair()}
                />
              )}
              {run.status === "FAILED" && !latestRepair && (
                <p className="break-all px-1 text-[11px] leading-relaxed text-muted-foreground">
                  当前失败没有可自动执行的重试计划，可在项目分镜中重新发起生产。
                </p>
              )}

              <section className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-medium">候选视频</h3>
                  <span className="text-xs text-muted-foreground">
                    {detail.takes.length}/3 · {run.selectedTakeId ? "已选定" : "未选定"}
                  </span>
                </div>
                {detail.takes.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-border/40 px-4 py-8 text-center text-xs text-muted-foreground">
                    暂无候选视频。生成中可稍后刷新，或尝试重新同步。
                  </div>
                ) : (
                  detail.takes.map(take => (
                    <TakeCard key={take.id} take={take} selected={run.selectedTakeId === take.id} />
                  ))
                )}
              </section>
            </>
          )}
        </div>

        <SheetFooter className="border-t border-border/20 sm:flex-row sm:justify-between">
          <span className="text-[11px] text-muted-foreground">
            {run ? `Run #${run.id} · 分镜条目 #${run.storyboardItemId}` : "运行详情"}
          </span>
          {run?.projectId && (
            <Link
              href={`/projects/${run.projectId}`}
              className="text-xs text-muted-foreground underline-offset-2 transition-colors hover:text-foreground hover:underline"
            >
              打开项目工作区 →
            </Link>
          )}
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
