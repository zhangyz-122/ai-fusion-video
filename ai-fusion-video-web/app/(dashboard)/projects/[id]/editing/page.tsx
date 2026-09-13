"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  Clapperboard,
  Loader2,
  Menu,
  Play,
  RotateCcw,
  Scissors,
} from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { OverlayScrollArea } from "@/components/dashboard/overlay-scroll-area";
import { toastApiError } from "@/lib/api/toast-api-error";
import {
  editingApi,
  resolveShotVideo,
  type EditingEpisodeNode,
  type EditingShotVideo,
} from "@/lib/api/editing";
import type { Storyboard } from "@/lib/api/storyboard";
import { useProject } from "../project-context";
import {
  buildEpisodeTree,
  flattenTimeline,
  matchesFilter,
  type EditingShotRow,
  type ShotFilter,
} from "./_components/editing-types";
import { EditingSidebar } from "./_components/editing-sidebar";
import { ShotCard } from "./_components/shot-card";
import { ChainPreviewSheet } from "./_components/chain-preview-sheet";

export default function ProjectEditingPage() {
  const params = useParams();
  const projectId = Number(params.id);
  const { project } = useProject();

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [storyboard, setStoryboard] = useState<Storyboard | null>(null);
  const [timelineEpisodes, setTimelineEpisodes] = useState<EditingEpisodeNode[]>([]);
  const [rows, setRows] = useState<EditingShotRow[]>([]);
  const [shotVideos, setShotVideos] = useState<Record<number, EditingShotVideo>>({});
  const [excludedIds, setExcludedIds] = useState<Set<number>>(new Set());
  const [filter, setFilter] = useState<ShotFilter>({ type: "all" });
  const [focusedShotId, setFocusedShotId] = useState<number | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  // 默认顺序快照，供重置使用
  const defaultRowsRef = useRef<EditingShotRow[]>([]);

  const loadTimeline = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const timeline = await editingApi.loadTimeline(projectId);
      const flatRows = flattenTimeline(timeline.episodes);
      setStoryboard(timeline.storyboard);
      setTimelineEpisodes(timeline.episodes);
      setRows(flatRows);
      defaultRowsRef.current = flatRows;
      setShotVideos(timeline.shotVideos);
      setExcludedIds(new Set());
      setFilter({ type: "all" });
    } catch (err) {
      console.error("加载剪辑时间线失败:", err);
      setLoadError(err instanceof Error ? err.message : String(err));
      toastApiError(err, "加载剪辑时间线失败");
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    loadTimeline();
  }, [loadTimeline]);

  // ========== 派生数据 ==========

  const visibleRows = useMemo(
    () => rows.filter((row) => matchesFilter(row, filter)),
    [rows, filter]
  );

  // 全局播放序号：仅启用镜头按当前顺序编号
  const orderNumberById = useMemo(() => {
    const map = new Map<number, number>();
    let seq = 0;
    for (const row of rows) {
      if (excludedIds.has(row.item.id)) continue;
      seq += 1;
      map.set(row.item.id, seq);
    }
    return map;
  }, [rows, excludedIds]);

  const enabledCount = orderNumberById.size;
  // 目录始终按默认数据结构展示，不跟随本地排序
  const episodeTree = useMemo(
    () => buildEpisodeTree(timelineEpisodes, shotVideos),
    [timelineEpisodes, shotVideos]
  );

  // ========== 操作 ==========

  const handleToggleEnabled = useCallback((itemId: number, enabled: boolean) => {
    setExcludedIds((prev) => {
      const next = new Set(prev);
      if (enabled) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });
  }, []);

  // 在当前过滤视图内与相邻可见镜头交换位置
  const handleMove = useCallback(
    (itemId: number, direction: -1 | 1) => {
      setRows((prev) => {
        const visibleIndexes = prev
          .map((row, index) => (matchesFilter(row, filter) ? index : -1))
          .filter((index) => index >= 0);
        const pos = visibleIndexes.findIndex(
          (index) => prev[index].item.id === itemId
        );
        const targetPos = pos + direction;
        if (pos < 0 || targetPos < 0 || targetPos >= visibleIndexes.length) {
          return prev;
        }
        const next = [...prev];
        const a = visibleIndexes[pos];
        const b = visibleIndexes[targetPos];
        [next[a], next[b]] = [next[b], next[a]];
        return next;
      });
    },
    [filter]
  );

  const isDirty =
    excludedIds.size > 0 ||
    rows.some((row, index) => defaultRowsRef.current[index]?.item.id !== row.item.id);

  // 目录点击镜头：切到其所属场次并滚动定位
  const handleShotSelect = useCallback(
    (episodeId: number, sceneId: number, itemId: number) => {
      setFilter({ type: "scene", episodeId, sceneId });
      setFocusedShotId(itemId);
      setMobileNavOpen(false);
    },
    []
  );

  useEffect(() => {
    if (focusedShotId == null) return;
    const el = document.getElementById(`shot-${focusedShotId}`);
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [focusedShotId, visibleRows]);

  const handleReset = useCallback(() => {
    setRows(defaultRowsRef.current);
    setExcludedIds(new Set());
  }, []);

  // 串联预览：启用镜头按当前全局顺序
  const previewShots = useMemo(
    () =>
      rows
        .filter((row) => !excludedIds.has(row.item.id))
        .map((row) => ({
          row,
          videoUrl: resolveShotVideo(row.item, shotVideos).videoUrl,
        })),
    [rows, excludedIds, shotVideos]
  );

  // ========== 渲染 ==========

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (loadError && !storyboard) {
    return (
      <div className="rounded-xl border border-destructive/30 bg-card p-6">
        <p className="font-medium">剪辑时间线加载失败</p>
        <p className="mt-1 text-sm text-muted-foreground">{loadError}</p>
        <Button variant="outline" size="sm" className="mt-3" onClick={() => void loadTimeline()}>
          <RotateCcw />
          重试
        </Button>
      </div>
    );
  }

  if (!storyboard) {
    return (
      <div className="rounded-xl border border-destructive/30 bg-card p-6">
        <p className="font-medium">项目分镜工作区未初始化</p>
        <p className="mt-1 text-sm text-muted-foreground">
          剪辑交付依赖分镜数据，请先完成分镜工作区初始化。
        </p>
      </div>
    );
  }

  const sidebar = (
    <EditingSidebar
      tree={episodeTree}
      totalShots={rows.length}
      filter={filter}
      focusedShotId={focusedShotId}
      onShotSelect={handleShotSelect}
      onFilterChange={(next) => {
        setFilter(next);
        setFocusedShotId(null);
        setMobileNavOpen(false);
      }}
    />
  );

  return (
    <div className="flex min-h-0 w-full flex-1 flex-col overflow-hidden rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm">
      {/* 顶部工具栏 */}
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-border/20 px-4 py-3 md:px-5">
        <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
          <SheetTrigger
            render={
              <button
                type="button"
                className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground xl:hidden motion-reduce:transition-none"
                aria-label="打开镜头目录"
              >
                <Menu className="h-5 w-5" />
              </button>
            }
          />
          <SheetContent side="left" className="w-[280px] border-r-0 p-0 flex flex-col pt-12">
            {sidebar}
          </SheetContent>
        </Sheet>

        <h2 className="flex items-center gap-2 text-base font-semibold">
          <Scissors className="h-4 w-4 text-primary shrink-0" />
          <span className="truncate">剪辑交付{project?.name ? ` · ${project.name}` : ""}</span>
        </h2>
        <span className="hidden text-xs text-muted-foreground sm:inline">
          共 {rows.length} 镜 · 启用 {enabledCount} 镜
          {excludedIds.size > 0 ? ` · 排除 ${excludedIds.size} 镜` : ""}
        </span>

        <div className="ml-auto flex items-center gap-2">
          {isDirty && (
            <Button variant="ghost" size="sm" onClick={handleReset} title="恢复默认顺序与启用状态">
              <RotateCcw />
              重置
            </Button>
          )}
          <Button
            variant="default"
            size="sm"
            disabled={previewShots.length === 0}
            onClick={() => setPreviewOpen(true)}
            title={
              previewShots.length === 0
                ? "暂无启用的镜头"
                : `按当前顺序串联播放 ${previewShots.length} 个镜头`
            }
          >
            <Play />
            串联预览
          </Button>
        </div>
      </div>

      {/* 主体：左侧目录 + 中间镜头列表 */}
      <div className="flex min-h-0 flex-1">
        <aside className="hidden w-64 shrink-0 border-r border-border/20 xl:block">
          {sidebar}
        </aside>

        <div className="relative min-h-0 min-w-0 flex-1">
          <OverlayScrollArea className="absolute inset-0">
            <div className="mx-auto w-full max-w-[960px] px-4 py-4 md:px-6">
              {visibleRows.length === 0 ? (
                <div className="flex flex-col items-center justify-center gap-3 py-20 text-center">
                  <Clapperboard className="h-10 w-10 text-muted-foreground/20" />
                  {rows.length === 0 ? (
                    <>
                      <p className="text-sm text-muted-foreground">
                        项目暂无分镜镜头，请先在分镜工作区生成内容
                      </p>
                      <Link
                        href={`/projects/${projectId}/storyboards`}
                        className={buttonVariants({ variant: "outline", size: "sm" })}
                      >
                        前往分镜工作区
                      </Link>
                    </>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      当前筛选范围内没有镜头
                    </p>
                  )}
                </div>
              ) : (
                <div className="flex flex-col gap-3">
                  {visibleRows.map((row) => (
                    <ShotCard
                      key={row.item.id}
                      row={row}
                      video={resolveShotVideo(row.item, shotVideos)}
                      orderNumber={orderNumberById.get(row.item.id) ?? null}
                      moveUpDisabled={row.item.id === visibleRows[0].item.id}
                      moveDownDisabled={row.item.id === visibleRows[visibleRows.length - 1].item.id}
                      onToggleEnabled={handleToggleEnabled}
                      onMove={handleMove}
                    />
                  ))}
                </div>
              )}
            </div>
          </OverlayScrollArea>
        </div>
      </div>

      <ChainPreviewSheet
        open={previewOpen}
        onOpenChange={setPreviewOpen}
        shots={previewShots}
      />
    </div>
  );
}
