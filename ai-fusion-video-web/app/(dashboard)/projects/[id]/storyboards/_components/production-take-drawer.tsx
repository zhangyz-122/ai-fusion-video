"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Check,
  CheckCircle2,
  Clapperboard,
  Loader2,
  PlayCircle,
  RefreshCw,
  RotateCcw,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { toastApiError } from "@/lib/api/toast-api-error";
import { aiModelApi, type AiModel } from "@/lib/api/ai-model";
import { capabilityApi, type VideoProfileOption } from "@/lib/api/capability";
import { resolveMediaUrl } from "@/lib/api/client";
import {
  productionApi,
  type ProductionQcStatus,
  type ProductionRunDetail,
  type ShotReadiness,
} from "@/lib/api/production";
import type { StoryboardItem } from "@/lib/api/storyboard";
import { useAuthStore } from "@/lib/store/auth-store";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const statusLabels: Record<string, string> = {
  CREATED: "准备中",
  WAITING_GENERATION: "生成中",
  QC_PENDING: "待质检",
  SELECTED: "已选定",
  FAILED: "失败",
};

const qcLabels: Record<ProductionQcStatus, string> = {
  PASS: "通过",
  FAIL: "不通过",
  REVIEW_REQUIRED: "待检查",
};

function takeStatusClass(status: ProductionQcStatus) {
  if (status === "PASS") return "text-emerald-600 bg-emerald-500/10 border-emerald-500/20";
  if (status === "FAIL") return "text-rose-600 bg-rose-500/10 border-rose-500/20";
  return "text-amber-600 bg-amber-500/10 border-amber-500/20";
}

export function ProductionTakeDrawer({
  open,
  item,
  onOpenChange,
  onComposeSubmitted,
}: {
  open: boolean;
  item: StoryboardItem | null;
  onOpenChange: (open: boolean) => void;
  onComposeSubmitted?: (taskId: string) => void;
}) {
  const [detail, setDetail] = useState<ProductionRunDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [workingTakeId, setWorkingTakeId] = useState<number | null>(null);
  const [composing, setComposing] = useState(false);
  const [repairing, setRepairing] = useState(false);
  const [videoModels, setVideoModels] = useState<AiModel[]>([]);
  const [profiles, setProfiles] = useState<VideoProfileOption[]>([]);
  const [modelId, setModelId] = useState<string>("");
  const [profileId, setProfileId] = useState<string>("");
  const [readiness, setReadiness] = useState<ShotReadiness | null>(null);
  const isAdmin = useAuthStore((s) => s.user?.roles?.includes("admin") ?? false);

  useEffect(() => {
    if (!open) return;
    let active = true;
    async function loadOptions() {
      try {
        const models = await aiModelApi.listByType(3);
        if (!active) return;
        const enabled = models.filter(m => m.status === 1);
        setVideoModels(enabled);
        const fallback = enabled.find(m => m.defaultModel) ?? enabled[0];
        setModelId(prev => prev || (fallback ? String(fallback.id) : ""));
      } catch {
        if (active) setVideoModels([]);
      }
      try {
        const list = await capabilityApi.videoProfiles();
        if (active) setProfiles(list);
      } catch {
        if (active) setProfiles([]);
      }
    }
    void loadOptions();
    return () => { active = false; };
  }, [open]);

  const refresh = useCallback(async (runId: number) => {
    const next = await productionApi.detail(runId);
    setDetail(next);
    return next;
  }, []);

  useEffect(() => {
    if (!open) {
      setDetail(null);
      return;
    }
    setDetail(null);
  }, [open, item?.id]);

  useEffect(() => {
    if (!open || !detail?.run.id) return;
    if (detail.run.status !== "WAITING_GENERATION") return;
    const timer = window.setInterval(() => {
      void refresh(detail.run.id).catch((error) => {
        console.error("刷新 ProductionRun 失败", error);
      });
    }, 5000);
    return () => window.clearInterval(timer);
  }, [detail?.run.id, detail?.run.status, open, refresh]);

  useEffect(() => {
    if (!open || !item?.id) {
      setReadiness(null);
      return;
    }
    let active = true;
    productionApi.readiness(item.id)
      .then((next) => {
        if (active) setReadiness(next);
      })
      .catch(() => {
        if (active) setReadiness(null);
      });
    return () => {
      active = false;
    };
  }, [open, item?.id]);

  const start = async () => {
    if (!item) return;
    setLoading(true);
    try {
      const next = await productionApi.start({
        storyboardItemId: item.id,
        idempotencyKey: `storyboard-item-${item.id}-${crypto.randomUUID()}`,
        prompt: item.videoPrompt || item.content || undefined,
        modelId: modelId ? Number(modelId) : undefined,
        workflowProfileId: profileId ? Number(profileId) : undefined,
        firstFrameImageUrl: item.firstFrameImageUrl,
        lastFrameImageUrl: item.lastFrameImageUrl,
        duration: item.duration || undefined,
      });
      setDetail(next);
    } catch (error) {
      toastApiError(error, "启动生产失败");
    } finally {
      setLoading(false);
    }
  };

  const reconcile = async () => {
    if (!detail) return;
    setLoading(true);
    try {
      setDetail(await productionApi.reconcile(detail.run.id));
    } catch (error) {
      toastApiError(error, "同步候选视频失败");
    } finally {
      setLoading(false);
    }
  };

  const updateQc = async (takeId: number, qcStatus: ProductionQcStatus) => {
    if (!detail) return;
    setWorkingTakeId(takeId);
    try {
      setDetail(await productionApi.updateQc(detail.run.id, takeId, { qcStatus }));
    } catch (error) {
      toastApiError(error, "更新质检结果失败");
    } finally {
      setWorkingTakeId(null);
    }
  };

  const selectTake = async (takeId: number) => {
    if (!detail) return;
    setWorkingTakeId(takeId);
    try {
      setDetail(await productionApi.selectTake(detail.run.id, takeId));
    } catch (error) {
      toastApiError(error, "选择候选视频失败");
    } finally {
      setWorkingTakeId(null);
    }
  };

  const compose = async () => {
    if (!detail) return;
    setComposing(true);
    try {
      const taskId = await productionApi.compose(detail.run.id);
      onComposeSubmitted?.(taskId);
    } catch (error) {
      toastApiError(error, "提交合成失败");
    } finally {
      setComposing(false);
    }
  };

  const repair = async () => {
    if (!detail) return;
    setRepairing(true);
    try {
      setDetail(await productionApi.repair(detail.run.id));
    } catch (error) {
      toastApiError(error, "提交修复任务失败");
    } finally {
      setRepairing(false);
    }
  };

  const title = item?.shotNumber || item?.autoShotNumber || (item ? `镜头 ${item.id}` : "镜头生产");
  const canCompose = detail?.run.status === "SELECTED" && !!detail.run.selectedTakeId;
  const waiting = detail?.run.status === "WAITING_GENERATION";
  const latestRepair = detail?.repairAttempts?.at(-1) ?? null;
  const canRepair = detail?.run.status === "FAILED" && latestRepair?.status === "PLANNED";

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-lg p-0 gap-0">
        <SheetHeader className="border-b border-border/20 pb-4">
          <SheetTitle className="flex items-center gap-2">
            <Clapperboard className="h-4 w-4 text-violet-500" />
            {title} · 生产这一镜
          </SheetTitle>
          <SheetDescription>
            生产这一镜会生成 3 个候选视频，逐个质检后选用。
          </SheetDescription>
        </SheetHeader>

        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          {!detail ? (
            <div className="space-y-4">
              <div className="rounded-xl border border-violet-500/20 bg-violet-500/5 p-4 space-y-3">
                <div className="text-sm font-medium">将生成 3 个候选视频</div>
                <p className="text-xs leading-relaxed text-muted-foreground">
                  这会创建一个可恢复的 ProductionRun，不会覆盖现有 Legacy 视频字段。
                </p>
                {readiness && !readiness.ready && (
                  <ul className="space-y-1 text-xs text-amber-700 dark:text-amber-300">
                    {readiness.blockers.map((blocker) => (
                      <li key={blocker.code}>· {blocker.message}</li>
                    ))}
                  </ul>
                )}
              </div>
              <div className="space-y-3">
                {isAdmin && (
                  <>
                <div className="space-y-1.5">
                  <label className="text-xs text-muted-foreground" htmlFor="prod-video-model">视频模型</label>
                  <Select value={modelId} onValueChange={v => setModelId(v ?? "")} items={videoModels.map(m => ({ value: String(m.id), label: m.name }))}>
                    <SelectTrigger id="prod-video-model" className="w-full">
                      <SelectValue placeholder={videoModels.length ? "选择视频模型" : "暂无可用视频模型"} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {videoModels.map(m => (
                          <SelectItem key={m.id} value={String(m.id)}>{m.name}{m.defaultModel ? "（默认）" : ""}</SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs text-muted-foreground" htmlFor="prod-video-profile">工作流 Profile（可选）</label>
                  <Select value={profileId} onValueChange={v => setProfileId(v ?? "")} items={[{ value: "", label: "跟随模型默认配置" }, ...profiles.map(p => ({ value: String(p.id), label: `${p.name}（${p.purpose ?? p.code}）` }))]}>
                    <SelectTrigger id="prod-video-profile" className="w-full">
                      <SelectValue placeholder="跟随模型默认配置" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="">跟随模型默认配置</SelectItem>
                        {profiles.map(p => (
                          <SelectItem key={p.id} value={String(p.id)}>{p.name}（{p.purpose ?? p.code}）</SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </div>
                  </>
                )}
                <Button variant="video" className="w-full" onClick={() => void start()} disabled={loading || !item || !modelId || readiness?.ready === false}>
                  {loading ? <Loader2 className="animate-spin" /> : <Clapperboard />}
                  生产这一镜
                </Button>
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between rounded-xl border border-border/30 bg-muted/15 px-4 py-3">
                <div>
                  <div className="text-xs text-muted-foreground">生产状态</div>
                  <div className="mt-1 text-sm font-semibold">{statusLabels[detail.run.status] || detail.run.status}</div>
                </div>
                <div className="flex items-center gap-2">
                  {waiting && <Loader2 className="h-4 w-4 animate-spin text-violet-500" />}
                  <Button variant="ghost" size="icon-sm" onClick={() => void refresh(detail.run.id)} disabled={loading} title="刷新">
                    <RefreshCw className={loading ? "animate-spin" : ""} />
                  </Button>
                </div>
              </div>

              {detail.run.failureMessage && (
                <div className="rounded-xl border border-rose-500/20 bg-rose-500/5 px-4 py-3 text-xs text-rose-700 dark:text-rose-300">
                  {detail.run.failureMessage}
                </div>
              )}

              {latestRepair && (
                <div className="flex items-center justify-between gap-3 rounded-xl border border-amber-500/20 bg-amber-500/5 px-4 py-3 text-xs">
                  <div className="min-w-0">
                    <div className="font-medium text-amber-700 dark:text-amber-300">
                      修复计划 #{latestRepair.attemptNo} · {latestRepair.route}
                    </div>
                    <div className="mt-1 truncate text-muted-foreground">
                      {latestRepair.status}{latestRepair.reason ? ` · ${latestRepair.reason}` : ""}
                    </div>
                  </div>
                  {canRepair && (
                    <Button variant="outline" size="xs" onClick={() => void repair()} disabled={repairing}>
                      {repairing ? <Loader2 className="animate-spin" /> : <RotateCcw />} 重新提交
                    </Button>
                  )}
                </div>
              )}

              {waiting && (
                <div className="flex items-center justify-between rounded-xl border border-border/30 px-4 py-3 text-xs text-muted-foreground">
                  <span>任务完成后会自动同步候选视频</span>
                  <Button variant="outline" size="xs" onClick={() => void reconcile()} disabled={loading}>
                    <RotateCcw /> 手动同步
                  </Button>
                </div>
              )}

              {detail.takes.length === 0 && !waiting && detail.run.status !== "FAILED" && (
                <div className="rounded-xl border border-dashed border-border/40 px-4 py-8 text-center text-xs text-muted-foreground">
                  暂无候选结果，请刷新或手动同步。
                </div>
              )}

              <div className="space-y-3">
                {detail.takes.map((take) => {
                  const isSelected = detail.run.selectedTakeId === take.id;
                  const isWorking = workingTakeId === take.id;
                  return (
                    <div key={take.id} className={`rounded-xl border p-3 space-y-3 ${isSelected ? "border-violet-500/50 bg-violet-500/5" : "border-border/30"}`}>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-sm font-medium">
                          {isSelected ? <CheckCircle2 className="h-4 w-4 text-violet-500" /> : <span className="text-muted-foreground">#{take.takeIndex}</span>}
                          Take {take.takeIndex}
                        </div>
                        <span className={`rounded-full border px-2 py-0.5 text-[10px] ${takeStatusClass(take.qcStatus)}`}>
                          {qcLabels[take.qcStatus]}
                        </span>
                      </div>
                      {take.videoUrl ? (
                        <video
                          controls
                          preload="metadata"
                          src={resolveMediaUrl(take.videoUrl) ?? undefined}
                          poster={resolveMediaUrl(take.coverUrl) ?? undefined}
                          className="h-40 w-full rounded-lg border border-border/20 bg-background/70"
                        />
                      ) : (
                        <div className="flex h-16 items-center justify-between gap-2 rounded-lg bg-muted/30 border border-border/20 px-3 text-xs text-muted-foreground">
                          <span className="flex items-center gap-2"><PlayCircle className="h-4 w-4" />{take.videoErrorMsg ? "生成失败" : "视频尚未生成"}</span>
                          {take.videoErrorMsg && <span className="truncate max-w-40" title={take.videoErrorMsg}>{take.videoErrorMsg}</span>}
                        </div>
                      )}
                      <div className="flex items-center gap-2">
                          <Button variant="outline" size="xs" onClick={() => void updateQc(take.id, "PASS")} disabled={isWorking}>
                            {isWorking ? <Loader2 className="animate-spin" /> : <Check />} 通过
                          </Button>
                          <Button variant="destructive" size="xs" onClick={() => void updateQc(take.id, "FAIL")} disabled={isWorking}>
                            <XCircle /> 不通过
                          </Button>
                          {take.qcStatus === "PASS" && !isSelected && (
                            <Button variant="video" size="xs" onClick={() => void selectTake(take.id)} disabled={isWorking}>
                              选用
                            </Button>
                          )}
                      </div>
                      {take.qcNote && <p className="text-[11px] text-muted-foreground">{take.qcNote}</p>}
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>

        <SheetFooter className="border-t border-border/20 sm:flex-row sm:justify-between">
          <span className="text-[11px] text-muted-foreground">
            {detail ? `Run #${detail.run.id}` : "尚未创建 ProductionRun"}
          </span>
          <Button variant="video" onClick={() => void compose()} disabled={!canCompose || composing}>
            {composing ? <Loader2 className="animate-spin" /> : <Clapperboard />}
            使用选中 Take 合成
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
