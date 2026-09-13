import type { StoryboardItem } from "@/lib/api/storyboard";

/** 安全解析 ID 数组（兼容后端返回的 JSON 字符串或原生数组） */
export function parseIds(raw: number[] | string | null | undefined): number[] {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  if (typeof raw === "string") {
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
}

const storyboardItemCollator = new Intl.Collator("zh-CN", {
  numeric: true,
  sensitivity: "base",
});

/** 分镜条目排序所需的字段(StoryboardItem 的结构子集,便于测试与复用) */
export type SortableStoryboardItem = Pick<
  StoryboardItem,
  "id" | "sortOrder" | "shotNumber" | "autoShotNumber"
>;

/** 分镜条目升序比较:sortOrder → 镜号(中文数字感知) → id */
export function compareStoryboardItemsAsc(
  a: SortableStoryboardItem,
  b: SortableStoryboardItem
) {
  const aSortOrder = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
  const bSortOrder = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
  if (aSortOrder !== bSortOrder) return aSortOrder - bSortOrder;

  const aShotNumber = a.shotNumber || a.autoShotNumber || "";
  const bShotNumber = b.shotNumber || b.autoShotNumber || "";
  const shotCompare = storyboardItemCollator.compare(aShotNumber, bShotNumber);
  if (shotCompare !== 0) return shotCompare;

  return a.id - b.id;
}
