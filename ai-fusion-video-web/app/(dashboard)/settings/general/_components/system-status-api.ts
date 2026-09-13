// 系统状态区块的接口类型、请求函数与纯格式化工具
import { http } from "@/lib/api/client";

export interface SystemHealth {
  status: "UP" | "DOWN";
  checkedAt: string;
  components: {
    backend: { status: string };
    database: { status: string };
  };
}

export interface MediaDirStats {
  fileCount: number;
  totalBytes: number;
}

export interface MediaStorageStats {
  storageType: string;
  basePath: string;
  exists: boolean;
  totalFiles: number;
  totalBytes: number;
  categories: {
    images: MediaDirStats;
    videos: MediaDirStats;
    composed: MediaDirStats;
  };
}

export interface VideoQueueStat {
  name: string;
  pending: number;
  running: number;
  maxConcurrent: number;
}

export interface VideoQueueStatus {
  available: boolean;
  error: string | null;
  totalPending: number;
  totalRunning: number;
  queues: VideoQueueStat[];
}

export function getSystemHealth(): Promise<SystemHealth> {
  return http.get("/api/system/status/health");
}

export function getMediaStorageStats(): Promise<MediaStorageStats> {
  return http.get("/api/system/status/storage");
}

export function getVideoQueueStatus(): Promise<VideoQueueStatus> {
  return http.get("/api/system/status/video-queue");
}

/** 字节数格式化为人类可读大小，保留一位小数 */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || Number.isNaN(bytes)) return "--";
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB", "PB"];
  let value = bytes;
  let unitIndex = -1;
  do {
    value /= 1024;
    unitIndex++;
  } while (value >= 1024 && unitIndex < units.length - 1);
  return `${value.toFixed(1)} ${units[unitIndex]}`;
}

/** 队列名缩短展示：video_generation:model:7 → 模型 7 */
export function shortQueueName(name: string): string {
  if (name.startsWith("video_generation:model:")) {
    return `模型 ${name.slice("video_generation:model:".length)}`;
  }
  return name;
}
