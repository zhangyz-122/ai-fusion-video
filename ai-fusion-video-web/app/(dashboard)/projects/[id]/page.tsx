"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { toastApiError } from "@/lib/api/toast-api-error";
import { projectApi } from "@/lib/api/project";
import { assetApi } from "@/lib/api/asset";
import { productionApi } from "@/lib/api/production";
import { useProject } from "./project-context";
import { cn } from "@/lib/utils";

type StageKey = "write" | "bible" | "board" | "shoot" | "cut";

interface Stage {
  key: StageKey;
  label: string;
  href: string;
  done: boolean;
  detail: string;
}

export default function ProjectOverviewPage() {
  const params = useParams();
  const projectId = Number(params.id);
  const { project } = useProject();
  const [loading, setLoading] = useState(true);
  const [initializing, setInitializing] = useState(false);
  const [sceneCount, setSceneCount] = useState(0);
  const [itemCount, setItemCount] = useState(0);
  const [assetCount, setAssetCount] = useState(0);
  const [runCount, setRunCount] = useState(0);
  const [selectedCount, setSelectedCount] = useState(0);
  const [hasWorkspace, setHasWorkspace] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [overview, assets, runsPage] = await Promise.all([
        projectApi.getWorkspaceOverview(projectId),
        assetApi.list(projectId).catch(() => []),
        productionApi.list({ pageNo: 1, pageSize: 50 }).catch(() => ({ list: [] })),
      ]);
      const mine = (runsPage.list ?? []).filter((run) => run.projectId === projectId);
      setSceneCount(overview.scriptSceneCount ?? 0);
      setItemCount(overview.storyboardStatistics?.itemCount ?? 0);
      setAssetCount(assets.length);
      setRunCount(mine.length);
      setSelectedCount(mine.filter((run) => run.status === "SELECTED").length);
      setHasWorkspace(Boolean(overview.script && overview.storyboard));
    } catch (error) {
      toastApiError(error, "加载这部剧失败");
      setHasWorkspace(false);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  const stages: Stage[] = useMemo(() => [
    {
      key: "write",
      label: "写",
      href: `/projects/${projectId}/source`,
      done: sceneCount > 0,
      detail: sceneCount > 0 ? `${sceneCount} 场` : "还没有场次",
    },
    {
      key: "bible",
      label: "定",
      href: `/projects/${projectId}/assets`,
      done: assetCount > 0,
      detail: assetCount > 0 ? `${assetCount} 个资产` : "还没有角色或场景",
    },
    {
      key: "board",
      label: "拆",
      href: `/projects/${projectId}/storyboards`,
      done: itemCount > 0,
      detail: itemCount > 0 ? `${itemCount} 个镜头` : "还没有分镜",
    },
    {
      key: "shoot",
      label: "拍",
      href: `/projects/${projectId}/production`,
      done: selectedCount > 0,
      detail: selectedCount > 0
        ? `${selectedCount} 镜已选片`
        : runCount > 0
          ? `${runCount} 条运行`
          : "锁首帧后生产",
    },
    {
      key: "cut",
      label: "剪",
      href: `/projects/${projectId}/delivery`,
      done: false,
      detail: "选片后合成",
    },
  ], [assetCount, itemCount, projectId, runCount, sceneCount, selectedCount]);

  const next = stages.find((stage) => !stage.done) ?? stages[3];
  const nextLabel: Record<StageKey, string> = {
    write: "去导入原文",
    bible: "去定角色场景",
    board: "去拆分镜",
    shoot: "去拍这一镜",
    cut: "去合成",
  };

  const initialize = async () => {
    setInitializing(true);
    try {
      await projectApi.initializeWorkspace(projectId);
      await load();
    } catch (error) {
      toastApiError(error, "初始化工作区失败");
    } finally {
      setInitializing(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-4xl space-y-8 px-6 py-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">这部剧</p>
          <h1 className="text-2xl font-semibold tracking-tight">{project?.name || "未命名"}</h1>
          <p className="text-sm text-muted-foreground">写 → 定 → 拆 → 拍 → 剪。当前只做下一步。</p>
        </div>
        <Link className={buttonVariants()} href={next.href}>
          {nextLabel[next.key]}
        </Link>
      </header>

      {!hasWorkspace && (
        <div className="rounded-xl border border-border/30 bg-card p-5">
          <p className="text-sm text-muted-foreground">工作区还没建好，不会覆盖已有数据。</p>
          <Button className="mt-3" variant="outline" onClick={() => void initialize()} disabled={initializing}>
            {initializing ? <Loader2 className="animate-spin" /> : null}
            初始化工作区
          </Button>
        </div>
      )}

      <ol className="grid gap-3 sm:grid-cols-5">
        {stages.map((stage, index) => (
          <li key={stage.key}>
            <Link
              href={stage.href}
              className={cn(
                "block rounded-xl border p-4 transition-colors",
                stage.key === next.key
                  ? "border-primary/40 bg-primary/5"
                  : "border-border/30 bg-card/50 hover:border-border/50"
              )}
            >
              <p className="text-xs text-muted-foreground">{index + 1}</p>
              <p className="mt-1 text-lg font-semibold">{stage.label}</p>
              <p className="mt-1 text-xs text-muted-foreground">{stage.detail}</p>
            </Link>
          </li>
        ))}
      </ol>
    </div>
  );
}
