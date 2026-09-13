"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, Clapperboard, Layers } from "lucide-react";
import { OverlayScrollArea } from "@/components/dashboard/overlay-scroll-area";
import { cn } from "@/lib/utils";
import type { EditingEpisodeTree, ShotFilter } from "./editing-types";
import { EpisodeSubtitleExportButton } from "./episode-subtitle-export-button";

interface EditingSidebarProps {
  tree: EditingEpisodeTree[];
  totalShots: number;
  filter: ShotFilter;
  onFilterChange: (filter: ShotFilter) => void;
  /** 点击镜头叶子：定位到对应镜头卡片 */
  onShotSelect?: (episodeId: number, sceneId: number, itemId: number) => void;
  /** 当前定位的镜头 ID（用于叶子高亮） */
  focusedShotId?: number | null;
}

export function EditingSidebar({
  tree,
  totalShots,
  filter,
  onFilterChange,
  onShotSelect,
  focusedShotId,
}: EditingSidebarProps) {
  // 折叠的分集 ID 集合，默认全部展开
  const [collapsedIds, setCollapsedIds] = useState<Set<number>>(new Set());

  const toggleEpisode = (episodeId: number) => {
    setCollapsedIds((prev) => {
      const next = new Set(prev);
      if (next.has(episodeId)) {
        next.delete(episodeId);
      } else {
        next.add(episodeId);
      }
      return next;
    });
  };

  const handleEpisodeSelect = (episodeId: number) => {
    onFilterChange({ type: "episode", episodeId });
    toggleEpisode(episodeId);
  };

  const isEpisodeActive = (episodeId: number) =>
    filter.type === "episode" && filter.episodeId === episodeId;
  const isSceneActive = (sceneId: number) =>
    filter.type === "scene" && filter.sceneId === sceneId;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex items-center gap-2 px-4 pt-4 pb-2">
        <Clapperboard className="h-3.5 w-3.5 text-primary" />
        <span className="text-xs font-semibold text-muted-foreground">
          镜头目录
        </span>
        <span className="ml-auto text-[10px] text-muted-foreground/70">
          {totalShots} 镜
        </span>
      </div>

      <OverlayScrollArea className="min-h-0 flex-1">
        <div className="flex flex-col gap-1 px-3 pb-4">
          <button
            type="button"
            onClick={() => onFilterChange({ type: "all" })}
            className={cn(
              "flex w-full items-center gap-2 rounded-lg border px-2.5 py-2 text-left text-sm transition-colors motion-reduce:transition-none",
              filter.type === "all"
                ? "border-border/40 bg-muted/70 font-medium text-foreground"
                : "border-transparent text-muted-foreground hover:bg-muted/40 hover:text-foreground"
            )}
          >
            <Layers className="h-3.5 w-3.5 shrink-0" />
            <span className="truncate">全部镜头</span>
            <span className="ml-auto text-[10px] text-muted-foreground/70">
              {totalShots}
            </span>
          </button>

          {tree.map((episodeNode) => {
            const collapsed = collapsedIds.has(episodeNode.episode.id);
            const episodeActive = isEpisodeActive(episodeNode.episode.id);
            return (
              <div key={episodeNode.episode.id} className="flex flex-col gap-1">
                <div
                  className={cn(
                    "flex w-full items-center rounded-lg border pr-1 transition-colors motion-reduce:transition-none",
                    episodeActive
                      ? "border-border/40 bg-muted/70"
                      : "border-transparent hover:bg-muted/40"
                  )}
                >
                  <button
                    type="button"
                    onClick={() => handleEpisodeSelect(episodeNode.episode.id)}
                    aria-expanded={!collapsed}
                    className={cn(
                      "flex min-w-0 flex-1 items-center gap-1.5 px-2.5 py-2 text-left text-sm rounded-lg",
                      episodeActive
                        ? "font-medium text-foreground"
                        : "text-muted-foreground hover:text-foreground"
                    )}
                  >
                    {collapsed ? (
                      <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground/60" />
                    ) : (
                      <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground/60" />
                    )}
                    <span className="truncate">{episodeNode.episodeLabel}</span>
                    <span className="ml-auto shrink-0 text-[10px] text-muted-foreground/70">
                      {episodeNode.shotCount} 镜
                    </span>
                  </button>
                  <EpisodeSubtitleExportButton
                    scriptEpisodeId={
                      episodeNode.episode.scriptEpisodeId ?? null
                    }
                    episodeLabel={episodeNode.episodeLabel}
                  />
                </div>

                {!collapsed && (
                  <div className="ml-4 flex flex-col gap-0.5 border-l border-border/20 pl-2">
                    {episodeNode.scenes.length === 0 ? (
                      <p className="px-2.5 py-1 text-xs text-muted-foreground/60">
                        暂无场次
                      </p>
                    ) : (
                      episodeNode.scenes.map((sceneNode) => (
                        <div
                          key={sceneNode.scene.id}
                          className="flex flex-col gap-0.5"
                        >
                          <button
                            type="button"
                            onClick={() =>
                              onFilterChange({
                                type: "scene",
                                episodeId: episodeNode.episode.id,
                                sceneId: sceneNode.scene.id,
                              })
                            }
                            className={cn(
                              "flex w-full items-center gap-2 rounded-lg border px-2.5 py-1.5 text-left text-xs transition-colors motion-reduce:transition-none",
                              isSceneActive(sceneNode.scene.id)
                                ? "border-border/40 bg-muted/70 font-medium text-foreground"
                                : "border-transparent text-muted-foreground hover:bg-muted/40 hover:text-foreground"
                            )}
                          >
                            <span className="truncate">
                              {sceneNode.sceneLabel}
                            </span>
                            <span className="ml-auto shrink-0 text-[10px] text-muted-foreground/60">
                              {sceneNode.shotCount}
                            </span>
                          </button>
                          {onShotSelect &&
                            sceneNode.shots.map((shot) => (
                              <button
                                key={shot.itemId}
                                type="button"
                                onClick={() =>
                                  onShotSelect(
                                    episodeNode.episode.id,
                                    sceneNode.scene.id,
                                    shot.itemId
                                  )
                                }
                                className={cn(
                                  "ml-3 flex w-[calc(100%-0.75rem)] items-center gap-2 rounded-lg border px-2 py-1 text-left text-xs transition-colors motion-reduce:transition-none",
                                  focusedShotId === shot.itemId
                                    ? "border-primary/30 bg-primary/10 font-medium text-primary"
                                    : "border-transparent text-muted-foreground/80 hover:bg-muted/40 hover:text-foreground"
                                )}
                              >
                                <span
                                  className={cn(
                                    "h-1.5 w-1.5 shrink-0 rounded-full",
                                    shot.hasVideo
                                      ? "bg-emerald-500/70"
                                      : "bg-muted-foreground/25"
                                  )}
                                  title={shot.hasVideo ? "有可用视频" : "暂无视频"}
                                />
                                <span className="truncate">
                                  镜头 {shot.shotLabel}
                                </span>
                              </button>
                            ))}
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            );
          })}

          {tree.length === 0 && (
            <p className="px-2.5 py-4 text-xs text-muted-foreground/60">
              项目暂无分集内容
            </p>
          )}
        </div>
      </OverlayScrollArea>
    </div>
  );
}
