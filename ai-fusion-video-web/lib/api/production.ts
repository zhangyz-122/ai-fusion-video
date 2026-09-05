/**
 * AI Drama OS Production API — types + fetch wrappers
 * PR-014: Frontend Production API/Types
 */

export interface ProductionRun {
  id: number;
  project_id: number;
  storyboard_id: number;
  storyboard_episode_id?: number;
  run_type: string;
  status: string;
  idempotency_key?: string;
  started_at?: string;
  finished_at?: string;
  metadata_json?: Record<string, unknown>;
}

export interface ProductionStep {
  id: number;
  run_id: number;
  storyboard_item_id: number;
  step_type: 'GENERATE_IMAGE' | 'GENERATE_VIDEO' | 'QC' | 'MEDIA' | 'HUMAN';
  status: 'PENDING' | 'READY' | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'RETRYING' | 'BLOCKED' | 'REVIEW_REQUIRED' | 'APPROVED' | 'CANCELLED';
  execution_type: 'IMAGE' | 'VIDEO' | 'QC' | 'MEDIA' | 'HUMAN';
  execution_ref_id?: number;
  attempt: number;
  error_code?: string;
  error_message?: string;
}

export interface ProductionTake {
  id: number;
  run_id: number;
  storyboard_item_id: number;
  source_type: 'IMAGE_ITEM' | 'VIDEO_ITEM';
  source_item_id: number;
  workflow_profile_id?: number;
  workflow_version_id?: number;
  model_id?: string;
  seed?: number;
  qc_status: 'PENDING' | 'PASS' | 'FAIL' | 'REVIEW_REQUIRED';
  metadata_json?: {
    videoUrl?: string;
    firstFrameUrl?: string;
    lastFrameUrl?: string;
    duration?: number;
  };
}

export interface ProductionSummary {
  itemId: number;
  productionStatus: string;
  selectedTakeId?: number;
  workflowProfileId?: number;
  videoUrl?: string;
  firstFrameUrl?: string;
  lastFrameUrl?: string;
}

const API_BASE = '/api';

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, { ...init, headers: { 'Content-Type': 'application/json', ...init?.headers } });
  const json = await res.json();
  if (json.code !== '0000') throw new Error(json.msg || `HTTP ${res.status}`);
  return json.data as T;
}

export async function getProductionSummary(itemId: number): Promise<ProductionSummary> {
  return fetchJson(`${API_BASE}/storyboard/item/${itemId}/production-summary`);
}

export async function getProductionRuns(projectId: number): Promise<ProductionRun[]> {
  return fetchJson(`${API_BASE}/video-fusion/production/runs?projectId=${projectId}`);
}

export async function getTakesForItem(itemId: number): Promise<ProductionTake[]> {
  return fetchJson(`${API_BASE}/video-fusion/production/takes?storyboardItemId=${itemId}`);
}

export async function selectTake(itemId: number, takeId: number): Promise<void> {
  await fetchJson(`${API_BASE}/video-fusion/production/select-take`, {
    method: 'POST',
    body: JSON.stringify({ storyboardItemId: itemId, takeId }),
  });
}
