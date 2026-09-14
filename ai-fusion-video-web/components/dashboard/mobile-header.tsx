"use client";

import { useCallback, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { Bell, LogOut, Settings } from "lucide-react";
import { AnimatedThemeToggler } from "@/components/ui/animated-theme-toggler";
import { UserAvatarDropdown } from "@/components/ui/menu";
import { NotificationPanel } from "@/components/dashboard/notification-panel";
import { TaskActivityBadge } from "@/components/dashboard/task-activity-badge";
import { ClientErrorBoundary } from "@/components/client-error-boundary";
import { useAuthStore } from "@/lib/store/auth-store";
import { usePipelineStore } from "@/lib/store/pipeline-store";
import { cn } from "@/lib/utils";

/**
 * 移动端顶栏(≤1024px):品牌 + 全局任务指示 + 通知,不承载页面导航
 * (导航由底部 Tab 栏与页面菜单浮层提供)。桌面端使用 AppHeader。
 */
export function MobileHeader() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const bellRef = useRef<HTMLButtonElement>(null);

  const {
    tasks,
    notificationOpen,
    setNotificationOpen,
    panelExpanded,
    setPanelExpanded,
  } = usePipelineStore();
  const runningCount = tasks.filter((t) => t.status === "running").length;
  const hasAnyTasks = tasks.length > 0;

  // 与桌面 AppHeader 保持一致:进入平台时恢复运行中的 Pipeline 列表
  useEffect(() => {
    const store = usePipelineStore.getState();
    store.restoreRunningPipelines();
  }, []);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        usePipelineStore.getState().resumePipelineConnections();
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, []);

  // 处理退出登录(layout 会在退出动画完成后自动跳转到登录页)
  const handleLogout = async () => {
    await new Promise((resolve) => setTimeout(resolve, 200));
    document.cookie = "auth-token=; path=/; max-age=0";
    await logout();
  };

  const handleNotificationPanelError = useCallback(() => {
    setPanelExpanded(false);
    setNotificationOpen(false);
  }, [setNotificationOpen, setPanelExpanded]);

  const userMenuItems = [
    {
      label: "个人设置",
      icon: <Settings className="h-full w-full" />,
      onClick: () => router.push("/settings/profile"),
    },
  ];

  const userLogoutItem = {
    label: "退出登录",
    icon: <LogOut className="h-full w-full" />,
    onClick: handleLogout,
  };

  return (
    <header className="fixed top-0 right-0 left-0 z-50 px-3 pt-3 lg:hidden">
      <div className="flex h-14 w-full items-center justify-between gap-2 rounded-2xl border border-border/40 bg-card/80 px-2 shadow-sm backdrop-blur-xl">
        {/* 品牌 */}
        <button
          type="button"
          className="flex min-h-11 shrink-0 cursor-pointer items-center px-2"
          onClick={() => router.push("/dashboard")}
        >
          <span className="text-sm font-semibold tracking-tight">融光</span>
        </button>

        {/* 状态与通知 */}
        <div className="flex items-center justify-end gap-1">
          <TaskActivityBadge />

          <AnimatedThemeToggler className="flex h-11 w-11 items-center justify-center rounded-xl text-violet-500 transition-colors hover:bg-violet-500/10 hover:text-violet-600 dark:text-violet-300 dark:hover:bg-violet-400/10 dark:hover:text-violet-200" />

          <button
            ref={bellRef}
            onClick={() => {
              if (panelExpanded) {
                setPanelExpanded(false);
                setNotificationOpen(false);
              } else {
                setPanelExpanded(true);
              }
            }}
            className={cn(
              "relative flex h-11 w-11 items-center justify-center rounded-xl transition-colors",
              "text-amber-500 hover:bg-amber-500/10 hover:text-amber-600 dark:text-amber-300 dark:hover:bg-amber-400/10 dark:hover:text-amber-200",
              panelExpanded
                ? "bg-amber-500/15 text-amber-600 dark:bg-amber-400/15 dark:text-amber-200"
                : ""
            )}
            aria-label="打开任务通知"
          >
            <Bell className="h-5 w-5" />
            {(runningCount > 0 || hasAnyTasks) && (
              <>
                {runningCount > 0 && (
                  <span className="absolute top-1.5 right-1.5 h-4 w-4 animate-ping rounded-full bg-blue-500/40" />
                )}
                <span
                  className={cn(
                    "absolute top-1.5 right-1.5 flex items-center justify-center rounded-full ring-2 ring-background/60",
                    runningCount > 0
                      ? "h-4 w-4 bg-blue-500 text-[9px] font-bold text-white"
                      : "h-2 w-2 bg-muted-foreground/40"
                  )}
                >
                  {runningCount > 0 ? runningCount : null}
                </span>
              </>
            )}
          </button>

          <ClientErrorBoundary
            key={`notification-panel-${notificationOpen}-${panelExpanded}`}
            context="AI 任务中心加载失败"
            onError={handleNotificationPanelError}
          >
            <NotificationPanel anchorRef={bellRef} />
          </ClientErrorBoundary>

          <UserAvatarDropdown
            user={{
              name: user?.nickname || user?.username || "用户",
              email: user?.email || user?.username,
              avatarUrl: user?.avatar,
            }}
            menuItems={userMenuItems}
            logoutItem={userLogoutItem}
          />
        </div>
      </div>
    </header>
  );
}
