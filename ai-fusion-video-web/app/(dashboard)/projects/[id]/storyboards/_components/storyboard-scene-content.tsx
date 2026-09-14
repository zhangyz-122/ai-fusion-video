"use client";

import type { RefObject } from "react";
import { Camera, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { StoryboardTableView } from "./storyboard-table-view";
import { StoryboardCardView } from "./storyboard-card-view";
import type { ViewMode, SceneWithItems } from "./storyboard-utils";
import type { Asset, AssetItem } from "@/lib/api/asset";
import type { StoryboardFrameType, StoryboardItem } from "@/lib/api/storyboard";

interface StoryboardSceneContentProps {
  /** 场次滚动容器,页面级的滚动监听与定位依赖它 */
  scrollContainerRef: RefObject<HTMLDivElement | null>;
  loading: boolean;
  sceneGroups: SceneWithItems[];
  activeSceneId: number | null;
  onSelectScene: (sceneId: number) => void;
  sceneRefs: RefObject<Record<number, HTMLDivElement | null>>;
  viewMode: ViewMode;
  selectedItemId: number | null;
  onSelectItem: (itemId: number | null) => void;
  onUpdateItemField: (itemId: number, field: string, value: string | number | null) => void;
  onAddItem: (sceneId: number, episodeId?: number) => void;
  onDeleteItem: (itemId: number) => void;
  onReorderItems: (sceneId: number, reorderedItems: StoryboardItem[]) => void;
  onVideoGen: (itemId: number) => void;
  onOpenFrameDialog: (item: StoryboardItem, frameType: StoryboardFrameType) => void;
  assetLookup: Record<number, { item: AssetItem; asset: Asset }>;
  onEditAssets: (item: StoryboardItem) => void;
}

/** 分镜中栏内容区:按场次分组的滚动容器与表格/卡片视图切换 */
export function StoryboardSceneContent({
  scrollContainerRef,
  loading,
  sceneGroups,
  activeSceneId,
  onSelectScene,
  sceneRefs,
  viewMode,
  selectedItemId,
  onSelectItem,
  onUpdateItemField,
  onAddItem,
  onDeleteItem,
  onReorderItems,
  onVideoGen,
  onOpenFrameDialog,
  assetLookup,
  onEditAssets,
}: StoryboardSceneContentProps) {
  return (
    <div
      ref={scrollContainerRef}
      className="flex-1 overflow-y-auto px-3 sm:px-6 py-4 sm:py-5 space-y-8"
    >
      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      ) : sceneGroups.length === 0 ? (
        <div className="text-center py-16">
          <Camera className="h-10 w-10 text-muted-foreground/20 mx-auto mb-3" />
          <p className="text-sm text-muted-foreground">
            暂无分镜内容，请在左侧创建分集和场次，或使用顶部 AI 生成
          </p>
        </div>
      ) : (
        sceneGroups.map(({ scene, items }) => (
          <div
            key={scene.id}
            data-scene-id={scene.id}
            ref={(el) => {
              sceneRefs.current[scene.id] = el;
            }}
            className={cn(
              "scroll-mt-4 p-3 sm:p-5 rounded-2xl border transition-all duration-500 ease-out",
              activeSceneId === scene.id
                ? "bg-violet-500/1.5 border-violet-500/15 shadow-[0_2px_8px_-3px_rgba(139,92,246,0.04)] dark:bg-violet-500/0.5"
                : "border-transparent bg-transparent"
            )}
            onClick={() => onSelectScene(scene.id)}
          >
            {/* 场次标题：点击时亦可切换激活场次 */}
            <div
              className="flex flex-wrap items-center gap-2 mb-3 cursor-pointer group/title"
              onClick={() => onSelectScene(scene.id)}
            >
              <Camera className={cn(
                "h-3.5 w-3.5 transition-colors",
                activeSceneId === scene.id ? "text-violet-500" : "text-primary/60 group-hover/title:text-primary"
              )} />
              <h3 className={cn(
                "text-sm font-semibold transition-colors",
                activeSceneId === scene.id ? "text-violet-600 dark:text-violet-400" : "group-hover/title:text-primary"
              )}>
                {scene.sceneHeading ||
                  `场次 ${scene.sceneNumber || scene.id}`}
              </h3>
              {scene.location && (
                <span className="text-[10px] px-1.5 py-0.5 rounded-md bg-muted/30 text-muted-foreground">
                  {scene.intExt && `${scene.intExt} `}
                  {scene.location}
                  {scene.timeOfDay && ` ${scene.timeOfDay}`}
                </span>
              )}
              <span className="text-[10px] text-muted-foreground/50 ml-auto">
                {items.length} 镜
              </span>
            </div>

            {/* 场次内的镜头列表 */}
            {viewMode === "table" ? (
              <StoryboardTableView
                items={items}
                selectedItemId={selectedItemId}
                onSelectItem={onSelectItem}
                onUpdateItemField={onUpdateItemField}
                onAddItem={() =>
                  onAddItem(scene.id, scene.episodeId)
                }
                onDeleteItem={onDeleteItem}
                onReorderItems={(reordered) =>
                  onReorderItems(scene.id, reordered)
                }
                onVideoGen={onVideoGen}
                onOpenFrameDialog={onOpenFrameDialog}
                assetLookup={assetLookup}
                onEditAssets={onEditAssets}
              />
            ) : (
              <StoryboardCardView
                items={items}
                selectedItemId={selectedItemId}
                onSelectItem={onSelectItem}
                onAddItem={() =>
                  onAddItem(scene.id, scene.episodeId)
                }
                onDeleteItem={onDeleteItem}
                onReorderItems={(reordered) =>
                  onReorderItems(scene.id, reordered)
                }
                onVideoGen={onVideoGen}
                onOpenFrameDialog={onOpenFrameDialog}
              />
            )}
          </div>
        ))
      )}
    </div>
  );
}
