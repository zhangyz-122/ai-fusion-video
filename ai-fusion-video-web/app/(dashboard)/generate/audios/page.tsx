"use client";

import { useEffect, useState } from "react";
import type { ComfyUiWorkflow } from "@/lib/api/comfyui-workflow";
import { audioWorkshopApi } from "@/lib/api/audio";
import { useAuthStore } from "@/lib/store/auth-store";
import {
  AudioCapabilityAdminLink,
  AudioCapabilityCard,
} from "./_components/audio-capability-card";

export default function GenerateAudiosPage() {
  const isAdmin = useAuthStore(s => s.user?.roles?.includes("admin") ?? false);
  const [workflow, setWorkflow] = useState<ComfyUiWorkflow | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const data = await audioWorkshopApi.getCapabilityWorkflow();
        if (active) setWorkflow(data);
      } catch {
        // 接口仅管理员可用:普通用户或异常时降级为"能力接入中"占位,不报错。
        if (active) setWorkflow(null);
      }
      if (active) setLoading(false);
    }
    void load();
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="mx-auto w-full max-w-6xl space-y-6 lg:space-y-8 px-5 py-4 lg:px-6 lg:py-6">
      <header className="space-y-2">
        <p className="text-sm text-muted-foreground">创作工作台 / 声音</p>
        <h1 className="text-2xl lg:text-3xl font-semibold tracking-tight">声音工坊</h1>
        <p className="text-muted-foreground">配音、对白、音乐与音效能力(建设中)。</p>
      </header>

      <section aria-labelledby="audio-capability-status" className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="audio-capability-status" className="text-xl font-semibold">
            能力状态
          </h2>
          {isAdmin && !loading && workflow && <AudioCapabilityAdminLink />}
        </div>
        <p className="text-sm text-muted-foreground">
          对白驱动的口播视频由 INFINITETALK 工作流提供,以下为平台实时接入状态。
        </p>
        <AudioCapabilityCard loading={loading} workflow={workflow} />
      </section>

      <p className="border-t border-border/20 pt-6 text-sm text-muted-foreground">
        G01 批次实现中:配音编辑器、对白管理与试听将在此提供。
      </p>
    </div>
  );
}
