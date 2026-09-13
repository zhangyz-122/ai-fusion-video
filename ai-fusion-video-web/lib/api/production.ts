import { http } from "./client";

export type ProductionRunStatus =
  | "CREATED"
  | "WAITING_GENERATION"
  | "QC_PENDING"
  | "SELECTED"
  | "FAILED";

export type ProductionQcStatus = "PASS" | "FAIL" | "REVIEW_REQUIRED";

export interface ProductionRun {
  id: number;
  storyboardItemId: number;
  userId: number;
  projectId: number | null;
  idempotencyKey: string;
  status: ProductionRunStatus;
  selectedTakeId: number | null;
  failureCode: string | null;
  failureMessage: string | null;
  createTime: string;
  updateTime: string;
}

export interface ProductionStep {
  id: number;
  runId: number;
  stepType: string;
  status: string;
  videoTaskId: number | null;
  workflowProfileId: number | null;
  workflowVersionId: number | null;
  executionRef: string | null;
  attempt: number;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface ProductionTake {
  id: number;
  runId: number;
  storyboardItemId: number;
  videoItemId: number;
  takeIndex: number;
  qcStatus: ProductionQcStatus;
  qcNote: string | null;
  /** 候选视频播放地址（来自 VideoItem，可能尚未生成） */
  videoUrl: string | null;
  /** 候选视频封面地址 */
  coverUrl: string | null;
  /** 底层 VideoItem 状态 */
  videoStatus: number | null;
  /** 底层 VideoItem 错误信息 */
  videoErrorMsg: string | null;
  createTime: string;
  updateTime: string;
}

export interface ProductionVideoTask {
  id: number;
  taskId: string;
  status: number;
  errorMsg: string | null;
  count: number;
}

export interface ProductionRepairAttempt {
  id: number;
  runId: number;
  sourceStepId: number;
  parentAttemptId: number | null;
  attemptNo: number;
  route: string;
  status: string;
  failureCode: string;
  reason: string | null;
  retryBudget: number;
  idempotencyKey: string;
  replacementVideoTaskId: number | null;
  replacementExecutionRef: string | null;
}

export interface ProductionRunDetail {
  run: ProductionRun;
  step: ProductionStep | null;
  videoTask: ProductionVideoTask | null;
  takes: ProductionTake[];
  qcResults?: unknown[];
  repairAttempts?: ProductionRepairAttempt[];
}

export interface ProductionRunPage {
  list: ProductionRun[];
  total: number;
}

export interface ProductionStartReq {
  storyboardItemId: number;
  idempotencyKey: string;
  prompt?: string;
  modelId?: number;
  workflowProfileId?: number;
  generateMode?: string;
  firstFrameImageUrl?: string | null;
  lastFrameImageUrl?: string | null;
  ratio?: string;
  resolution?: string;
  duration?: number;
  seed?: number;
}

export interface ShotReadinessBlocker {
  code: string;
  message: string;
}

export interface ShotReadiness {
  ready: boolean;
  blockers: ShotReadinessBlocker[];
}

export const productionApi = {
  readiness: (storyboardItemId: number) =>
    http.get<never, ShotReadiness>(`/api/production/shots/${storyboardItemId}/readiness`),
  /** 当前用户的生产运行分页列表，可按状态过滤 */
  list: (params?: { status?: ProductionRunStatus; pageNo?: number; pageSize?: number }) => {
    const query = new URLSearchParams();
    if (params?.status) query.set("status", params.status);
    query.set("pageNo", String(params?.pageNo ?? 1));
    query.set("pageSize", String(params?.pageSize ?? 10));
    return http.get<never, ProductionRunPage>(`/api/production/runs?${query.toString()}`);
  },
  start: (data: ProductionStartReq) =>
    http.post<never, ProductionRunDetail>("/api/production/runs", data),
  detail: (runId: number) =>
    http.get<never, ProductionRunDetail>(`/api/production/runs/${runId}`),
  reconcile: (runId: number) =>
    http.post<never, ProductionRunDetail>(
      `/api/production/runs/${runId}/reconcile`
    ),
  repair: (runId: number) =>
    http.post<never, ProductionRunDetail>(`/api/production/runs/${runId}/repair`),
  updateQc: (
    runId: number,
    takeId: number,
    data: { qcStatus: ProductionQcStatus; qcNote?: string | null }
  ) =>
    http.put<never, ProductionRunDetail>(
      `/api/production/runs/${runId}/takes/${takeId}/qc`,
      data
    ),
  selectTake: (runId: number, takeId: number) =>
    http.post<never, ProductionRunDetail>(
      `/api/production/runs/${runId}/takes/${takeId}/select`
    ),
  compose: (runId: number) =>
    http.post<never, string>(`/api/production/runs/${runId}/compose`),
};
