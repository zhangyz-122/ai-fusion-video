import { http } from "./client";

/**
 * AI Drama OS Production API 类型与调用封装。
 * 字段命名与后端实体保持一致（camelCase），JSON 列以字符串下发后在使用处解析。
 */

export interface ProductionRun {
  id: number;
  projectId: number;
  storyboardId: number;
  storyboardEpisodeId: number | null;
  runType: string;
  status: string;
  idempotencyKey: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  metadataJson: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ProductionTakeSourceType = "IMAGE_ITEM" | "VIDEO_ITEM";
export type ProductionTakeQcStatus = "PENDING" | "PASS" | "FAIL" | "REVIEW_REQUIRED";

export interface ProductionTake {
  id: number;
  runId: number;
  storyboardItemId: number;
  sourceType: ProductionTakeSourceType;
  sourceItemId: number;
  workflowProfileId: number | null;
  workflowVersionId: number | null;
  modelId: string | null;
  seed: number | null;
  qcStatus: ProductionTakeQcStatus;
  metadataJson: string | null;
  createdAt: string;
}

/** afv_production_take.metadata_json 的结构 */
export interface ProductionTakeMetadata {
  videoUrl?: string;
  firstFrameUrl?: string;
  lastFrameUrl?: string;
  duration?: number;
}

export interface QcResult {
  id: number;
  takeId: number;
  criterion: string;
  verdict: "PASS" | "FAIL" | "REVIEW_REQUIRED";
  valueScore: number | null;
  thresholdValue: number | null;
  evidenceUrl: string | null;
  evidenceJson: string | null;
  reviewedBy: number | null;
  overrideReason: string | null;
  createdAt: string;
}

export interface ProductionSummary {
  itemId: number;
  productionStatus: string | null;
  selectedTakeId: number | null;
  workflowProfileId: number | null;
  videoUrl: string | null;
  firstFrameUrl: string | null;
  lastFrameUrl: string | null;
}

/** metadata_json 解析失败时按空对象处理，避免列表整体渲染中断 */
export function parseTakeMetadata(metadataJson: string | null): ProductionTakeMetadata {
  if (!metadataJson) return {};
  try {
    return JSON.parse(metadataJson) as ProductionTakeMetadata;
  } catch {
    return {};
  }
}

export const productionApi = {
  getRuns: (projectId: number) =>
    http.get<never, ProductionRun[]>("/api/production/runs", { params: { projectId } }),

  getTakesByItem: (itemId: number) =>
    http.get<never, ProductionTake[]>(`/api/production/takes/item/${itemId}`),

  selectTake: (storyboardItemId: number, takeId: number) =>
    http.post<never, ProductionTake>("/api/production/takes/select", { storyboardItemId, takeId }),

  deselectTake: (itemId: number) =>
    http.delete<never, void>(`/api/production/takes/deselect/${itemId}`),

  evaluateTakeQc: (takeId: number) =>
    http.post<never, ProductionTake>(`/api/production/takes/${takeId}/qc`),

  getTakeQcResults: (takeId: number) =>
    http.get<never, QcResult[]>(`/api/production/takes/${takeId}/qc`),

  getSummary: (itemId: number) =>
    http.get<never, ProductionSummary>(`/api/storyboard/item/${itemId}/production-summary`),
};
