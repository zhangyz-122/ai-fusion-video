"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CircleAlert, Loader2, RotateCcw, Sparkles, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { scriptApi } from "@/lib/api/script";
import { aiModelApi, type AiModel } from "@/lib/api/ai-model";
import { toastApiError } from "@/lib/api/toast-api-error";

/** 每块目标字符数的可选范围与默认值（与后端钳制规则一致） */
const MIN_CHUNK_CHARS = 2000;
const MAX_CHUNK_CHARS = 12000;
const DEFAULT_CHUNK_CHARS = 6000;

/** 解析状态轮询间隔 */
const POLL_INTERVAL_MS = 3000;

type Phase = "idle" | "tracking" | "done" | "failed";

/**
 * 故事转剧本（自动分块解析）入口：
 * 读取剧本原文，按章节/段落分块逐块调用文本模型改写为结构化场次，
 * 解析期间轮询展示实时进度，完成后给出"共 N 集"结果摘要。
 */
export function StoryToScriptButton({
  scriptId,
  rawContentLength,
  onStarted,
}: {
  projectId: number;
  scriptId: number;
  rawContentLength: number;
  onStarted?: () => void | Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [models, setModels] = useState<AiModel[]>([]);
  const [modelId, setModelId] = useState("");
  const [chunkChars, setChunkChars] = useState(String(DEFAULT_CHUNK_CHARS));
  const [starting, setStarting] = useState(false);

  // 解析跟踪状态：轮询由 phase === "tracking" 驱动，与提示条是否展示无关
  const [phase, setPhase] = useState<Phase>("idle");
  const [progress, setProgress] = useState("排队中");
  const [failMessage, setFailMessage] = useState("");
  const [resultCount, setResultCount] = useState(0);
  const [trackingVisible, setTrackingVisible] = useState(true);

  const onStartedRef = useRef(onStarted);
  useEffect(() => {
    onStartedRef.current = onStarted;
  });

  // 页面加载时恢复跟踪：若该剧本已有解析任务进行中，直接接续进度展示
  useEffect(() => {
    let cancelled = false;
    scriptApi
      .autoSplitStatus(scriptId)
      .then((status) => {
        if (cancelled || status.parsingStatus !== 1) return;
        setProgress(status.parsingProgress ?? "解析中…");
        setPhase("tracking");
      })
      .catch(() => {
        // 状态查询失败不阻塞页面，用户手动发起时会重新获取
      });
    return () => {
      cancelled = true;
    };
  }, [scriptId]);

  // 解析进行中轮询状态，直到完成或失败
  useEffect(() => {
    if (phase !== "tracking") return;
    let cancelled = false;
    const tick = async () => {
      try {
        const status = await scriptApi.autoSplitStatus(scriptId);
        if (cancelled) return;
        if (status.parsingStatus === 2) {
          setResultCount(status.totalEpisodes ?? 0);
          setPhase("done");
          void onStartedRef.current?.();
        } else if (status.parsingStatus === 3) {
          setFailMessage(status.parsingProgress || "解析失败，请重跑");
          setPhase("failed");
        } else if (status.parsingStatus === 1) {
          setProgress(status.parsingProgress ?? "解析中…");
        } else {
          // 任务被重置为未解析，停止跟踪
          setPhase("idle");
        }
      } catch {
        // 单次轮询失败忽略，等待下一轮
      }
    };
    void tick();
    const timer = window.setInterval(() => void tick(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [phase, scriptId]);

  useEffect(() => {
    if (!open) return;
    let active = true;
    aiModelApi
      .listByType(1)
      .then((list) => {
        if (!active) return;
        const enabled = list.filter((m) => m.status === 1);
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

  const start = useCallback(async () => {
    if (!modelId) return;
    const chars = Math.max(
      MIN_CHUNK_CHARS,
      Math.min(MAX_CHUNK_CHARS, Number(chunkChars) || DEFAULT_CHUNK_CHARS)
    );
    setStarting(true);
    try {
      await scriptApi.autoSplitNovel(scriptId, Number(modelId), chars);
      setOpen(false);
      setResultCount(0);
      setFailMessage("");
      setProgress("排队中");
      setTrackingVisible(true);
      setPhase("tracking");
      void onStartedRef.current?.();
    } catch (error) {
      toastApiError(error, "启动自动分块解析失败");
    } finally {
      setStarting(false);
    }
  }, [chunkChars, modelId, scriptId]);

  const dismissStatus = () => {
    setPhase("idle");
    setFailMessage("");
  };

  const longWarning =
    rawContentLength > 50000
      ? `剧本原文约 ${(rawContentLength / 10000).toFixed(1)} 万字，解析耗时较长；可调大每块字符数减少调用次数，解析期间请保持页面开启。`
      : null;

  return (
    <>
      <div className="flex items-center gap-2">
        {phase === "tracking" && trackingVisible && (
          <div className="flex items-center gap-1.5 rounded-lg border border-border/30 bg-background/70 px-2.5 py-1 text-xs text-muted-foreground">
            <Loader2 className="h-3.5 w-3.5 animate-spin text-primary" />
            <span className="max-w-56 truncate">{progress}</span>
            <Button
              variant="ghost"
              size="xs"
              onClick={() => setTrackingVisible(false)}
              title="解析仍在后台进行，关闭提示不会中断任务"
            >
              取消
            </Button>
          </div>
        )}
        {phase === "tracking" && !trackingVisible && (
          <button
            onClick={() => setTrackingVisible(true)}
            className="rounded-lg px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            title="查看实时解析进度"
          >
            解析仍在后台进行，点击查看进度
          </button>
        )}
        {phase === "done" && (
          <div className="flex items-center gap-1.5 rounded-lg border border-primary/30 bg-primary/5 px-2.5 py-1 text-xs text-primary">
            <Sparkles className="h-3.5 w-3.5" />
            <span>解析完成 · 共 {resultCount} 集</span>
            <Button
              variant="ghost"
              size="icon-xs"
              aria-label="关闭结果摘要"
              onClick={dismissStatus}
            >
              <X className="h-3 w-3" />
            </Button>
          </div>
        )}
        {phase === "failed" && (
          <div className="flex items-center gap-1.5 rounded-lg border border-destructive/30 bg-destructive/5 px-2.5 py-1 text-xs text-destructive">
            <CircleAlert className="h-3.5 w-3.5 shrink-0" />
            <span className="max-w-56 truncate" title={failMessage}>
              {failMessage}
            </span>
            <Button variant="outline" size="xs" onClick={() => setOpen(true)}>
              <RotateCcw className="h-3 w-3" />
              重跑
            </Button>
            <Button
              variant="ghost"
              size="icon-xs"
              aria-label="关闭失败提示"
              onClick={dismissStatus}
            >
              <X className="h-3 w-3" />
            </Button>
          </div>
        )}
        <Button variant="ai" size="sm" onClick={() => setOpen(true)}>
          <Sparkles className="h-3.5 w-3.5" />
          故事转剧本
        </Button>
      </div>

      <Dialog open={open} onOpenChange={(value) => setOpen(value)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-primary" />
              故事转剧本
            </DialogTitle>
            <DialogDescription>
              按章节/段落自动分块，逐块调用文本模型改写为结构化场次；解析将清空并重建该剧本的全部分集与场次。
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            {phase === "tracking" && (
              <div className="rounded-lg border border-border/30 bg-background/70 px-3 py-2 text-xs text-muted-foreground">
                当前已有解析任务进行中（{progress}），重新开始将覆盖其结果。
              </div>
            )}
            {longWarning && (
              <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs leading-relaxed text-amber-700 dark:text-amber-300">
                {longWarning}
              </div>
            )}
            <div className="space-y-1.5">
              <label className="text-xs text-muted-foreground" htmlFor="sts-model">
                文本模型
              </label>
              <Select
                value={modelId}
                onValueChange={(v) => setModelId(v ?? "")}
                items={models.map((m) => ({ value: String(m.id), label: m.name }))}
              >
                <SelectTrigger id="sts-model" className="w-full">
                  <SelectValue
                    placeholder={models.length ? "选择文本模型" : "暂无可用文本模型"}
                  />
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
              <label className="text-xs text-muted-foreground" htmlFor="sts-chunk-chars">
                每块字符数（{MIN_CHUNK_CHARS}-{MAX_CHUNK_CHARS}，默认 {DEFAULT_CHUNK_CHARS}）
              </label>
              <Input
                id="sts-chunk-chars"
                type="number"
                min={MIN_CHUNK_CHARS}
                max={MAX_CHUNK_CHARS}
                step={500}
                value={chunkChars}
                onChange={(e) => setChunkChars(e.target.value)}
              />
              <p className="text-xs text-muted-foreground/80">
                每块越大调用次数越少；章节边界优先，块内按段落切分，不会截断丢字。
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={starting}>
              取消
            </Button>
            <Button variant="ai" onClick={() => void start()} disabled={starting || !modelId}>
              {starting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              开始解析
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
