"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useConfirm } from "@/components/ui/confirm-dialog";
import { VideoPreviewDialog } from "@/components/dashboard/video-preview-dialog";
import { motion } from "framer-motion";
import { toastApiError } from "@/lib/api/toast-api-error";
import {
  storyboardApi,
  type StoryboardFrameType,
  type StoryboardItem,
  type StoryboardScene,
} from "@/lib/api/storyboard";
import { usePipelineStore } from "@/lib/store/pipeline-store";
import { useProject } from "../project-context";
import { StoryboardSidebar } from "./_components/storyboard-sidebar";
import { StoryboardRefPanel } from "./_components/storyboard-ref-panel";
import { StoryboardFrameReferenceDialog } from "./_components/storyboard-frame-reference-dialog";
import { ProductionTakeDrawer } from "./_components/production-take-drawer";
import { EditItemAssetsDialog } from "./_components/edit-assets-dialog";
import { StoryboardToolbar } from "./_components/storyboard-toolbar";
import { StoryboardSceneContent } from "./_components/storyboard-scene-content";
import { StoryboardAiConfirmDialog } from "./_components/storyboard-ai-confirm-dialog";
import { useStoryboardData } from "./_components/use-storyboard-data";
import { useStoryboardPipelineActions } from "./_components/use-storyboard-pipeline";
import {
  readStoredSidebarCollapsed,
  readStoredViewMode,
  resolveActiveSceneIdOnScroll,
  writeStoredSidebarCollapsed,
  writeStoredViewMode,
} from "./_components/storyboard-utils";
import type { SidebarSelection, ViewMode } from "./_components/storyboard-utils";

export default function StoryboardTabPage() {
  const params = useParams();
  const projectId = Number(params.id);
  const { project } = useProject();
  const { confirm } = useConfirm();
  const { attachTaskStream, setNotificationOpen } = usePipelineStore();

  // 视图状态
  const [viewMode, setViewMode] = useState<ViewMode>("table");
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);

  // 加载本地用户偏好
  useEffect(() => {
    const savedMode = readStoredViewMode();
    if (savedMode) {
      // 挂载后读取本地偏好再回填,避免 SSR 水合不一致(原始行为,保持不变)
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setViewMode(savedMode);
    }
    setIsSidebarCollapsed(readStoredSidebarCollapsed());
  }, []);

  const handleSetViewMode = useCallback((mode: ViewMode) => {
    setViewMode(mode);
    writeStoredViewMode(mode);
  }, []);
  const handleSetSidebarCollapsed = useCallback((collapsed: boolean) => {
    setIsSidebarCollapsed(collapsed);
    writeStoredSidebarCollapsed(collapsed);
  }, []);

  const [sidebarSelection, setSidebarSelection] = useState<SidebarSelection>({
    type: "episode",
  });
  const sidebarSelectionRef = useRef(sidebarSelection);
  useEffect(() => {
    sidebarSelectionRef.current = sidebarSelection;
  }, [sidebarSelection]);

  // 移动端侧边栏状态
  const [leftSheetOpen, setLeftSheetOpen] = useState(false);
  const [rightSheetOpen, setRightSheetOpen] = useState(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia("(min-width: 1024px)");
    const handleResize = (e: MediaQueryListEvent | MediaQueryList) => {
      if (e.matches) {
        setLeftSheetOpen(false);
        setRightSheetOpen(false);
      }
    };
    handleResize(mediaQuery);
    mediaQuery.addEventListener("change", handleResize);
    return () => mediaQuery.removeEventListener("change", handleResize);
  }, []);

  // ========== 数据加载(加载/刷新/失效/本集合成状态) ==========

  const {
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
  } = useStoryboardData({ projectId, sidebarSelection, sidebarSelectionRef });

  // ========== AI 生成与首尾帧 pipeline 动作 ==========

  const {
    handleAiStoryboard,
    handleGenerateEpisodeStoryboard,
    handleGenerateItemFrame,
    handleBatchGenerateSceneFrames,
  } = useStoryboardPipelineActions({
    projectId,
    projectName: project?.name,
    storyboard,
    scriptEpisodes,
    sceneGroups,
    currentEpisode,
    loadStoryboard,
    refreshStoryboardData,
  });

  const [showAiConfirmDialog, setShowAiConfirmDialog] = useState(false);
  const [selectedItemId, setSelectedItemId] = useState<number | null>(null);
  const [productionItem, setProductionItem] = useState<StoryboardItem | null>(null);
  const [frameDialogItemId, setFrameDialogItemId] = useState<number | null>(null);
  const [frameDialogInitialType, setFrameDialogInitialType] =
    useState<StoryboardFrameType>("first");

  // 关联资产编辑弹窗
  const [editAssetsOpen, setEditAssetsOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<StoryboardItem | null>(null);
  const handleEditAssets = useCallback((item: StoryboardItem) => {
    setEditingItem(item);
    setEditAssetsOpen(true);
  }, []);

  // 滚动定位 refs
  const sceneRefs = useRef<Record<number, HTMLDivElement | null>>({});
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  // 滚动时当前可见的场次 ID（用于侧边栏高亮）
  const [activeSceneId, setActiveSceneId] = useState<number | null>(null);
  // 标记是否由用户点击触发的滚动（此时不要通过 observer 覆盖）
  const isUserScrollRef = useRef(false);

  // ========== 派生数据 ==========

  const allItems = sceneGroups.flatMap((g) => g.items);
  const selectedItem = selectedItemId
    ? allItems.find((i) => i.id === selectedItemId) || null
    : null;
  const frameDialogItem = frameDialogItemId
    ? allItems.find((i) => i.id === frameDialogItemId) || null
    : null;

  // 当前激活场次的分组（用于右侧面板展示场次资产）
  const activeSceneGroup = activeSceneId
    ? sceneGroups.find((g) => g.scene.id === activeSceneId) || null
    : null;

  // 处理镜头选择，同时静默同步定位该镜头所属的场次
  const handleSelectItem = useCallback((itemId: number | null) => {
    setSelectedItemId(itemId);
    if (itemId) {
      const group = sceneGroups.find((g) => g.items.some((item) => item.id === itemId));
      if (group) {
        setActiveSceneId(group.scene.id);
      }
    }
  }, [sceneGroups]);

  // 追踪当前已加载的集ID，避免同集内切换场次重复加载
  const loadedEpisodeIdRef = useRef<number | null>(null);

  // sidebar 初始化完成后通知 page 第一集 episodeId
  const handleSidebarInitialLoad = useCallback((firstEpisodeId: number) => {
    setSidebarSelection({ type: "episode", episodeId: firstEpisodeId });
  }, []);

  // 滚动到指定场次
  const scrollToScene = (sceneId: number) => {
    isUserScrollRef.current = true;
    setActiveSceneId(sceneId);
    const el = sceneRefs.current[sceneId];
    if (el && scrollContainerRef.current) {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    }
    // 滚动动画完成后恢复 observer
    setTimeout(() => {
      isUserScrollRef.current = false;
    }, 600);
  };

  // 当侧边栏选择变化时加载数据
  useEffect(() => {
    if (!storyboard) return;

    if (sidebarSelection.type === "all") {
      // 切换选择时重置激活场次(原始行为,保持不变)
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setActiveSceneId(null);
      loadedEpisodeIdRef.current = null;
      loadSceneGroups();
    } else if (sidebarSelection.type === "episode" && sidebarSelection.episodeId) {
      setActiveSceneId(null);
      loadedEpisodeIdRef.current = sidebarSelection.episodeId;
      loadSceneGroups(sidebarSelection.episodeId);
    } else if (
      sidebarSelection.type === "scene" &&
      sidebarSelection.sceneId
    ) {
      const sceneExists = sceneGroups.some(
        (g) => g.scene.id === sidebarSelection.sceneId
      );
      // 同一集且场次已存在于数据中：直接滚动
      if (
        sidebarSelection.episodeId &&
        loadedEpisodeIdRef.current === sidebarSelection.episodeId &&
        sceneExists
      ) {
        setTimeout(() => {
          scrollToScene(sidebarSelection.sceneId!);
        }, 50);
      } else if (sidebarSelection.episodeId) {
        // 不同集 / 首次加载 / 新添加的场次：重新加载数据再滚动
        loadedEpisodeIdRef.current = sidebarSelection.episodeId;
        loadSceneGroups(sidebarSelection.episodeId).then(() => {
          setTimeout(() => {
            scrollToScene(sidebarSelection.sceneId!);
          }, 100);
        });
      }
    }
  }, [sidebarSelection, storyboard, loadSceneGroups]);

  // ========== 滚动监听：更新当前可视场次 ==========
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container || sceneGroups.length === 0) return;

    let ticking = false;

    const handleScroll = () => {
      if (isUserScrollRef.current) return;

      if (!ticking) {
        window.requestAnimationFrame(() => {
          const nextSceneId = resolveActiveSceneIdOnScroll(
            container,
            sceneGroups,
            sceneRefs.current
          );
          if (nextSceneId !== null) {
            setActiveSceneId(nextSceneId);
          }
          ticking = false;
        });

        ticking = true;
      }
    };

    container.addEventListener("scroll", handleScroll, { passive: true });
    // 初始化执行一次
    handleScroll();

    return () => {
      container.removeEventListener("scroll", handleScroll);
    };
  }, [sceneGroups]);

  // ========== 操作 ==========

  const handleAddItem = async (sceneId: number, episodeId?: number) => {
    if (!storyboard) return;
    const group = sceneGroups.find((g) => g.scene.id === sceneId);
    const currentItems = group?.items || [];
    try {
      const newItem = await storyboardApi.createItem({
        storyboardId: storyboard.id,
        storyboardSceneId: sceneId,
        storyboardEpisodeId: episodeId,
        sortOrder: currentItems.length,
        shotNumber: String(currentItems.length + 1),
      });
      setSceneGroups((prev) =>
        prev.map((g) =>
          g.scene.id === sceneId
            ? { ...g, items: [...g.items, newItem] }
            : g
        )
      );
      setSelectedItemId(newItem.id);
    } catch (err) {
      console.error("添加新分镜条目失败:", err);
      toastApiError(err, "添加新分镜条目失败");
    }
  };

  const handleDeleteEpisode = async (episodeId: number) => {
    const ok = await confirm({ title: "删除分镜集", description: "确定要删除该分镜集吗？相关的分镜内容也将被一并删除，此操作无法撤销。", variant: "destructive", confirmText: "确定删除" }); if (!ok) return false;
    try {
      await storyboardApi.deleteEpisode(episodeId);
      if (
        sidebarSelection.episodeId === episodeId
      ) {
        setSidebarSelection({ type: "all" });
      }
      loadStoryboard();
      return true;
    } catch (err) {
      console.error("删除分集失败:", err);
      toastApiError(err, "删除分集失败");
      return false;
    }
  };

  const handleDeleteScene = async (sceneId: number, episodeId: number) => {
    const ok = await confirm({ title: "删除分镜场次", description: "确定要删除该分镜场次吗？场次内的所有镜头将被一并删除。", variant: "destructive", confirmText: "确定删除" }); if (!ok) return false;
    try {
      await storyboardApi.deleteScene(sceneId);
      if (sidebarSelection.sceneId === sceneId) {
        setSidebarSelection({ type: "episode", episodeId });
      }
      loadSceneGroups(episodeId);
      return true;
    } catch (err) {
      console.error("删除分镜头报错", err);
      toastApiError(err, "删除分镜头失败");
      return false;
    }
  };

  const handleReorderScenes = async (episodeId: number, sortedScenes: StoryboardScene[]) => {
    try {
      await Promise.all(
        sortedScenes.map((scene, idx) =>
          storyboardApi.updateScene({ id: scene.id, sortOrder: idx })
        )
      );
      if (
        sidebarSelection.type === "all" ||
        sidebarSelection.episodeId === episodeId
      ) {
        loadSceneGroups(sidebarSelection.type === "all" ? undefined : episodeId);
      }
    } catch (err) {
      console.error("更新排序失败", err);
      throw err;
    }
  };

  const handleDeleteItem = async (itemId: number) => {
    const ok = await confirm({ title: "删除镜头", description: "确定要删除该镜头吗？此操作无法撤销。", variant: "destructive", confirmText: "确定删除" }); if (!ok) return;
    try {
      await storyboardApi.deleteItem(itemId);
      setSceneGroups((prev) =>
        prev.map((g) => ({
          ...g,
          items: g.items.filter((i) => i.id !== itemId),
        }))
      );
      if (selectedItemId === itemId) setSelectedItemId(null);
      if (frameDialogItemId === itemId) setFrameDialogItemId(null);
    } catch (err) {
      console.error("删除条目失败:", err);
      toastApiError(err, "删除条目失败");
    }
  };

  const handleUpdateItemField = async (
    itemId: number,
    field: string,
    value: string | number | null
  ) => {
    try {
      const updated = await storyboardApi.updateItem({
        id: itemId,
        [field]: field === "duration" ? (value ? Number(value) : null) : value,
      });
      setSceneGroups((prev) =>
        prev.map((g) => ({
          ...g,
          items: g.items.map((i) => (i.id === updated.id ? updated : i)),
        }))
      );
    } catch (err) {
      console.error("更新条目失败:", err);
      toastApiError(err, "更新条目失败");
    }
  };

  const updateItemInSceneGroups = useCallback((updated: StoryboardItem) => {
    setSceneGroups((prev) =>
      prev.map((g) => ({
        ...g,
        items: g.items.map((i) => (i.id === updated.id ? updated : i)),
      }))
    );
  }, [setSceneGroups]);

  /** 手动更新镜头首尾帧 */
  const handleUpdateItemFrame = useCallback(
    async (itemId: number, frameType: StoryboardFrameType, imageUrl: string | null) => {
      try {
        const updated = await storyboardApi.updateFrame(itemId, {
          frameType,
          imageUrl,
          prompt: null,
        });
        updateItemInSceneGroups(updated);
      } catch (err) {
        console.error("更新镜头首尾帧失败:", err);
        throw err;
      }
    },
    [updateItemInSceneGroups]
  );

  /** 打开单个镜头首尾帧编辑弹窗 */
  const handleOpenFrameDialog = useCallback(
    (item: StoryboardItem, frameType: StoryboardFrameType) => {
      setSelectedItemId(item.id);
      setFrameDialogItemId(item.id);
      setFrameDialogInitialType(frameType);
    },
    []
  );

  /** 关闭单个镜头首尾帧编辑弹窗 */
  const handleCloseFrameDialog = useCallback(() => {
    setFrameDialogItemId(null);
  }, []);

  // 拖拽排序
  const handleReorderItems = async (
    sceneId: number,
    reorderedItems: StoryboardItem[]
  ) => {
    // 乐观更新本地状态
    setSceneGroups((prev) =>
      prev.map((g) =>
        g.scene.id === sceneId ? { ...g, items: reorderedItems } : g
      )
    );
    // 后台批量更新 sortOrder
    try {
      await storyboardApi.batchUpdateItemSort(
        reorderedItems.map((item) => item.id)
      );
    } catch (err) {
      console.error("更新排序失败:", err);
      toastApiError(err, "更新排序失败");
    }
  };

  /** 单个镜头生成视频 */
  const handleVideoGen = useCallback(
    (itemId: number) => {
      const item = sceneGroups
        .flatMap((group) => group.items)
        .find((candidate) => candidate.id === itemId);
      if (!item) return;
      setSelectedItemId(item.id);
      setProductionItem(item);
    },
    [sceneGroups]
  );

  const handleProductionComposeSubmitted = useCallback(
    (taskId: string) => {
      if (!productionItem) return;
      const shotLabel = productionItem.shotNumber || productionItem.autoShotNumber || productionItem.id;
      setNotificationOpen(true);
      attachTaskStream({
        label: `合成镜头 ${shotLabel} 所在分集`,
        projectId,
        taskId,
        cancellable: false,
        onSettled: () => {
          void refreshCurrentEpisode();
        },
      });
    },
    [attachTaskStream, projectId, productionItem, refreshCurrentEpisode, setNotificationOpen]
  );

  const handleBindScriptEpisode = useCallback(async (
    storyboardEpisodeId: number,
    scriptEpisodeId: number
  ) => {
    const updated = await storyboardApi.bindScriptEpisode(storyboardEpisodeId, scriptEpisodeId);
    await refreshStoryboardData();
    return updated;
  }, [refreshStoryboardData]);

  // ========== 渲染 ==========

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  // 固定容器缺失属于项目初始化异常，不提供运行时创建入口
  if (!storyboard) {
    return (
      <div className="rounded-xl border border-destructive/30 bg-card p-6">
        <p className="font-medium">项目分镜工作区未初始化</p>
        <p className="mt-1 text-sm text-muted-foreground">请检查项目创建流程后重试。</p>
      </div>
    );
  }

  // 有分镜：三栏布局
  const sidebarProps = {
    onRefresh: handleManualRefreshStoryboard,
    isRefreshing: isRefreshingStoryboard,
    storyboardId: storyboard.id,
    selection: sidebarSelection,
    activeSceneId: activeSceneId,
    onSelect: setSidebarSelection,
    onInitialLoad: handleSidebarInitialLoad,
    onDeleteEpisode: handleDeleteEpisode,
    onDeleteScene: handleDeleteScene,
    onReorderScenes: handleReorderScenes,
    scriptEpisodes: scriptEpisodes,
    onBindScriptEpisode: handleBindScriptEpisode,
    onGenerateEpisodeStoryboard: handleGenerateEpisodeStoryboard,
  };

  return (
    <motion.div
      variants={{ hidden: { opacity: 0 }, visible: { opacity: 1, transition: { staggerChildren: 0.08, delayChildren: 0.1 } } }}
      initial="hidden"
      animate="visible"
      className="flex min-h-0 w-full flex-1 rounded-xl border border-border/20 overflow-hidden bg-card/10"
    >
      {/* 左栏：分镜目录 */}
      <motion.div variants={{ hidden: { opacity: 0, y: 16 }, visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.25, 0.46, 0.45, 0.94] } } }} className="shrink-0 hidden xl:block">
      <StoryboardSidebar
        {...sidebarProps}
        collapsed={isSidebarCollapsed}
        onCollapsedChange={handleSetSidebarCollapsed}
      />

      <StoryboardAiConfirmDialog
        open={showAiConfirmDialog}
        onOpenChange={setShowAiConfirmDialog}
        hasScenes={sceneGroups.length > 0}
        onStart={() => {
          setShowAiConfirmDialog(false);
          void handleAiStoryboard();
        }}
      />
    </motion.div>

      {/* 中栏：按场次分组的分镜内容 */}
      <motion.div variants={{ hidden: { opacity: 0, y: 16 }, visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.25, 0.46, 0.45, 0.94] } } }} className="flex-1 flex flex-col min-w-0">
        {/* 工具栏 */}
        <StoryboardToolbar
          projectName={project?.name}
          sceneCount={sceneGroups.length}
          itemCount={allItems.length}
          aiButtonLabel={sceneGroups.length > 0 ? "AI 补全" : "AI 生成"}
          onOpenAiDialog={() => setShowAiConfirmDialog(true)}
          leftSheetOpen={leftSheetOpen}
          onLeftSheetOpenChange={setLeftSheetOpen}
          mobileSidebar={<StoryboardSidebar {...sidebarProps} />}
          currentEpisodeId={currentEpisodeId}
          currentEpisode={currentEpisode}
          submittingComposeEpisodeIds={submittingComposeEpisodeIds}
          runningComposeEpisodeIds={runningComposeEpisodeIds}
          onComposeEpisode={handleComposeEpisodeVideo}
          onPreviewComposedVideo={setComposedPreviewUrl}
          viewMode={viewMode}
          onViewModeChange={handleSetViewMode}
          rightSheetOpen={rightSheetOpen}
          onRightSheetOpenChange={setRightSheetOpen}
          refPanel={
            <StoryboardRefPanel
              storyboard={storyboard}
              items={allItems}
              selectedItem={selectedItem}
              activeSceneGroup={activeSceneGroup}
              projectId={projectId}
              project={project}
              assetLookup={assetLookup}
              onUpdateFrame={handleUpdateItemFrame}
              onGenerateFrame={handleGenerateItemFrame}
              onBatchGenerateFrames={handleBatchGenerateSceneFrames}
              onEditAssets={handleEditAssets}
            />
          }
        />

        {/* 内容区域 - 按场次滚动 */}
        <StoryboardSceneContent
          scrollContainerRef={scrollContainerRef}
          loading={loadingScenes}
          sceneGroups={sceneGroups}
          activeSceneId={activeSceneId}
          onSelectScene={setActiveSceneId}
          sceneRefs={sceneRefs}
          viewMode={viewMode}
          selectedItemId={selectedItemId}
          onSelectItem={handleSelectItem}
          onUpdateItemField={handleUpdateItemField}
          onAddItem={handleAddItem}
          onDeleteItem={handleDeleteItem}
          onReorderItems={handleReorderItems}
          onVideoGen={handleVideoGen}
          onOpenFrameDialog={handleOpenFrameDialog}
          assetLookup={assetLookup}
          onEditAssets={handleEditAssets}
        />
      </motion.div>

      {/* 右栏：引用信息 */}
      <motion.div variants={{ hidden: { opacity: 0, y: 16 }, visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.25, 0.46, 0.45, 0.94] } } }} className="shrink-0 hidden 2xl:block">
      <StoryboardRefPanel
        storyboard={storyboard}
        items={allItems}
        selectedItem={selectedItem}
        activeSceneGroup={activeSceneGroup}
        projectId={projectId}
        project={project}
        assetLookup={assetLookup}
        onUpdateFrame={handleUpdateItemFrame}
        onGenerateFrame={handleGenerateItemFrame}
        onBatchGenerateFrames={handleBatchGenerateSceneFrames}
        onEditAssets={handleEditAssets}
      />
      </motion.div>

        <VideoPreviewDialog
          open={!!composedPreviewUrl}
          title="本集合成视频"
          videoUrl={composedPreviewUrl}
          onClose={() => setComposedPreviewUrl(null)}
        />

        <ProductionTakeDrawer
          open={!!productionItem}
          item={productionItem}
          onOpenChange={(open) => {
            if (!open) setProductionItem(null);
          }}
          onComposeSubmitted={handleProductionComposeSubmitted}
        />

      <StoryboardFrameReferenceDialog
        key={`${frameDialogItemId ?? "closed"}-${frameDialogInitialType}`}
        open={frameDialogItemId !== null}
        item={frameDialogItem}
        project={project}
        initialFrameType={frameDialogInitialType}
        onClose={handleCloseFrameDialog}
        onUpdateFrame={handleUpdateItemFrame}
        onGenerateFrame={handleGenerateItemFrame}
      />

      <EditItemAssetsDialog
        open={editAssetsOpen}
        item={editingItem}
        assetsList={assetsList}
        onClose={() => {
          setEditAssetsOpen(false);
          setEditingItem(null);
        }}
        onConfirm={async ({ characterIds, sceneAssetItemId, propIds }) => {
          if (!editingItem) return;
          try {
            const updated = await storyboardApi.updateItemAssets(editingItem.id, {
              characterIds,
              sceneAssetItemId,
              propIds,
            });
            // 局部更新场次数据状态
            setSceneGroups((prev) =>
              prev.map((g) => ({
                ...g,
                items: g.items.map((i) => (i.id === updated.id ? updated : i)),
              }))
            );
            setEditAssetsOpen(false);
            setEditingItem(null);
          } catch (err) {
            console.error("更新关联资产失败:", err);
            toastApiError(err, "保存资产关联失败，请重试");
          }
        }}
      />
    </motion.div>
  );
}
