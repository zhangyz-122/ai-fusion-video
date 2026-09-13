"use client";

import { useCallback } from "react";
import { toastApiError } from "@/lib/api/toast-api-error";
import { scriptApi, type ScriptEpisode } from "@/lib/api/script";
import {
  storyboardApi,
  type Storyboard,
  type StoryboardEpisode,
  type StoryboardFrameType,
  type StoryboardItem,
} from "@/lib/api/storyboard";
import { usePipelineStore } from "@/lib/store/pipeline-store";
import { useConfirm } from "@/components/ui/confirm-dialog";
import { buildDefaultBatchFramePrompt } from "./storyboard-frame-reference-dialog";
import type { BatchFrameGeneratePayload } from "./storyboard-ref-panel";
import type { SceneWithItems } from "./storyboard-utils";

interface UseStoryboardPipelineActionsOptions {
  projectId: number;
  projectName?: string;
  storyboard: Storyboard | null;
  scriptEpisodes: ScriptEpisode[];
  sceneGroups: SceneWithItems[];
  currentEpisode: StoryboardEpisode | null;
  loadStoryboard: () => Promise<void>;
  refreshStoryboardData: () => Promise<void>;
}

/**
 * 分镜页 AI 生成类 pipeline 动作:
 * AI 生成分镜、按集生成分镜、单镜头首尾帧生成、当前场次批量首尾帧生成。
 */
export function useStoryboardPipelineActions({
  projectId,
  projectName,
  storyboard,
  scriptEpisodes,
  sceneGroups,
  currentEpisode,
  loadStoryboard,
  refreshStoryboardData,
}: UseStoryboardPipelineActionsOptions) {
  const {
    addPipeline,
    setPanelExpanded,
    setExpandedTaskId,
    setNotificationOpen,
  } = usePipelineStore();
  const { confirm, alert } = useConfirm();

  const handleAiStoryboard = useCallback(async () => {
    try {
      const currentScript = await scriptApi.getByProject(projectId);

      if (!currentScript || !storyboard) {
        await alert({ title: "工作区未初始化", description: "项目剧本或分镜工作区未初始化，请检查后重试。", variant: "warning" });
        return;
      }

      const scriptDisplayTitle =
        projectName?.trim() || "未命名项目";

      const pipelineId = addPipeline({
        label: `AI 生成分镜 - ${scriptDisplayTitle}`,
        projectId,
        request: {
          agentType: "script_to_storyboard",
          toolExecutionMode: "FULL_ACCESS",
          category: "pipeline",
          title: `AI 生成分镜：${scriptDisplayTitle}`,
          projectId,
          context: {
            scriptId: currentScript.id,
            storyboardId: storyboard.id,
          },
        },
        onComplete: async () => {
          // DONE 只代表模型结束输出；必须确认数据库已经产生分镜内容。
          let fallbackError = "";
          try {
            await storyboardApi.fallbackGenerate(storyboard.id);
          } catch (error) {
            fallbackError = error instanceof Error ? error.message : String(error);
          }
          const statistics = await storyboardApi.getStatistics(storyboard.id);
          if (
            statistics.episodeCount === 0 ||
            statistics.sceneCount === 0 ||
            statistics.itemCount === 0
          ) {
            throw new Error(
              fallbackError ||
                `分镜仍为空（分集 ${statistics.episodeCount}，场次 ${statistics.sceneCount}，镜头 ${statistics.itemCount}）`,
            );
          }
          await loadStoryboard();
        },
      });

      setPanelExpanded(true);
      setExpandedTaskId(pipelineId);
      await loadStoryboard();
    } catch (err) {
      console.error("启动分镜生成失败:", err);
      toastApiError(err, "启动分镜生成失败，请重试");
    }
  }, [
    addPipeline,
    alert,
    loadStoryboard,
    projectName,
    projectId,
    setExpandedTaskId,
    setPanelExpanded,
    storyboard,
  ]);

  const handleGenerateEpisodeStoryboard = useCallback(async (episode: StoryboardEpisode) => {
    if (!storyboard) return;
    if (!storyboard.scriptId) {
      await alert({ title: "未关联剧本", description: "当前分镜未关联剧本，无法按单集重新生成。", variant: "warning" });
      return;
    }
    if (!episode.scriptEpisodeId) {
      await alert({ title: "未绑定剧本集", description: "请先绑定剧本集后再重新生成本集分镜。", variant: "warning" });
      return;
    }

    const scriptEpisode = scriptEpisodes.find((item) => item.id === episode.scriptEpisodeId);
    const displayNumber = scriptEpisode?.episodeNumber ?? episode.episodeNumber ?? "?";
    const confirmed = await confirm({ title: "覆盖生成本集分镜", description: `将覆盖第 ${displayNumber} 集已有分镜内容，不影响其它集。确定继续？`, variant: "ai", confirmText: "确定覆盖生成" });
    if (!confirmed) return;

    try {
      await storyboardApi.clearEpisodeContent(episode.id);
      const pipelineId = addPipeline({
        label: `AI 分镜 · 第 ${displayNumber} 集`,
        projectId,
        request: {
          agentType: "episode_storyboard_writer",
          toolExecutionMode: "FULL_ACCESS",
          category: "pipeline",
          title: `AI 分镜 · 第 ${displayNumber} 集`,
          message: `请为剧本分集（scriptEpisodeId: ${episode.scriptEpisodeId}）生成分镜，并保存到分镜脚本 ${storyboard.id}。`,
          projectId,
          context: {
            scriptId: storyboard.scriptId,
            storyboardId: storyboard.id,
            scriptEpisodeId: episode.scriptEpisodeId,
          },
        },
        onComplete: () => {
          refreshStoryboardData();
        },
      });

      setPanelExpanded(true);
      setExpandedTaskId(pipelineId);
    } catch (err) {
      console.error("启动单集分镜生成失败:", err);
      toastApiError(err, "启动单集分镜生成失败，请重试");
    }
  }, [
    addPipeline,
    alert,
    confirm,
    projectId,
    refreshStoryboardData,
    scriptEpisodes,
    setExpandedTaskId,
    setPanelExpanded,
    storyboard,
  ]);

  /** 提交镜头首尾帧 AI 生成任务 */
  const handleGenerateItemFrame = useCallback(
    async (item: StoryboardItem, frameType: StoryboardFrameType, prompt: string) => {
      if (!storyboard) {
        throw new Error("缺少分镜上下文，无法生成首尾帧");
      }
      const frameLabel = frameType === "first" ? "首帧" : "尾帧";
      const shotLabel = item.shotNumber || item.autoShotNumber || String(item.id);
      try {
        setNotificationOpen(true);
        const pipelineId = addPipeline({
          label: `生成镜头 ${shotLabel} ${frameLabel}`,
          projectId,
          request: {
            agentType: "storyboard_frame_gen",
            toolExecutionMode: "FULL_ACCESS",
            category: "pipeline",
            title: `生成镜头 ${shotLabel} ${frameLabel}`,
            projectId,
            context: {
              selectedStoryboardItemIds: [item.id],
              storyboardId: storyboard.id,
              frameType,
              framePrompt: prompt,
            },
          },
          onComplete: () => {
            void refreshStoryboardData();
          },
        });
        setPanelExpanded(true);
        setExpandedTaskId(pipelineId);
      } catch (err) {
        console.error("提交镜头首尾帧生成任务失败:", err);
        throw err;
      }
    },
    [
      addPipeline,
      projectId,
      refreshStoryboardData,
      setExpandedTaskId,
      setNotificationOpen,
      setPanelExpanded,
      storyboard,
    ]
  );

  /** 批量提交当前场次的首尾帧 AI 生成任务 */
  const handleBatchGenerateSceneFrames = useCallback(
    async ({
      episodeId,
      sceneId,
      firstItemIds,
      lastItemIds,
    }: BatchFrameGeneratePayload) => {
      if (!storyboard) {
        throw new Error("缺少分镜上下文，无法生成首尾帧");
      }
      if (!episodeId || !sceneId) {
        await alert({ title: "未选择场次", description: "请先选择场次后再批量生成首尾帧。", variant: "info" });
        return;
      }
      const sceneGroup = sceneGroups.find((group) => group.scene.id === sceneId);
      if (!sceneGroup) {
        await alert({ title: "数据未加载", description: "当前场次数据未加载，请重新选择场次后再试。", variant: "warning" });
        return;
      }
      const allowedItemIds = new Set(sceneGroup.items.map((item) => item.id));
      const safeFirstItemIds = firstItemIds.filter((id) => allowedItemIds.has(id));
      const safeLastItemIds = lastItemIds.filter((id) => allowedItemIds.has(id));
      const tasks = [
        {
          frameType: "first" as const,
          itemIds: safeFirstItemIds,
          frameLabel: "首帧",
          framePrompt: buildDefaultBatchFramePrompt("first"),
        },
        {
          frameType: "last" as const,
          itemIds: safeLastItemIds,
          frameLabel: "尾帧",
          framePrompt: buildDefaultBatchFramePrompt("last"),
        },
      ].filter((task) => task.itemIds.length > 0);

      if (tasks.length === 0) {
        await alert({ title: "无需生成", description: "当前场次首尾帧已完整，无需重复生成。", variant: "info" });
        return;
      }

      const matchedEpisode = currentEpisode?.id === episodeId ? currentEpisode : null;
      const episodeLabel = matchedEpisode?.title?.trim()
        || (matchedEpisode?.episodeNumber != null
          ? `第 ${matchedEpisode.episodeNumber} 集`
          : `分镜集 ${episodeId}`);
      const sceneLabel =
        sceneGroup.scene.sceneHeading ||
        (sceneGroup.scene.sceneNumber ? `场次 ${sceneGroup.scene.sceneNumber}` : `场次 ${sceneId}`);
      let firstPipelineId: string | null = null;

      for (const task of tasks) {
        const title = `批量生成${episodeLabel} ${sceneLabel}${task.frameLabel}`;
        const pipelineId = addPipeline({
          label: `${title} (${task.itemIds.length} 个镜头)`,
          projectId,
          request: {
            agentType: "storyboard_frame_gen",
            toolExecutionMode: "FULL_ACCESS",
            category: "pipeline",
            title,
            projectId,
            context: {
              selectedStoryboardItemIds: task.itemIds,
              storyboardId: storyboard.id,
              frameType: task.frameType,
              framePrompt: task.framePrompt,
            },
          },
          onComplete: () => {
            void refreshStoryboardData();
          },
        });
        firstPipelineId = firstPipelineId || pipelineId;
      }

      setNotificationOpen(true);
      setPanelExpanded(true);
      if (firstPipelineId) {
        setExpandedTaskId(firstPipelineId);
      }
    },
    [
      addPipeline,
      alert,
      currentEpisode,
      projectId,
      refreshStoryboardData,
      sceneGroups,
      setExpandedTaskId,
      setNotificationOpen,
      setPanelExpanded,
      storyboard,
    ]
  );

  return {
    handleAiStoryboard,
    handleGenerateEpisodeStoryboard,
    handleGenerateItemFrame,
    handleBatchGenerateSceneFrames,
  };
}
