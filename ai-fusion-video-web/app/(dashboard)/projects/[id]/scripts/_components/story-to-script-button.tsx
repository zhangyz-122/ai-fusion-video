"use client";

import { useEffect, useState } from "react";
import { Sparkles, Loader2 } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { aiModelApi, type AiModel } from "@/lib/api/ai-model";
import { usePipelineStore } from "@/lib/store/pipeline-store";
import { toastApiError } from "@/lib/api/toast-api-error";

/**
 * 故事转剧本（story_to_script）入口：
 * 读取项目剧本原文，由主 Agent 规划分集大纲、子 Agent 逐集创作对白与场次。
 * 文本模型可选（默认对话模型 / 本地 Ollama / LM Studio 均可）。
 */
export function StoryToScriptButton({
  projectId,
  scriptId,
  rawContentLength,
  onStarted,
}: {
  projectId: number;
  scriptId: number;
  rawContentLength: number;
  onStarted?: () => void | Promise<void>;
}) {
  const { addPipeline, setPanelExpanded, setExpandedTaskId } = usePipelineStore();
  const [open, setOpen] = useState(false);
  const [models, setModels] = useState<AiModel[]>([]);
  const [modelId, setModelId] = useState("");
  const [episodeCount, setEpisodeCount] = useState("1");
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    if (!open) return;
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
  }, [open]);

  const start = async () => {
    const count = Math.max(1, Math.min(50, Number(episodeCount) || 1));
    setStarting(true);
    try {
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
          await onStarted?.();
        },
      });
      setOpen(false);
      setPanelExpanded(true);
      setExpandedTaskId(pipelineId);
    } catch (error) {
      toastApiError(error, "启动故事转剧本失败");
    } finally {
      setStarting(false);
    }
  };

  const longWarning =
    rawContentLength > 50000
      ? `剧本原文约 ${(rawContentLength / 10000).toFixed(1)} 万字，超出单次创作建议长度；建议先按卷/章拆分原文，或分批指定集数创作。`
      : null;

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="inline-flex items-center gap-1.5 rounded-lg border border-primary/40 bg-primary/5 px-3 py-1.5 text-xs font-medium text-primary transition-colors hover:bg-primary/10"
      >
        <Sparkles className="h-3.5 w-3.5" />
        故事转剧本
      </button>

      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="modal-overlay absolute inset-0" onClick={() => !starting && setOpen(false)} />
          <div className="modal-surface relative z-10 w-full max-w-md rounded-2xl border border-border/40 p-6 space-y-4">
            <div className="flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-purple-400" />
              <h2 className="text-lg font-semibold">故事转剧本</h2>
            </div>
            <p className="text-xs leading-relaxed text-muted-foreground">
              AI 读取项目剧本原文（故事概述或全文），规划分集大纲，并逐集创作对白与场次。
            </p>
            {longWarning && (
              <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
                {longWarning}
              </div>
            )}
            <div className="space-y-1.5">
              <label className="text-xs text-muted-foreground" htmlFor="sts-model">
                创作模型（可切换本地模型）
              </label>
              <Select
                value={modelId}
                onValueChange={(v) => setModelId(v ?? "")}
                items={models.map((m) => ({ value: String(m.id), label: m.name }))}
              >
                <SelectTrigger id="sts-model" className="w-full">
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
            <div className="space-y-1.5">
              <label className="text-xs text-muted-foreground" htmlFor="sts-episodes">
                创作集数（1-50）
              </label>
              <input
                id="sts-episodes"
                type="number"
                min={1}
                max={50}
                value={episodeCount}
                onChange={(e) => setEpisodeCount(e.target.value)}
                className="w-full rounded-lg border border-border/40 bg-muted/50 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={() => setOpen(false)}
                disabled={starting}
                className="rounded-xl px-4 py-2 text-sm font-medium hover:bg-muted transition-colors disabled:opacity-50"
              >
                取消
              </button>
              <button
                onClick={() => void start()}
                disabled={starting || !modelId}
                className="inline-flex items-center gap-2 rounded-xl bg-linear-to-r from-purple-600 to-pink-600 px-5 py-2 text-sm font-medium text-white shadow-lg shadow-purple-500/20 transition-all hover:shadow-purple-500/30 disabled:opacity-50"
              >
                {starting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                开始创作
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
