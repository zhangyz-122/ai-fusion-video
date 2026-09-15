"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { dashboardApi, type DashboardActivity } from "@/lib/api/dashboard";
import { cn } from "@/lib/utils";

/**
 * 顶栏全局任务指示器：跨页面轮询当前用户的进行中任务，
 * 有活动任务时显示徽标，点击跳转仪表盘查看明细。
 * 仅统计真正执行中的任务（running）；失败待确认、待质检等
 * 待办事项不计入，避免旋转加载指示器传达错误的进行中语义。
 */
export function TaskActivityBadge() {
  const router = useRouter();
  const [activity, setActivity] = useState<DashboardActivity | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const data = await dashboardApi.activity();
        if (active) setActivity(data);
      } catch {
        if (active) setActivity(null);
      }
    }
    void load();
    const timer = window.setInterval(() => void load(), 15000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  // 待办事项（失败待确认、待质检）只在仪表盘“待处理”列表展示，不计入进行中
  const runningCount = activity?.running?.length ?? 0;
  if (runningCount === 0) {
    return null;
  }
  const latest = activity?.running?.[0];

  return (
    <button
      onClick={() => router.push("/dashboard")}
      title={latest ? `${latest.title} · ${latest.detail}` : "有任务进行中"}
      className={cn(
        "relative inline-flex items-center gap-1.5 rounded-full border border-primary/30 bg-primary/5 px-2.5 py-1 text-xs text-primary transition-colors hover:bg-primary/10",
        // 移动端徽标较小,扩大热区但不横向扩展,避免挤压顶栏相邻控件
        "max-lg:relative max-lg:after:absolute max-lg:after:inset-x-0 max-lg:after:-inset-y-2.5 max-lg:after:rounded-full max-lg:after:content-['']"
      )}
    >
      <Loader2 className="h-3.5 w-3.5 animate-spin" />
      <span className="hidden sm:inline">{runningCount} 项进行中</span>
      <span className="sm:hidden">{runningCount}</span>
    </button>
  );
}
