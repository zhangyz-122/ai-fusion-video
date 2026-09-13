import { http } from "./client";

// ========== 类型定义 ==========

/** 能力目录条目（公共只读，已脱敏） */
export interface CapabilityCatalogItem {
  id: number;
  name: string;
  description: string | null;
  /** 来源类型：MODEL=已配置模型，WORKFLOW=工作流能力 */
  source: "MODEL" | "WORKFLOW";
  enabled: boolean;
  /** 是否默认能力（仅模型条目有意义） */
  defaultCapability: boolean;
  /** 不可用原因（仅待接入条目有值） */
  pendingReason: string | null;
}

// ========== API ==========

export const capabilityApi = {
  /** 按模型类型查询能力目录（已启用与待接入），任一登录用户可调用 */
  catalog: (modelType: number) =>
    http.get<never, CapabilityCatalogItem[]>(`/api/ai/capability/catalog?modelType=${modelType}`),
};
