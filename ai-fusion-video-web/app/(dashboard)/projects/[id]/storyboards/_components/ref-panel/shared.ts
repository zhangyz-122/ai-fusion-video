import { MapPin, Package, Users } from "lucide-react";

import type { AssetItem } from "@/lib/api/asset";
import type { StoryboardItem, StoryboardScene } from "@/lib/api/storyboard";

// ========== 类型 ==========

export interface SceneWithItems {
  scene: StoryboardScene;
  items: StoryboardItem[];
}

/** 带主资产名称和类型的子资产 */
export interface AssetItemWithParent extends AssetItem {
  parentName: string;
  parentType: string;
}

/** 资产分组 */
export interface GroupedAssets {
  characters: AssetItemWithParent[];
  scenes: AssetItemWithParent[];
  props: AssetItemWithParent[];
}

export interface BatchFrameGeneratePayload {
  episodeId: number;
  sceneId: number;
  firstItemIds: number[];
  lastItemIds: number[];
  overwriteExisting: boolean;
}

export type BatchFrameGenerateHandler = (
  payload: BatchFrameGeneratePayload
) => Promise<void> | void;

// ========== 常量 ==========

export const typeConfig = {
  character: {
    label: "角色",
    icon: Users,
    color: "text-blue-400",
    bgColor: "bg-blue-500/10",
  },
  scene: {
    label: "场景",
    icon: MapPin,
    color: "text-green-400",
    bgColor: "bg-green-500/10",
  },
  prop: {
    label: "道具",
    icon: Package,
    color: "text-amber-400",
    bgColor: "bg-amber-500/10",
  },
} as const;
