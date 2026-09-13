import type { Asset } from "@/lib/api/asset";

// ============================================================
// 标签与属性解析
// ============================================================

/**
 * 解析资产的 tags 负载。
 * 后端 tags 列为 JSON 字符串，经实体直接序列化后可能是
 * JSON 字符串（如 "[\"a\",\"b\"]"）、数组或 null。
 */
export function parseAssetTags(raw: unknown): string[] {
  if (Array.isArray(raw)) {
    return raw
      .filter((t): t is string => typeof t === "string")
      .map((t) => t.trim())
      .filter(Boolean);
  }
  if (typeof raw === "string" && raw.trim()) {
    try {
      return parseAssetTags(JSON.parse(raw));
    } catch {
      return [];
    }
  }
  return [];
}

/** 从资产集合统计标签频次，按频次降序（同频按名称）截断 */
export function buildTagCloud(
  assets: Asset[],
  limit = 24,
): { tag: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const asset of assets) {
    for (const tag of parseAssetTags(asset.tags)) {
      counts.set(tag, (counts.get(tag) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count || a.tag.localeCompare(b.tag))
    .slice(0, limit);
}

/** 关键词匹配：名称或任一标签（不区分大小写） */
export function matchesKeyword(asset: Asset, keyword: string): boolean {
  const kw = keyword.trim().toLowerCase();
  if (!kw) return true;
  if (asset.name?.toLowerCase().includes(kw)) return true;
  return parseAssetTags(asset.tags).some((t) => t.toLowerCase().includes(kw));
}

// ============================================================
// 展示格式化
// ============================================================

export function formatDate(dateStr: string): string {
  if (!dateStr) return "";
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHour = Math.floor(diffMs / 3600000);
  const diffDay = Math.floor(diffMs / 86400000);

  if (diffMin < 1) return "刚刚";
  if (diffMin < 60) return `${diffMin} 分钟前`;
  if (diffHour < 24) return `${diffHour} 小时前`;
  if (diffDay < 7) return `${diffDay} 天前`;
  return date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${Math.round(kb)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

/** 文件名去掉扩展名，作为上传资产的默认名称 */
export function stripFileExtension(fileName: string): string {
  const base = fileName.replace(/\.[^.]+$/, "").trim();
  return base || fileName;
}
