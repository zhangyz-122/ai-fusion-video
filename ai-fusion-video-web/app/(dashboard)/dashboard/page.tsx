"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Film, Loader2, Plus } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { projectApi, type Project } from "@/lib/api/project";
import { toastApiError } from "@/lib/api/toast-api-error";
import { ActivitySection } from "./_components/activity-section";
import { cn } from "@/lib/utils";

function formatTime(iso: string) {
  const d = new Date(iso);
  const now = new Date();
  const mins = Math.floor((now.getTime() - d.getTime()) / 60000);
  if (mins < 1) return "刚刚";
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} 小时前`;
  return d.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

export default function WorkbenchPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    projectApi.list()
      .then(setProjects)
      .catch((err) => toastApiError(err, "加载项目失败"))
      .finally(() => setLoading(false));
  }, []);

  const latest = useMemo(
    () => [...projects].sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())[0],
    [projects]
  );

  if (loading) {
    return (
      <div className="flex justify-center py-32">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-4xl space-y-8 px-6 py-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">工作台</p>
          <h1 className="text-2xl font-semibold tracking-tight">继续这部剧</h1>
          <p className="text-sm text-muted-foreground">写、定、拆、拍、剪。一次只做下一步。</p>
        </div>
        {latest ? (
          <Button onClick={() => router.push(`/projects/${latest.id}`)}>
            打开 {latest.name}
            <ArrowRight />
          </Button>
        ) : (
          <Link className={buttonVariants()} href="/projects">
            <Plus /> 新建剧目
          </Link>
        )}
      </header>

      <ActivitySection />

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold">最近剧目</h2>
          <Link href="/projects" className="text-xs text-muted-foreground hover:text-foreground">全部</Link>
        </div>
        {projects.length === 0 ? (
          <Link
            href="/projects"
            className="flex flex-col items-center rounded-xl border border-dashed border-border/40 px-6 py-12 text-center"
          >
            <p className="text-sm font-medium">还没有剧目</p>
            <p className="mt-1 text-xs text-muted-foreground">新建一部剧，从剧本开始</p>
          </Link>
        ) : (
          <div className="divide-y divide-border/20 rounded-xl border border-border/30 bg-card">
            {projects
              .slice()
              .sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())
              .slice(0, 6)
              .map((project) => (
                <button
                  key={project.id}
                  type="button"
                  onClick={() => router.push(`/projects/${project.id}`)}
                  className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/20"
                >
                  <Film className="h-4 w-4 shrink-0 text-muted-foreground" />
                  <span className={cn("min-w-0 flex-1 truncate text-sm font-medium")}>{project.name}</span>
                  <span className="shrink-0 text-xs text-muted-foreground">{formatTime(project.updateTime)}</span>
                </button>
              ))}
          </div>
        )}
      </section>
    </div>
  );
}
