"use client";

import { usePathname, useRouter } from "next/navigation";
import {
  Factory,
  FolderKanban,
  LayoutDashboard,
  Sparkles,
  UserRound,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";

interface MobileTabItem {
  label: string;
  icon: LucideIcon;
  href: string;
  /** 当前路由是否归属该入口 */
  match: (pathname: string) => boolean;
}

/**
 * 移动端底部主导航(≤1024px 显示)。
 * 五大入口:仪表盘 / 项目 / 创作 / 生产 / 我的;桌面端由侧边栏承载,本组件不渲染。
 */
const TAB_ITEMS: MobileTabItem[] = [
  {
    label: "仪表盘",
    icon: LayoutDashboard,
    href: "/dashboard",
    match: (p) => p.startsWith("/dashboard"),
  },
  {
    label: "项目",
    icon: FolderKanban,
    href: "/projects",
    match: (p) => p.startsWith("/projects") || p.startsWith("/assets"),
  },
  {
    label: "创作",
    icon: Sparkles,
    href: "/generate",
    match: (p) => p.startsWith("/generate"),
  },
  {
    label: "生产",
    icon: Factory,
    href: "/production",
    match: (p) => p.startsWith("/production"),
  },
  {
    label: "我的",
    icon: UserRound,
    href: "/settings",
    match: (p) => p.startsWith("/settings"),
  },
];

export function MobileTabBar() {
  const router = useRouter();
  const pathname = usePathname();

  return (
    <nav
      aria-label="底部主导航"
      className="fixed inset-x-0 bottom-0 z-50 border-t border-border/30 bg-background/85 backdrop-blur-xl lg:hidden"
    >
      <div className="mx-auto flex w-full max-w-xl items-stretch pb-[env(safe-area-inset-bottom)]">
        {TAB_ITEMS.map((item) => {
          const Icon = item.icon;
          const active = item.match(pathname);
          return (
            <button
              key={item.label}
              type="button"
              aria-current={active ? "page" : undefined}
              onClick={() => router.push(item.href)}
              className={cn(
                "flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 rounded-xl px-1 outline-none",
                "transition-colors focus-visible:ring-[3px] focus-visible:ring-ring/50 motion-reduce:transition-none",
                active ? "text-primary" : "text-muted-foreground"
              )}
            >
              <span
                className={cn(
                  "flex h-6.5 w-12 items-center justify-center rounded-full transition-colors motion-reduce:transition-none",
                  active && "bg-primary/10"
                )}
              >
                <Icon className="h-5 w-5" />
              </span>
              <span className={cn("text-[10px] leading-none", active && "font-medium")}>
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
