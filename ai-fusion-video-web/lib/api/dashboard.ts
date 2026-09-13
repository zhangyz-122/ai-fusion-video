import { http } from "./client";

// ========== 类型定义 ==========

export interface DashboardActivityItem {
  /** 条目种类：IMAGE_TASK / VIDEO_TASK / PRODUCTION_RUN */
  kind: string;
  refId: number | null;
  refKey: string | null;
  title: string;
  detail: string;
  projectId: number | null;
  createTime: string | null;
}

export interface DashboardActivity {
  running: DashboardActivityItem[];
  pending: DashboardActivityItem[];
}

// ========== API ==========

export const dashboardApi = {
  /** 当前用户的进行中任务与待处理事项 */
  activity: () => http.get<never, DashboardActivity>("/api/dashboard/activity"),
};
