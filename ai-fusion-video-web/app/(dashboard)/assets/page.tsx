"use client";

import { useState, useEffect, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { motion, useReducedMotion } from "framer-motion";
import {
  Loader2,
  Package,
  AlertCircle,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { assetApi, type Asset } from "@/lib/api/asset";
import { projectApi, type Project } from "@/lib/api/project";
import { toastApiError } from "@/lib/api/toast-api-error";
import { useConfirm } from "@/components/ui/confirm-dialog";
import { Button } from "@/components/ui/button";
import { AssetTypeCards } from "./_components/asset-type-cards";
import { AssetsToolbar, type AssetsViewMode } from "./_components/assets-toolbar";
import { TagCloud } from "./_components/tag-cloud";
import { AssetGrid } from "./_components/asset-grid";
import { AssetListView } from "./_components/asset-list-view";
import { UploadPanel } from "./_components/upload-panel";
import { RecycleBinView } from "./_components/recycle-bin-view";
import { useAssetUpload } from "./_components/use-asset-upload";
import {
  FETCH_PAGE_SIZE,
  MAX_LOADED_ASSETS,
  VIEW_MODE_STORAGE_KEY,
} from "./_components/constants";
import {
  buildTagCloud,
  matchesKeyword,
  parseAssetTags,
} from "./_components/utils";

type PageTab = "assets" | "recycle";

export default function AssetsPage() {
  const router = useRouter();
  const { confirm } = useConfirm();
  const reduceMotion = useReducedMotion() ?? false;

  // 数据（全量加载，筛选与统计在前端计算）
  const [assets, setAssets] = useState<Asset[]>([]);
  const [serverTotal, setServerTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [projects, setProjects] = useState<Project[]>([]);

  // 筛选
  const [selectedProjectId, setSelectedProjectId] = useState("all");
  const [selectedType, setSelectedType] = useState("all");
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [viewMode, setViewMode] = useState<AssetsViewMode>("grid");

  // 回收站（服务端已删除资产）
  const [tab, setTab] = useState<PageTab>("assets");
  const [binRecords, setBinRecords] = useState<Asset[]>([]);
  const [binLoading, setBinLoading] = useState(true);
  const [restoringId, setRestoringId] = useState<number | null>(null);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedKeyword(keyword), 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  // 视图偏好持久化
  useEffect(() => {
    const saved = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY);
    if (saved === "grid" || saved === "list") setViewMode(saved);
  }, []);
  useEffect(() => {
    window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, viewMode);
  }, [viewMode]);

  // 项目列表
  useEffect(() => {
    projectApi.list().then(setProjects).catch((error) => {
      toastApiError(error, "加载项目列表失败");
      setProjects([]);
    });
  }, []);

  // 全量加载资产（分页循环，上限 MAX_LOADED_ASSETS）
  const loadAssets = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const collected: Asset[] = [];
      let total = 0;
      for (let page = 1; collected.length < MAX_LOADED_ASSETS; page++) {
        const resp = await assetApi.listAll({ page, size: FETCH_PAGE_SIZE });
        total = typeof resp.total === "number" ? resp.total : collected.length;
        const records = resp.records || [];
        collected.push(...records);
        if (records.length === 0 || collected.length >= total) break;
      }
      setAssets(collected);
      setServerTotal(total);
    } catch (error) {
      toastApiError(error, "加载资产失败");
      setLoadError(true);
      setAssets([]);
      setServerTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  // 加载回收站（已删除资产，分页循环，上限 MAX_LOADED_ASSETS）
  const loadBin = useCallback(async () => {
    setBinLoading(true);
    try {
      const collected: Asset[] = [];
      for (let page = 1; collected.length < MAX_LOADED_ASSETS; page++) {
        const resp = await assetApi.listRecycleBin({ page, size: FETCH_PAGE_SIZE });
        const records = resp.records || [];
        collected.push(...records);
        if (records.length === 0 || collected.length >= (resp.total ?? 0)) break;
      }
      setBinRecords(collected);
    } catch (error) {
      toastApiError(error, "加载回收站失败");
      setBinRecords([]);
    } finally {
      setBinLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAssets();
  }, [loadAssets]);

  useEffect(() => {
    loadBin();
  }, [loadBin]);

  // ===== 派生数据 =====

  const projectMap = useMemo(() => {
    const map: Record<number, string> = {};
    projects.forEach((p) => { map[p.id] = p.name; });
    return map;
  }, [projects]);

  const projectOptions = useMemo(() => [
    { value: "all", label: "全部项目" },
    ...projects.map((p) => ({ value: String(p.id), label: p.name })),
  ], [projects]);

  const typeCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    assets.forEach((a) => { counts[a.type] = (counts[a.type] || 0) + 1; });
    return counts;
  }, [assets]);

  // 标签云基于项目 + 类型过滤后的集合（不随关键词变化）
  const tagScopeAssets = useMemo(() => assets.filter((a) => {
    if (selectedProjectId !== "all" && String(a.projectId) !== selectedProjectId) return false;
    if (selectedType !== "all" && a.type !== selectedType) return false;
    return true;
  }), [assets, selectedProjectId, selectedType]);

  const tagCloud = useMemo(() => buildTagCloud(tagScopeAssets), [tagScopeAssets]);

  const visibleAssets = useMemo(() => tagScopeAssets.filter((a) => {
    if (!matchesKeyword(a, debouncedKeyword)) return false;
    if (selectedTags.length === 0) return true;
    const tags = parseAssetTags(a.tags);
    return selectedTags.every((t) => tags.includes(t));
  }), [tagScopeAssets, debouncedKeyword, selectedTags]);

  const filtersActive =
    debouncedKeyword.trim() !== "" ||
    selectedTags.length > 0 ||
    selectedType !== "all" ||
    selectedProjectId !== "all";

  // ===== 操作 =====

  const upload = useAssetUpload({
    projectId: selectedProjectId === "all" ? null : Number(selectedProjectId),
    onUploaded: () => void loadAssets(),
  });

  const handleAssetClick = (asset: Asset) => {
    router.push(`/projects/${asset.projectId}/assets?highlight=${asset.id}`);
  };

  const handleToggleTag = (tag: string) => {
    setSelectedTags((prev) =>
      prev.includes(tag) ? prev.filter((t) => t !== tag) : [...prev, tag]
    );
  };

  // 删除：确认后软删，资产进入服务端回收站
  const handleDelete = useCallback(async (asset: Asset) => {
    const ok = await confirm({
      title: "删除资产",
      description: `「${asset.name}」将移入回收站，可在回收站中恢复。`,
      confirmText: "删除",
      variant: "destructive",
    });
    if (!ok) return;
    try {
      await assetApi.delete(asset.id);
      toast.success(`已移入回收站「${asset.name}」`);
      await Promise.all([loadAssets(), loadBin()]);
    } catch (error) {
      toastApiError(error, "删除资产失败");
    }
  }, [confirm, loadAssets, loadBin]);

  // 恢复：置 deleted = 0，保留原 id 与子资产
  const handleRestore = useCallback(async (record: Asset) => {
    setRestoringId(record.id);
    try {
      const restored = await assetApi.restoreRecycled(record.id);
      toast.success(`已恢复「${restored.name}」`);
      await Promise.all([loadAssets(), loadBin()]);
    } catch (error) {
      toastApiError(error, "恢复失败，请稍后重试");
    } finally {
      setRestoringId(null);
    }
  }, [loadAssets, loadBin]);

  // 彻底删除：物理删除（二次确认在回收站视图内完成）
  const handlePurge = useCallback(async (record: Asset) => {
    try {
      await assetApi.purgeRecycled(record.id);
      toast.success(`已彻底删除「${record.name}」`);
      await loadBin();
    } catch (error) {
      toastApiError(error, "彻底删除失败");
    }
  }, [loadBin]);

  return (
    <motion.div
      className="max-w-[1200px]"
      initial={reduceMotion ? false : { opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.25, 0.46, 0.45, 0.94] }}
    >
      {/* ========== 页面标题 + 页签 ========== */}
      <div className="flex items-end justify-between gap-4 flex-wrap mb-5 lg:mb-8">
        <div>
          <h1 className="text-2xl lg:text-3xl font-bold tracking-tight">素材资产</h1>
          <p className="text-muted-foreground mt-1">
            跨项目查看和管理所有创作素材
          </p>
        </div>
        <div className="inline-flex items-center gap-1 rounded-xl border border-border/30 bg-card/50 p-1">
          <button
            type="button"
            aria-pressed={tab === "assets"}
            onClick={() => setTab("assets")}
            className={cn(
              "inline-flex items-center min-h-11 rounded-lg px-4 lg:px-3 lg:py-1.5 text-sm transition-colors",
              "focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
              tab === "assets"
                ? "bg-background text-foreground font-medium shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            全部资产
          </button>
          <button
            type="button"
            aria-pressed={tab === "recycle"}
            onClick={() => setTab("recycle")}
            className={cn(
              "inline-flex items-center min-h-11 rounded-lg px-4 lg:px-3 lg:py-1.5 text-sm transition-colors",
              "focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50",
              tab === "recycle"
                ? "bg-background text-foreground font-medium shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            <Trash2 className="h-3.5 w-3.5 mr-1" />
            回收站
            {binRecords.length > 0 && (
              <span className="ml-1.5 rounded-full bg-destructive/10 px-1.5 text-[10px] font-medium text-destructive">
                {binRecords.length}
              </span>
            )}
          </button>
        </div>
      </div>

      {tab === "recycle" ? (
        <RecycleBinView
          records={binRecords}
          loading={binLoading}
          restoringId={restoringId}
          onRestore={(record) => void handleRestore(record)}
          onPurge={(record) => void handlePurge(record)}
          onRetry={() => void loadBin()}
        />
      ) : (
        <>
          {/* ========== 统计卡片 ========== */}
          <div className="mb-5 lg:mb-8">
            <AssetTypeCards
              totalCount={assets.length}
              typeCounts={typeCounts}
              selectedType={selectedType}
              onSelectType={(type) => {
                setSelectedType(type);
                setSelectedTags([]);
              }}
            />
          </div>

          {/* ========== 筛选工具栏 ========== */}
          <div className="mb-4">
            <AssetsToolbar
              projectOptions={projectOptions}
              selectedProjectId={selectedProjectId}
              onSelectProject={(value) => {
                setSelectedProjectId(value);
                setSelectedTags([]);
              }}
              keyword={keyword}
              onKeywordChange={setKeyword}
              viewMode={viewMode}
              onViewModeChange={setViewMode}
              onUploadFiles={(files) => upload.addFiles(files)}
            />
          </div>

          {/* ========== 上传任务面板 ========== */}
          {upload.tasks.length > 0 && (
            <div className="mb-4">
              <UploadPanel
                tasks={upload.tasks}
                onRetry={upload.retry}
                onDismiss={upload.dismiss}
              />
            </div>
          )}

          {/* ========== 标签云 ========== */}
          {tagCloud.length > 0 && (
            <div className="mb-6">
              <TagCloud
                tags={tagCloud}
                selectedTags={selectedTags}
                onToggle={handleToggleTag}
              />
            </div>
          )}

          {/* ========== 内容区 ========== */}
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
              <Loader2 className="h-8 w-8 animate-spin mb-3 text-muted-foreground/40" />
              <p className="text-sm">加载资产中...</p>
            </div>
          ) : loadError ? (
            <div className="flex flex-col items-center justify-center py-20">
              <AlertCircle className="h-8 w-8 text-destructive/50 mb-3" />
              <p className="text-sm text-muted-foreground mb-4">
                加载资产失败，请检查网络后重试
              </p>
              <Button variant="outline" size="sm" onClick={() => void loadAssets()}>
                <RefreshCw data-icon="inline-start" />
                重新加载
              </Button>
            </div>
          ) : visibleAssets.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-center">
              <div className="h-16 w-16 rounded-2xl bg-orange-500/10 flex items-center justify-center mb-4">
                <Package className="h-8 w-8 text-orange-400/60" />
              </div>
              <h3 className="text-lg font-semibold mb-1">暂无资产</h3>
              <p className="text-sm text-muted-foreground max-w-sm">
                {filtersActive
                  ? "没有找到匹配的资产，试试调整筛选条件"
                  : "在项目中创建角色、场景、道具等资产后，这里会集中展示"}
              </p>
            </div>
          ) : viewMode === "grid" ? (
            <AssetGrid
              assets={visibleAssets}
              projectMap={projectMap}
              onOpen={handleAssetClick}
              onDelete={(asset) => void handleDelete(asset)}
            />
          ) : (
            <AssetListView
              assets={visibleAssets}
              projectMap={projectMap}
              onOpen={handleAssetClick}
              onDelete={(asset) => void handleDelete(asset)}
            />
          )}

          {/* ========== 底部汇总 ========== */}
          {!loading && !loadError && visibleAssets.length > 0 && (
            <div className="flex items-center justify-center py-6">
              <p className="text-xs text-muted-foreground/40">
                {serverTotal > assets.length
                  ? `已加载前 ${assets.length} 个资产（共 ${serverTotal} 个），匹配 ${visibleAssets.length} 个`
                  : filtersActive
                    ? `匹配 ${visibleAssets.length} / ${assets.length} 个资产`
                    : `共 ${assets.length} 个资产`}
              </p>
            </div>
          )}
        </>
      )}
    </motion.div>
  );
}
