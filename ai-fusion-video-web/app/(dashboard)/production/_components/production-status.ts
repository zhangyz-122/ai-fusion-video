import type { ProductionQcStatus, ProductionRunStatus } from "@/lib/api/production";

export const runStatusFilters: Array<{ label: string; value: ProductionRunStatus | "" }> = [
  { label: "全部", value: "" },
  { label: "准备中", value: "CREATED" },
  { label: "生成中", value: "WAITING_GENERATION" },
  { label: "待质检", value: "QC_PENDING" },
  { label: "已选定", value: "SELECTED" },
  { label: "失败", value: "FAILED" },
];

export const runStatusLabels: Record<ProductionRunStatus, string> = {
  CREATED: "准备中",
  WAITING_GENERATION: "生成中",
  QC_PENDING: "待质检",
  SELECTED: "已选定",
  FAILED: "失败",
};

export const runStatusStyles: Record<ProductionRunStatus, string> = {
  CREATED: "text-muted-foreground bg-muted/30 border-border/30",
  WAITING_GENERATION: "text-cyan-600 bg-cyan-500/10 border-cyan-500/20",
  QC_PENDING: "text-amber-600 bg-amber-500/10 border-amber-500/20",
  SELECTED: "text-violet-600 bg-violet-500/10 border-violet-500/20",
  FAILED: "text-rose-600 bg-rose-500/10 border-rose-500/20",
};

export const qcLabels: Record<ProductionQcStatus, string> = {
  PASS: "通过",
  FAIL: "不通过",
  REVIEW_REQUIRED: "待检查",
};

export const qcStatusStyles: Record<ProductionQcStatus, string> = {
  PASS: "text-emerald-600 bg-emerald-500/10 border-emerald-500/20",
  FAIL: "text-rose-600 bg-rose-500/10 border-rose-500/20",
  REVIEW_REQUIRED: "text-amber-600 bg-amber-500/10 border-amber-500/20",
};

export const stepStatusLabels: Record<string, string> = {
  SUBMITTED: "已提交",
  SUCCEEDED: "已完成",
  FAILED: "失败",
};

export const repairStatusLabels: Record<string, string> = {
  PLANNED: "待执行",
  SUBMITTED: "已提交",
  BLOCKED: "已阻断",
};

export const repairRouteLabels: Record<string, string> = {
  RETRY_SAME_WORKFLOW: "沿用当前工作流重试",
  SWITCH_WORKFLOW: "切换工作流重试",
  MANUAL_REVIEW: "转人工处理",
  TERMINATE: "终止运行",
};

export function formatRunTime(value: string): string {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}
