"use client";

import { type RefObject, useCallback, useEffect, useRef, useState } from "react";
import { toastApiError } from "@/lib/api/toast-api-error";
import { scriptApi, type ScriptEpisode } from "@/lib/api/script";
import { assetApi, type Asset, type AssetItem, type AssetWithItems } from "@/lib/api/asset";
import {
  storyboardApi,
  type Storyboard,
  type StoryboardEpisode,
  type StoryboardScene,
} from "@/lib/api/storyboard";
import { usePipelineStore } from "@/lib/store/pipeline-store";
import { deriveCurrentEpisodeId } from "./storyboard-utils";
import type { SceneWithItems, SidebarSelection } from "./storyboard-utils";

interface UseStoryboardDataOptions {
  projectId: number;
  sidebarSelection: SidebarSelection;
  /** 与 sidebarSelection 保持同步的 ref,供完整刷新时读取最新选择 */
  sidebarSelectionRef: RefObject<SidebarSelection>;
}

/**
 * 分镜页数据加载:分镜/剧本集/场次分组/项目资产加载,
 * 完整刷新、pipeline 失效联动与本集合成状态编排。
 */
export function useStoryboardData({
  projectId,
  sidebarSelection,
  sidebarSelectionRef,
}: UseStoryboardDataOptions) {
  const { attachTaskStream, setNotificationOpen } = usePipelineStore();

  const [loading, setLoading] = useState(true);
  const [isRefreshingStoryboard, setIsRefreshingStoryboard] = useState(false);
  const [storyboard, setStoryboard] = useState<Storyboard | null>(null);
  const [scriptEpisodes, setScriptEpisodes] = useState<ScriptEpisode[]>([]);

  // 关联资产状态
  const [assetsList, setAssetsList] = useState<AssetWithItems[]>([]);
  const [assetLookup, setAssetLookup] = useState<Record<number, { item: AssetItem; asset: Asset }>>({});

  // 按场次分组数据
  const [sceneGroups, setSceneGroups] = useState<SceneWithItems[]>([]);
  const [loadingScenes, setLoadingScenes] = useState(false);

  // 当前选中集的合成状态
  const [currentEpisode, setCurrentEpisode] = useState<StoryboardEpisode | null>(null);

  // 当前选中的 episodeId（episode 或 scene 选择都会有）
  const currentEpisodeId = deriveCurrentEpisodeId(sidebarSelection);

  const [composedPreviewUrl, setComposedPreviewUrl] = useState<string | null>(null);
  const [runningComposeEpisodeIds, setRunningComposeEpisodeIds] = useState<number[]>([]);
  const [submittingComposeEpisodeIds, setSubmittingComposeEpisodeIds] = useState<number[]>([]);

  const loadProjectAssets = useCallback(async () => {
    try {
      const list = await assetApi.listWithItems(projectId);
      setAssetsList(list);

      const lookup: Record<number, { item: AssetItem; asset: Asset }> = {};
      list.forEach((asset) => {
        if (asset.items && Array.isArray(asset.items)) {
          asset.items.forEach((item) => {
            lookup[item.id] = { item, asset };
          });
        }
      });
      setAssetLookup(lookup);
    } catch (err) {
      console.error("加载资产失败:", err);
      toastApiError(err, "加载资产失败");
    }
  }, [projectId]);

  useEffect(() => {
    loadProjectAssets();
  }, [loadProjectAssets]);

  // 拉取当前集详情（含合成状态）
  const refreshCurrentEpisode = useCallback(async () => {
    if (!currentEpisodeId) {
      setCurrentEpisode(null);
      return;
    }
    try {
      const ep = await storyboardApi.getEpisode(currentEpisodeId);
      setCurrentEpisode(ep);
    } catch (err) {
      console.error("加载集详情失败:", err);
      toastApiError(err, "加载集详情失败");
    }
  }, [currentEpisodeId]);

  // 加载分镜
  const loadStoryboard = useCallback(async () => {
    try {
      setLoading(true);
      const activeStoryboard = await storyboardApi.getByProject(projectId);
      if (activeStoryboard) {
        setStoryboard(activeStoryboard);
        if (activeStoryboard.scriptId) {
          const episodes = await scriptApi.listEpisodes(activeStoryboard.scriptId);
          setScriptEpisodes(episodes);
        } else {
          setScriptEpisodes([]);
        }
      } else {
        setStoryboard(null);
        setScriptEpisodes([]);
        setSceneGroups([]);
      }
    } catch (err) {
      console.error("加载分镜失败:", err);
      toastApiError(err, "加载分镜失败");
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadStoryboard();
  }, [loadStoryboard]);

  const refreshStoryboardData = useCallback(async () => {
    try {
      void loadProjectAssets();
      void refreshCurrentEpisode();

      const loadedStoryboard = await storyboardApi.getByProject(projectId);
      let activeStoryboard = storyboard;
      if (loadedStoryboard) {
        activeStoryboard = loadedStoryboard;
        setStoryboard(loadedStoryboard);
        if (activeStoryboard.scriptId) {
          const episodes = await scriptApi.listEpisodes(activeStoryboard.scriptId);
          setScriptEpisodes(episodes);
        } else {
          setScriptEpisodes([]);
        }
      } else {
        setStoryboard(null);
        setScriptEpisodes([]);
        setSceneGroups([]);
        return;
      }

      const selection = sidebarSelectionRef.current;
      let scenes: StoryboardScene[];
      if (selection.type === "episode" || selection.type === "scene") {
        if (selection.episodeId) {
          scenes = await storyboardApi.listScenesByEpisode(selection.episodeId);
        } else {
          scenes = [];
        }
      } else {
        scenes = await storyboardApi.listScenesByStoryboard(activeStoryboard.id);
      }

      const groups = await Promise.all(
        scenes.map(async (scene) => {
          const items = await storyboardApi.listItemsByScene(scene.id);
          return { scene, items };
        })
      );
      setSceneGroups(groups);
    } catch (err) {
      console.error("完整刷新分镜页数据失败:", err);
      toastApiError(err, "刷新分镜页数据失败");
    }
  }, [projectId, storyboard, loadProjectAssets, refreshCurrentEpisode, sidebarSelectionRef]);

  const handleManualRefreshStoryboard = useCallback(async () => {
    setIsRefreshingStoryboard(true);
    try {
      await refreshStoryboardData();
    } finally {
      setIsRefreshingStoryboard(false);
    }
  }, [refreshStoryboardData]);

  // 加载场次分组数据
  const loadSceneGroups = useCallback(
    async (episodeId?: number) => {
      if (!storyboard) return;
      setLoadingScenes(true);
      try {
        let scenes: StoryboardScene[];
        if (episodeId) {
          scenes = await storyboardApi.listScenesByEpisode(episodeId);
        } else {
          scenes = await storyboardApi.listScenesByStoryboard(storyboard.id);
        }

        // 并行加载每个场次的条目
        const groups = await Promise.all(
          scenes.map(async (scene) => {
            const items = await storyboardApi.listItemsByScene(scene.id);
            return { scene, items };
          })
        );

        setSceneGroups(groups);
      } catch (err) {
        console.error("加载场次失败:", err);
        toastApiError(err, "加载场次失败");
      } finally {
        setLoadingScenes(false);
      }
    },
    [storyboard]
  );

  // AI 工具执行后自动刷新
  const storyboardsInvalidation = usePipelineStore((s) => s.invalidation.storyboards);
  const storyboardsInvRef = useRef(storyboardsInvalidation);
  useEffect(() => {
    if (storyboardsInvRef.current !== storyboardsInvalidation) {
      storyboardsInvRef.current = storyboardsInvalidation;
      refreshStoryboardData();
    }
  }, [storyboardsInvalidation, refreshStoryboardData]);

  /** 提交本集合成视频任务 */
  const handleComposeEpisodeVideo = useCallback(async () => {
    if (!currentEpisodeId || !currentEpisode) return;
    if (
      submittingComposeEpisodeIds.includes(currentEpisodeId) ||
      runningComposeEpisodeIds.includes(currentEpisodeId) ||
      currentEpisode.composeStatus === 1
    ) {
      return;
    }

    const epLabel = currentEpisode.title?.trim()
      || (currentEpisode.episodeNumber != null ? `第 ${currentEpisode.episodeNumber} 集` : `集 ${currentEpisode.id}`);
    setSubmittingComposeEpisodeIds((prev) =>
      prev.includes(currentEpisodeId) ? prev : [...prev, currentEpisodeId]
    );
    setNotificationOpen(true);
    try {
      const taskId = await storyboardApi.composeEpisodeVideo(currentEpisodeId);
      setSubmittingComposeEpisodeIds((prev) =>
        prev.filter((id) => id !== currentEpisodeId)
      );
      setRunningComposeEpisodeIds((prev) =>
        prev.includes(currentEpisodeId) ? prev : [...prev, currentEpisodeId]
      );

      attachTaskStream({
        label: `合成本集视频：${epLabel}`,
        projectId,
        taskId,
        cancellable: false,
        onSettled: () => {
          setRunningComposeEpisodeIds((prev) =>
            prev.filter((id) => id !== currentEpisodeId)
          );
          void refreshCurrentEpisode();
        },
      });

      void refreshCurrentEpisode();
    } catch (err) {
      console.error("提交合成任务失败:", err);
      toastApiError(err, "提交合成任务失败");
      setSubmittingComposeEpisodeIds((prev) =>
        prev.filter((id) => id !== currentEpisodeId)
      );
      setRunningComposeEpisodeIds((prev) =>
        prev.filter((id) => id !== currentEpisodeId)
      );
    }
  }, [
    currentEpisodeId,
    currentEpisode,
    submittingComposeEpisodeIds,
    runningComposeEpisodeIds,
    projectId,
    attachTaskStream,
    setNotificationOpen,
    refreshCurrentEpisode,
  ]);

  useEffect(() => {
    refreshCurrentEpisode();
  }, [refreshCurrentEpisode]);

  return {
    loading,
    storyboard,
    scriptEpisodes,
    assetsList,
    assetLookup,
    sceneGroups,
    setSceneGroups,
    loadingScenes,
    currentEpisode,
    currentEpisodeId,
    composedPreviewUrl,
    setComposedPreviewUrl,
    runningComposeEpisodeIds,
    submittingComposeEpisodeIds,
    handleComposeEpisodeVideo,
    isRefreshingStoryboard,
    handleManualRefreshStoryboard,
    loadStoryboard,
    loadSceneGroups,
    refreshCurrentEpisode,
    refreshStoryboardData,
  };
}
