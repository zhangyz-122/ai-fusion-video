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

export const productionApi = {
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
