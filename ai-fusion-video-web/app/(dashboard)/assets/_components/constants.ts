import {
  Users,
  MapPin,
  Wrench,
  Image as ImageIcon,
  Package,
  type LucideIcon,
} from "lucide-react";

// ============================================================
// 资产类型元数据
// ============================================================

export interface AssetTypeMeta {
  label: string;
  icon: LucideIcon;
  color: string;
  bg: string;
  /** SafeImage 占位图类型 */
  fallback: "avatar" | "scene" | "prop" | "image";
}

export const ASSET_TYPE_META: Record<string, AssetTypeMeta> = {
  character: {
    label: "角色",
    icon: Users,
    color: "text-blue-400",
    bg: "bg-blue-500/10",
    fallback: "avatar",
  },
  scene: {
    label: "场景",
    icon: MapPin,
    color: "text-green-400",
    bg: "bg-green-500/10",
    fallback: "scene",
  },
  prop: {
    label: "道具",
    icon: Wrench,
    color: "text-amber-400",
    bg: "bg-amber-500/10",
    fallback: "prop",
  },
  image: {
    label: "图片",
    icon: ImageIcon,
    color: "text-violet-400",
    bg: "bg-violet-500/10",
    fallback: "image",
  },
};

/** 统计卡片展示的类型顺序 */
export const ASSET_TYPE_ORDER = ["character", "scene", "prop", "image"] as const;

/** 未知类型的兜底元数据 */
export const FALLBACK_TYPE_META: AssetTypeMeta = {
  label: "资产",
  icon: Package,
  color: "text-muted-foreground",
  bg: "bg-muted/40",
  fallback: "image",
};

export function getTypeMeta(type: string): AssetTypeMeta {
  return ASSET_TYPE_META[type] ?? { ...FALLBACK_TYPE_META, label: type };
}

// ============================================================
// 数据加载与本地存储
// ============================================================

/** 全量加载时的分页大小 */
export const FETCH_PAGE_SIZE = 200;

/** 全量加载的资产数量上限，超过则截断并提示 */
export const MAX_LOADED_ASSETS = 1000;

/** 网格/列表视图偏好存储键 */
export const VIEW_MODE_STORAGE_KEY = "rg:assets:view-mode:v1";

/** 上传允许的图片类型（与后端 /api/storage/upload 校验一致） */
export const UPLOAD_ACCEPT = "image/png,image/jpeg,image/jpg,image/webp,image/gif";
