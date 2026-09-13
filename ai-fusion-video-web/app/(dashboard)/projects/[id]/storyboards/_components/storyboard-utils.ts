import type { StoryboardScene, StoryboardItem } from "@/lib/api/storyboard";

// ========== 页面级类型 ==========

export type ViewMode = "table" | "card";

export interface SidebarSelection {
  type: "all" | "episode" | "scene";
  episodeId?: number;
  sceneId?: number;
}

/** 场次及其条目 */
export interface SceneWithItems {
  scene: StoryboardScene;
  items: StoryboardItem[];
}

// ========== 本地偏好存储 ==========

export const VIEW_MODE_STORAGE_KEY = "fusion-storyboard-view-mode";
export const SIDEBAR_COLLAPSED_STORAGE_KEY = "fusion-storyboard-sidebar-collapsed";

/** 读取持久化的视图模式,未设置或非法时返回 null */
export function readStoredViewMode(): ViewMode | null {
  const savedMode = localStorage.getItem(VIEW_MODE_STORAGE_KEY);
  return savedMode === "table" || savedMode === "card" ? savedMode : null;
}

export function writeStoredViewMode(mode: ViewMode) {
  localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode);
}

/** 读取侧边栏折叠偏好 */
export function readStoredSidebarCollapsed(): boolean {
  return localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === "true";
}

export function writeStoredSidebarCollapsed(collapsed: boolean) {
  localStorage.setItem(
    SIDEBAR_COLLAPSED_STORAGE_KEY,
    collapsed ? "true" : "false"
  );
}

// ========== 派生与滚动工具 ==========

/** 当前选中的 episodeId(episode 或 scene 选择都会有) */
export function deriveCurrentEpisodeId(selection: SidebarSelection): number | null {
  return selection.type === "episode" || selection.type === "scene"
    ? selection.episodeId ?? null
    : null;
}

/**
 * 计算滚动后应激活的场次 ID;返回 null 表示保持现状。
 * 调用方需保证 sceneGroups 非空。
 */
export function resolveActiveSceneIdOnScroll(
  container: HTMLDivElement,
  sceneGroups: SceneWithItems[],
  sceneRefs: Record<number, HTMLDivElement | null>
): number | null {
  const scrollTop = container.scrollTop;
  const scrollHeight = container.scrollHeight;
  const clientHeight = container.clientHeight;

  // 1. 如果已滚动到最顶部，直接激活第一个场次
  if (scrollTop === 0) {
    return sceneGroups[0].scene.id;
  }

  // 2. 如果已滚动到最底部（解决短场次或大屏幕下，最末尾场次无法卷到顶部触发激活线的问题）
  if (scrollTop + clientHeight >= scrollHeight - 15) {
    return sceneGroups[sceneGroups.length - 1].scene.id;
  }

  // 3. 普通滚动过程中，使用较为灵敏的激活线（容器高度的 35%，最大不超过 300px）
  const containerRect = container.getBoundingClientRect();
  let activeId: number | null = null;
  let minDiff = Infinity;
  const triggerY = Math.min(300, containerRect.height * 0.35);

  for (const { scene } of sceneGroups) {
    const el = sceneRefs[scene.id];
    if (!el) continue;
    const rect = el.getBoundingClientRect();
    const relativeTop = rect.top - containerRect.top;
    const relativeBottom = rect.bottom - containerRect.top;

    // 判断该场次是否跨越容器顶部的激活线
    if (relativeTop <= triggerY && relativeBottom > triggerY) {
      activeId = scene.id;
      break;
    }

    // 备选：如果没有跨越激活线的，记录离激活线最近的一个
    const diff = Math.abs(relativeTop - triggerY);
    if (diff < minDiff) {
      minDiff = diff;
      activeId = scene.id;
    }
  }

  return activeId;
}
