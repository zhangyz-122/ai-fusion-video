"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Sparkles, Loader2, ArrowRight, FileText } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { aiModelApi, type AiModel } from "@/lib/api/ai-model";
import { scriptApi } from "@/lib/api/script";
import { usePipelineStore } from "@/lib/store/pipeline-store";
import { useConfirm } from "@/components/ui/confirm-dialog";
import { toastApiError } from "@/lib/api/toast-api-error";
import { useAuthStore } from "@/lib/store/auth-store";

/** 超过该字数自动切换为“自动分块解析”模式 */
const LONG_TEXT_THRESHOLD = 30000;

type Phase = "idle" | "starting" | "polling";

/**
 * 原文页的转剧本面板：
 * - 短文本走 story_to_script 主 Agent（可选模型、可指定集数）；
 * - 超长文本走后端章节感知分块解析，任何模型都能稳定完成；
 * - 原文本身已是“第X集/场次”结构时可改用按结构解析。
 * 转换结果（剧集）统一在「剧本」页管理。
 */
export function StoryToScriptPanel({
  projectId,
  scriptId,
  rawContent,
  episodeCount,
  projectName,
  onConverted,
}: {
  projectId: number;
  scriptId: number;
  rawContent: string;
  episodeCount: number;
  projectName?: string;
  onConverted?: () => void | Promise<void>;
}) {
  const router = useRouter();
  const { confirm } = useConfirm();
  const isAdmin = useAuthStore((state) => state.user?.roles?.includes("admin") ?? false);
  const { addPipeline, setPanelExpanded, setExpandedTaskId } = usePipelineStore();

  const [models, setModels] = useState<AiModel[]>([]);
  const [modelId, setModelId] = useState("");
  const [episodeTarget, setEpisodeTarget] = useState("1");
  const [phase, setPhase] = useState<Phase>("idle");
  const [progress, setProgress] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const pollTimerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (pollTimerRef.current !== null) window.clearInterval(pollTimerRef.current);
    };
  }, []);

  const hasText = rawContent.trim().length > 0;
  const longText = rawContent.length > LONG_TEXT_THRESHOLD;
  const busy = phase !== "idle";

  useEffect(() => {
    let active = true;
    aiModelApi
      .listByType(1)
      .then((models) => {
        if (!active) return;
        const enabled = models.filter((m) => m.status === 1);
        setModels(enabled);
        const fallback = enabled.find((m) => m.defaultModel) ?? enabled[0];
        setModelId((prev) => prev || (fallback ? String(fallback.id) : ""));
      })
      .catch(() => {
        if (active) setModels([]);
      });
    return () => {
      active = false;
    };
  }, []);

  /** 已有剧集时，转换前先重置（保留原文，清空旧剧集） */
  const resetExistingEpisodes = async () => {
    if (episodeCount === 0) return true;
    const ok = await confirm({
      title: "重新转换剧本",
      description: `剧本已存在 ${episodeCount} 集。转换前会清空这些剧集与场次（原文保留），确定继续？`,
      variant: "ai",
      confirmText: "清空并重新转换",
    });
    if (!ok) return false;
    await scriptApi.replaceSource(scriptId, rawContent);
    return true;
  };

  /** DONE 只代表模型结束输出；必须确认数据库已经产生结构化分集和场次 */
  const verifyEpisodes = async () => {
    const eps = await scriptApi.listEpisodes(scriptId);
    const sceneTotal = (
      await Promise.all(eps.map((ep) => scriptApi.listScenes(ep.id)))
    ).reduce((total, scenes) => total + scenes.length, 0);
    if (eps.length === 0 || sceneTotal === 0) {
      throw new Error(
        `剧本仍未生成有效结构（分集 ${eps.length}，场次 ${sceneTotal}）。请更换支持工具调用的模型后重试。`,
      );
    }
  };

  const startStoryToScript = async () => {
    if (!(await resetExistingEpisodes())) return;
    setDone(false);
    setPhase("starting");
    try {
      const count = Math.max(1, Math.min(50, Number(episodeTarget) || 1));
      const pipelineId = addPipeline({
        label: `故事转剧本 · ${count} 集`,
        projectId,
        request: {
          agentType: "story_to_script",
          toolExecutionMode: "FULL_ACCESS",
          category: "pipeline",
          title: `故事转剧本 · ${count} 集`,
          projectId,
          modelId: modelId ? Number(modelId) : undefined,
          message: `请根据剧本原文创作 ${count} 集结构化剧本，逐集完成对白与场次。`,
          context: { projectId, scriptId },
        },
        onComplete: async () => {
          await verifyEpisodes();
          setDone(true);
          await onConverted?.();
        },
      });
      setPanelExpanded(true);
      setExpandedTaskId(pipelineId);
    } catch (error) {
      toastApiError(error, "启动转换失败");
    } finally {
      setPhase("idle");
    }
  };

  const startStructuredParse = async () => {
    if (!(await resetExistingEpisodes())) return;
    setDone(false);
    setPhase("starting");
    try {
      const displayTitle = projectName?.trim() || "未命名项目";
      const pipelineId = addPipeline({
        label: `AI 解析剧本 - ${displayTitle}`,
        projectId,
        request: {
          agentType: "script_full_parse",
          toolExecutionMode: "FULL_ACCESS",
          category: "pipeline",
          title: `AI 剧本解析：${displayTitle}`,
          projectId,
          context: { scriptId },
        },
        onComplete: async () => {
          // 本地模型未落库时，按原文标题兜底生成分集与场次
          let fallbackError = "";
          try {
            await scriptApi.fallbackParse(scriptId);
          } catch (error) {
            fallbackError = error instanceof Error ? error.message : String(error);
          }
          try {
            await verifyEpisodes();
          } catch (error) {
            throw new Error(
              fallbackError || (error instanceof Error ? error.message : String(error)),
            );
          }
          setDone(true);
          await onConverted?.();
        },
      });
      setPanelExpanded(true);
      setExpandedTaskId(pipelineId);
    } catch (error) {
      toastApiError(error, "启动解析失败");
    } finally {
      setPhase("idle");
    }
  };

  const startAutoSplit = async () => {
    setDone(false);
    setPhase("starting");
    try {
      await scriptApi.autoSplitNovel(scriptId, modelId ? Number(modelId) : undefined);
      setPhase("polling");
      setProgress("正在分块…");
      pollTimerRef.current = window.setInterval(async () => {
        try {
          const status = await scriptApi.autoSplitStatus(scriptId);
          setProgress(status.parsingProgress ?? "解析中…");
          if (status.parsingStatus === 2 || status.parsingStatus === 3) {
            if (pollTimerRef.current !== null) window.clearInterval(pollTimerRef.current);
            pollTimerRef.current = null;
            setPhase("idle");
            setProgress(null);
            if (status.parsingStatus === 3) {
              toastApiError(new Error(status.parsingProgress ?? "解析失败"), "自动分块解析失败");
            } else {
              setDone(true);
              await onConverted?.();
            }
          }
        } catch {
          if (pollTimerRef.current !== null) window.clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
          setPhase("idle");
          setProgress(null);
        }
      }, 3000);
    } catch (error) {
      setPhase("idle");
      toastApiError(error, "启动自动分块解析失败");
    }
  };

  const start = () => {
    if (longText) {
      void startAutoSplit();
    } else {
      void startStoryToScript();
    }
  };

  return (
    <section className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-5 space-y-4">
      <div className="flex items-start gap-3">
        <div className="h-9 w-9 rounded-xl bg-violet-500/10 flex items-center justify-center shrink-0">
          <Sparkles className="h-4.5 w-4.5 text-violet-400" />
        </div>
        <div className="min-w-0">
          <h3 className="font-semibold">转剧本</h3>
          <p className="text-xs text-muted-foreground mt-0.5">
            AI 读取原文，规划分集大纲并逐集创作对白与场次；转换结果在「剧本」页按剧集管理。
          </p>
        </div>
      </div>

      {!hasText ? (
        <p className="rounded-lg border border-border/20 bg-background/70 px-3 py-2.5 text-xs text-muted-foreground">
          请先导入或粘贴原文，再开始转换。
        </p>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3">
            {isAdmin && (
            <div className="flex-1 min-w-[200px] space-y-1.5">
              <label className="text-xs text-muted-foreground" htmlFor="source-sts-model">
                创作模型（可切换本地模型）
              </label>
              <Select
                value={modelId}
                onValueChange={(v) => setModelId(v ?? "")}
                items={models.map((m) => ({ value: String(m.id), label: m.name }))}
              >
                <SelectTrigger id="source-sts-model" className="w-full" disabled={busy}>
                  <SelectValue placeholder={models.length ? "选择文本模型" : "暂无可用文本模型"} />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {models.map((m) => (
                      <SelectItem key={m.id} value={String(m.id)}>
                        {m.name}
                        {m.defaultModel ? "（默认）" : ""}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
            )}
            {!longText && (
              <div className="w-28 space-y-1.5">
                <label className="text-xs text-muted-foreground" htmlFor="source-sts-episodes">
                  创作集数
                </label>
                <input
                  id="source-sts-episodes"
                  type="number"
                  min={1}
                  max={50}
                  value={episodeTarget}
                  onChange={(e) => setEpisodeTarget(e.target.value)}
                  disabled={busy}
                  className="w-full rounded-lg border border-border/40 bg-muted/50 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:opacity-50"
                />
              </div>
            )}
          </div>

          {longText && (
            <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
              原文约 {(rawContent.length / 10000).toFixed(1)} 万字。将使用自动分块解析：平台按章节把原文切成小块，
              逐块 AI 转写为剧本场次，任何模型都可稳定完成。
            </div>
          )}

          {episodeCount > 0 && (
            <p className="text-xs text-muted-foreground">
              当前剧本已有 {episodeCount} 集，重新转换前会先清空（原文保留）。
            </p>
          )}

          {progress && (
            <div className="flex items-center gap-2 rounded-lg border border-primary/30 bg-primary/5 px-3 py-2 text-xs text-primary">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              {progress}
            </div>
          )}

          {done && (
            <div className="flex items-center justify-between rounded-lg border border-green-500/30 bg-green-500/5 px-3 py-2 text-xs text-green-600 dark:text-green-400">
              转换完成，剧集已在「剧本」页生成。
              <Button size="xs" variant="secondary" onClick={() => router.push(`/projects/${projectId}/scripts`)}>
                前往剧本页
                <ArrowRight data-icon="inline-end" />
              </Button>
            </div>
          )}

          <div className="flex flex-wrap items-center gap-2 pt-1">
            <Button variant="ai" onClick={start} disabled={busy || !modelId}>
              {busy ? <Loader2 className="animate-spin" /> : <Sparkles />}
              {phase === "polling"
                ? "解析中…"
                : longText
                  ? "开始自动分块解析"
                  : "转成剧本"}
            </Button>
            {!longText && (
              <Button variant="ghost" size="sm" onClick={() => void startStructuredParse()} disabled={busy}>
                <FileText data-icon="inline-start" />
                按剧本结构解析
              </Button>
            )}
          </div>
          {!longText && (
            <p className="text-[11px] text-muted-foreground">
              原文已是「第X集 / 场次」标准剧本格式？可改用按结构解析，直接按结构拆分为剧集。
            </p>
          )}
        </>
      )}
    </section>
  );
}
