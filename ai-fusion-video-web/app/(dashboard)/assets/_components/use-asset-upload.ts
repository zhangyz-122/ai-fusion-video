"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { assetApi } from "@/lib/api/asset";
import { getApiErrorMessage } from "@/lib/api/api-error";
import { uploadFile } from "@/lib/api/storage";
import { stripFileExtension } from "./utils";

// ============================================================
// 上传任务队列
// ============================================================

export type UploadTaskStatus = "uploading" | "success" | "failed";

export interface UploadTask {
  id: string;
  file: File;
  name: string;
  size: number;
  status: UploadTaskStatus;
  /** 失败原因（仅 failed 状态） */
  error?: string;
}

interface UseAssetUploadOptions {
  /** 上传目标项目；null 表示未选择具体项目 */
  projectId: number | null;
  /** 任一文件成功入库后回调（用于刷新资产列表） */
  onUploaded?: () => void;
}

/** 成功任务在队列中保留的时长，超时自动移除 */
const SUCCESS_RETENTION_MS = 4000;

/**
 * 图片上传队列：串行记录每个文件的上传状态，
 * 失败保留 File 引用供“重试”（重新上传整个文件，不做断点续传）。
 */
export function useAssetUpload(options: UseAssetUploadOptions) {
  const [tasks, setTasks] = useState<UploadTask[]>([]);
  const tasksRef = useRef<UploadTask[]>([]);
  const projectIdRef = useRef<number | null>(options.projectId);
  const onUploadedRef = useRef(options.onUploaded);
  const timersRef = useRef<Set<ReturnType<typeof setTimeout>>>(new Set());

  useEffect(() => {
    tasksRef.current = tasks;
    projectIdRef.current = options.projectId;
    onUploadedRef.current = options.onUploaded;
  }, [tasks, options.projectId, options.onUploaded]);

  const updateTask = useCallback(
    (id: string, patch: Partial<Omit<UploadTask, "id" | "file">>) => {
      setTasks((prev) =>
        prev.map((t) => (t.id === id ? { ...t, ...patch } : t)),
      );
    },
    [],
  );

  const runTask = useCallback(
    async (task: UploadTask) => {
      const projectId = projectIdRef.current;
      if (projectId == null) {
        toast.info("请先选择要上传到的项目");
        return;
      }
      updateTask(task.id, { status: "uploading", error: undefined });
      try {
        const url = await uploadFile(task.file, "assets");
        await assetApi.create({
          projectId,
          type: "image",
          name: task.name,
          coverUrl: url,
        });
        updateTask(task.id, { status: "success" });
        onUploadedRef.current?.();
        const timer = setTimeout(() => {
          setTasks((prev) => prev.filter((t) => t.id !== task.id));
          timersRef.current.delete(timer);
        }, SUCCESS_RETENTION_MS);
        timersRef.current.add(timer);
      } catch (error) {
        updateTask(task.id, {
          status: "failed",
          error: getApiErrorMessage(error) || "上传失败",
        });
      }
    },
    [updateTask],
  );

  const addFiles = useCallback(
    (files: FileList | File[]) => {
      if (projectIdRef.current == null) {
        toast.info("请先选择要上传到的项目");
        return;
      }
      const next: UploadTask[] = Array.from(files).map((file, i) => ({
        id: `${Date.now()}-${i}-${file.name}`,
        file,
        name: stripFileExtension(file.name),
        size: file.size,
        status: "uploading",
      }));
      if (next.length === 0) return;
      setTasks((prev) => [...prev, ...next]);
      next.forEach((task) => void runTask(task));
    },
    [runTask],
  );

  const retry = useCallback(
    (id: string) => {
      const task = tasksRef.current.find((t) => t.id === id);
      if (task) void runTask(task);
    },
    [runTask],
  );

  const dismiss = useCallback((id: string) => {
    setTasks((prev) => prev.filter((t) => t.id !== id));
  }, []);

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach((t) => clearTimeout(t));
      timers.clear();
    };
  }, []);

  return { tasks, addFiles, retry, dismiss };
}
