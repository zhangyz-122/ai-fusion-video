"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { dashboardApi, type DashboardActivityItem } from "@/lib/api/dashboard";
import { Button } from "@/components/ui/button";

const kindLabels: Record<string, string> = {
  SCRIPT_PARSE: "剧本解析",
  IMAGE_TASK: "生图任务",
  VIDEO_TASK: "生视频任务",
  PRODUCTION_RUN: "生产运行",
};

/** 按条目种类深链到对应编辑器或工作区 */
function itemHref(item: DashboardActivityItem): string {
  if (item.kind === "SCRIPT_PARSE") {
    return item.projectId ? `/projects/${item.projectId}/scripts` : "/projects";
  }
  if (item.kind === "PRODUCTION_RUN") return "/production";
  if (item.kind === "IMAGE_TASK") return "/generate/image";
  if (item.kind === "VIDEO_TASK") return "/generate/video";
  if (item.projectId) return `/projects/${item.projectId}`;
  return "/generate";
}

function ActivityList({ items, emptyText }: { items: DashboardActivityItem[]; emptyText: string }) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">{emptyText}</p>;
  }
  return (
    <ul className="divide-y divide-border/20">
      {items.map((item, index) => (
        <li key={`${item.kind}-${item.refId ?? item.refKey ?? index}`} className="py-3">
          <Link href={itemHref(item)} className="group flex items-center justify-between gap-3">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs text-muted-foreground">{kindLabels[item.kind] ?? item.kind}</span>
                <span className="truncate text-sm font-medium group-hover:underline">{item.title}</span>
              </div>
              <p className="mt-0.5 truncate text-xs text-muted-foreground">{item.detail}</p>
            </div>
            {item.createTime && (
              <span className="shrink-0 text-xs text-muted-foreground">
                {new Date(item.createTime).toLocaleString("zh-CN", { hour12: false })}
              </span>
            )}
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function ActivitySection() {
  const [running, setRunning] = useState<DashboardActivityItem[]>([]);
  const [pending, setPending] = useState<DashboardActivityItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const data = await dashboardApi.activity();
        if (active) {
          setRunning(data.running ?? []);
          setPending(data.pending ?? []);
        }
      } catch {
        if (active) setError("无法读取任务动态，请稍后重试。");
      }
      if (active) setLoading(false);
    }
    void load();
    const timer = window.setInterval(() => void load(), 30000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [revision]);

  const total = running.length + pending.length;

  return (
    <section aria-labelledby="activity" className="space-y-4 rounded-xl border border-border/30 bg-card/50 p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="activity" className="text-xl font-semibold">进行中与待办</h2>
        <Button variant="ghost" size="icon-sm" onClick={() => setRevision(v => v + 1)} disabled={loading} title="刷新">
          {loading ? <Loader2 className="animate-spin" /> : <span aria-hidden>⟳</span>}
        </Button>
      </div>
      {error && <div role="alert" className="text-sm">{error}</div>}
      {!error && (
        <div className="grid gap-6 md:grid-cols-2">
          <div className="space-y-1">
            <h3 className="text-sm font-medium">进行中（{running.length}）</h3>
            {loading ? <Loader2 className="animate-spin text-muted-foreground" /> : <ActivityList items={running} emptyText="暂无进行中的任务" />}
          </div>
          <div className="space-y-1">
            <h3 className="text-sm font-medium">待处理（{pending.length}）</h3>
            {loading ? <Loader2 className="animate-spin text-muted-foreground" /> : <ActivityList items={pending} emptyText="没有需要处理的事项" />}
          </div>
        </div>
      )}
      {!error && total === 0 && !loading && (
        <p className="text-xs text-muted-foreground">启动一项生成或生产后，这里会实时显示进度。</p>
      )}
    </section>
  );
}
