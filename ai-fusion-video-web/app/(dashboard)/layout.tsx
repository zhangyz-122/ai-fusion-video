"use client";

import { useEffect, useState, useMemo, useSyncExternalStore } from "react";
import { useRouter, usePathname } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { Menu, X } from "lucide-react";
import { useAuthStore } from "@/lib/store/auth-store";
import { AppHeader } from "@/components/dashboard/app-header";
import { MobileHeader } from "@/components/dashboard/mobile-header";
import { MobileTabBar } from "@/components/dashboard/mobile-tab-bar";
import { SidebarNav } from "@/components/dashboard/sidebar-nav";
import { OverlayScrollArea } from "@/components/dashboard/overlay-scroll-area";
import { useMediaQuery } from "@/lib/hooks/use-media-query";
import { cn } from "@/lib/utils";
import { projectApi, type Project } from "@/lib/api/project";
import { toastApiError } from "@/lib/api/toast-api-error";
import { AssistantDockSlot } from "@/components/dashboard/assistant/dock-slot";
import { ClientErrorBoundary } from "@/components/client-error-boundary";
import { ErrorRecoveryPanel } from "@/components/error-recovery-panel";
import { ErrorRegionFallback } from "@/components/error-region-fallback";
import { getClientErrorMessage } from "@/lib/client-error";

const GLOBAL_SIDEBAR_COLLAPSED_STORAGE_KEY = "fusion-dashboard-sidebar-collapsed";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  // 导航分界:≥1024px 使用桌面顶栏+侧边栏;窄屏使用移动顶栏+底部 Tab 栏
  const isDesktopNav = useMediaQuery("(min-width: 1024px)", true);
  const authHydrated = useSyncExternalStore(
    (onStoreChange) => {
      const unsubStart = useAuthStore.persist.onHydrate(onStoreChange);
      const unsubFinish = useAuthStore.persist.onFinishHydration(onStoreChange);
      return () => {
        unsubStart();
        unsubFinish();
      };
    },
    () => useAuthStore.persist.hasHydrated(),
    () => false
  );
  const [sidebarRoute, setSidebarRoute] = useState<string | null>(null);
  const [isGlobalSidebarCollapsed, setIsGlobalSidebarCollapsed] = useState(() => {
    if (typeof window === "undefined") return false;
    return localStorage.getItem(GLOBAL_SIDEBAR_COLLAPSED_STORAGE_KEY) === "true";
  });
  const [projectState, setProjectState] = useState<{ id: number; project: Project } | null>(null);
  const sidebarOpen = sidebarRoute === pathname;
  const currentProjectId = useMemo(() => {
    const match = pathname.match(/^\/projects\/(\d+)/);
    return match ? Number(match[1]) : null;
  }, [pathname]);
  const currentProject = projectState?.id === currentProjectId ? projectState.project : null;

  const handleSetGlobalSidebarCollapsed = (collapsed: boolean) => {
    setIsGlobalSidebarCollapsed(collapsed);
    localStorage.setItem(
      GLOBAL_SIDEBAR_COLLAPSED_STORAGE_KEY,
      collapsed ? "true" : "false"
    );
  };

  // 在 layout 层统一请求 project 数据，供桌面/移动端 SidebarNav 共享
  useEffect(() => {
    if (currentProjectId === null) {
      return;
    }
    let cancelled = false;
    projectApi.get(currentProjectId)
      .then((project) => {
        if (!cancelled) {
          setProjectState({ id: currentProjectId, project });
        }
      })
      .catch((error) => {
        if (!cancelled) toastApiError(error, "加载项目信息失败");
      });

    return () => {
      cancelled = true;
    };
  }, [currentProjectId]);

  // 运行时登出检测：用户主动登出后跳转到登录页
  // 初始进入时的认证保护由 middleware 处理
  useEffect(() => {
    if (authHydrated && !isAuthenticated) {
      router.replace("/login");
    }
  }, [authHydrated, isAuthenticated, router]);

  const ready = authHydrated && isAuthenticated;

  return (
    <div className="h-screen overflow-hidden flex flex-col bg-background">
        {/* 顶部导航:桌面顶栏 / 移动端窄屏顶栏(品牌+通知) */}
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={ready ? { opacity: 1, y: 0 } : { opacity: 0, y: -20 }}
          transition={{ duration: 0.5, ease: [0.25, 0.46, 0.45, 0.94] }}
        >
          <ClientErrorBoundary
            context="顶部导航运行异常"
            fallback={(_error, reset) => (
              <ErrorRegionFallback label="顶部导航加载失败" onRetry={reset} />
            )}
          >
            {isDesktopNav ? <AppHeader /> : <MobileHeader />}
          </ClientErrorBoundary>
        </motion.div>

        {/* 底部 Tab 栏(≤1024px) */}
        {ready && <MobileTabBar />}

        {/* 侧边栏 + 主内容 */}
        <motion.div
          className="flex pt-20 flex-1 min-h-0"
          initial={{ opacity: 0 }}
          animate={ready ? { opacity: 1 } : { opacity: 0 }}
          transition={{ duration: 0.4, delay: 0.15 }}
        >
          {/* 桌面端：浮动侧边栏卡片 */}
          {/* lg(1024px): ~240px card | xl(1280px): ~270px card | 2xl+(1440px): 300px card */}
          {ready && (
            <div
              className={cn(
                "hidden lg:block shrink-0 pt-1.5 self-start transition-[width,padding] duration-200 ease-out",
                isGlobalSidebarCollapsed
                  ? "w-20 px-3"
                  : "w-[clamp(272px,23vw,332px)] px-3"
              )}
            >
              <ClientErrorBoundary context="侧边栏运行异常">
                <SidebarNav
                  project={currentProject}
                  collapsed={isGlobalSidebarCollapsed}
                  onCollapsedChange={handleSetGlobalSidebarCollapsed}
                />
              </ClientErrorBoundary>
            </div>
          )}

          {/* 移动端：浮动抽屉 */}
          <AnimatePresence>
            {ready && sidebarOpen && (
              <>
                <motion.div
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.2 }}
                  className="fixed inset-0 z-40 bg-white/40 backdrop-blur-sm lg:hidden"
                  onClick={() => setSidebarRoute(null)}
                />
                <motion.div
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -20 }}
                  transition={{ duration: 0.2, ease: "easeOut" }}
                  className="fixed left-4 top-22 z-50 lg:hidden w-[60vw] min-w-[200px] max-w-[300px]"
                >
                  <ClientErrorBoundary context="移动侧边栏运行异常">
                    <SidebarNav
                      project={currentProject}
                      onNavigate={() => setSidebarRoute(null)}
                    />
                  </ClientErrorBoundary>
                </motion.div>
              </>
            )}
          </AnimatePresence>

          {/* 移动端页面菜单按钮(项目/设置等二级导航入口,悬浮于底部 Tab 栏上方) */}
          {ready && (
            <button
              onClick={() => setSidebarRoute(sidebarOpen ? null : pathname)}
              className={cn(
                "fixed left-3 bottom-20 z-60 lg:hidden",
                "h-11 w-11 rounded-full flex items-center justify-center",
                "bg-primary text-primary-foreground shadow-lg shadow-primary/20",
                "hover:shadow-primary/30 hover:scale-105",
                "active:scale-95 transition-all duration-200"
              )}
              aria-label={sidebarOpen ? "关闭菜单" : "打开页面菜单"}
            >
              {sidebarOpen ? (
                <X className="h-5 w-5" />
              ) : (
                <Menu className="h-5 w-5" />
              )}
            </button>
          )}

          {/* 主内容区;窄屏为底部 Tab 栏预留高度 */}
          <main className="dashboard-scroll flex flex-1 min-w-0 min-h-0 flex-col overflow-hidden pb-14 lg:pb-0">
            <OverlayScrollArea key={pathname} className="min-h-0 flex-1">
              <div className="dashboard-content flex min-h-full w-full shrink-0 flex-col pt-4">
                <ClientErrorBoundary
                  key={pathname}
                  context="当前页面运行异常"
                  fallback={(error, reset) => (
                    <ErrorRecoveryPanel
                      className="min-h-[60vh]"
                      title="当前页面发生异常"
                      description="顶部导航和侧边栏仍可使用；重试只会重新渲染当前页面。"
                      details={getClientErrorMessage(error)}
                      onRetry={reset}
                      onGoHome={() => router.push("/dashboard")}
                    />
                  )}
                >
                  {ready ? children : null}
                </ClientErrorBoundary>
              </div>
            </OverlayScrollArea>
          </main>
          {ready && (
            <ClientErrorBoundary context="助手运行异常">
              <AssistantDockSlot projectId={currentProjectId} />
            </ClientErrorBoundary>
          )}
        </motion.div>
      </div>
  );
}
