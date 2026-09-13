"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronRight, Film, Plus } from "lucide-react";
import { cn } from "@/lib/utils";
import { projectApi, type Project } from "@/lib/api/project";
import { assetApi } from "@/lib/api/asset";
import { SectionHeader } from "./section-header";

/** 项目真实计数：分集数来自工作区概览，资产数来自资产分页 total */
interface ProjectCounts {
  episodes: number | null;
  assets: number | null;
}

function formatTime(iso: string) {
  const d = new Date(iso);
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "刚刚";
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return d.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

/** 单个项目行的计数摘要，如 "3 集 · 12 资产"；计数加载失败时不显示 */
function CountSummary({ counts }: { counts?: ProjectCounts }) {
  if (!counts) return null;
  const parts: string[] = [];
  if (counts.episodes !== null) parts.push(`${counts.episodes} 集`);
  if (counts.assets !== null) parts.push(`${counts.assets} 资产`);
  if (parts.length === 0) return null;
  return (
    <span className="hidden md:inline text-[11px] text-muted-foreground/60 tabular-nums">
      {parts.join(" · ")}
    </span>
  );
}

/** 最近项目区块：列表条目展示真实分集数与资产数 */
export function RecentProjects({ projects }: { projects: Project[] }) {
  const router = useRouter();
  const [counts, setCounts] = useState<Record<number, ProjectCounts>>({});

  useEffect(() => {
    if (projects.length === 0) return;
    let active = true;
    void Promise.all(
      projects.map(async (project) => {
        const [overview, assets] = await Promise.all([
          projectApi.getWorkspaceOverview(project.id).catch(() => null),
          assetApi.listAll({ projectId: project.id, page: 1, size: 1 }).catch(() => null),
        ]);
        return [
          project.id,
          {
            episodes: overview ? overview.scriptEpisodeCount : null,
            assets: assets ? assets.total : null,
          } satisfies ProjectCounts,
        ] as const;
      })
    ).then((entries) => {
      if (!active) return;
      const next: Record<number, ProjectCounts> = {};
      for (const [id, value] of entries) {
        next[id] = value;
      }
      setCounts(next);
    });
    return () => {
      active = false;
    };
  }, [projects]);

  if (projects.length === 0) {
    return (
      <>
        <SectionHeader title="最近项目" icon={<Film className="h-4 w-4 text-primary" />} />
        <div
          onClick={() => router.push("/projects")}
          className={cn(
            "rounded-xl border border-dashed border-border/40 p-10",
            "flex flex-col items-center justify-center text-center",
            "bg-card/20 cursor-pointer hover:border-primary/40 hover:bg-primary/5 transition-all"
          )}
        >
          <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center mb-3">
            <Plus className="h-6 w-6 text-primary/60" />
          </div>
          <p className="text-sm font-medium mb-0.5">创建你的第一个项目</p>
          <p className="text-xs text-muted-foreground">
            点击此处开始创建项目
          </p>
        </div>
      </>
    );
  }

  return (
    <>
      <SectionHeader
        title="最近项目"
        icon={<Film className="h-4 w-4 text-primary" />}
        action={{ label: "全部项目", onClick: () => router.push("/projects") }}
      />
      <div className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm overflow-hidden divide-y divide-border/15">
        {projects.map((project) => {
          const projectCounts = counts[project.id];
          return (
            <div
              key={project.id}
              onClick={() => router.push(`/projects/${project.id}`)}
              className="group flex items-center gap-4 px-4 py-3.5 cursor-pointer hover:bg-muted/20 transition-colors"
            >
              <div className="h-9 w-9 rounded-lg bg-primary/8 flex items-center justify-center shrink-0 group-hover:bg-primary/12 transition-colors">
                <Film className="h-4.5 w-4.5 text-primary/70" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate group-hover:text-primary transition-colors">
                  {project.name}
                </p>
                <p className="text-xs text-muted-foreground/60 truncate mt-0.5">
                  {project.description || "暂无描述"}
                </p>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <CountSummary counts={projectCounts} />
                <span className="text-[11px] text-muted-foreground/40 tabular-nums">
                  {formatTime(project.updateTime)}
                </span>
                <ChevronRight className="h-3.5 w-3.5 text-muted-foreground/20 group-hover:text-muted-foreground/60 transition-colors" />
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}
