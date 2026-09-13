"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { capabilityApi, type CapabilityCatalogItem } from "@/lib/api/capability";
import { useAuthStore } from "@/lib/store/auth-store";
import { Button, buttonVariants } from "@/components/ui/button";

export default function ImageStudio() {
  const isAdmin = useAuthStore((s) => s.user?.roles?.includes("admin") ?? false);
  const [catalog, setCatalog] = useState<CapabilityCatalogItem[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      setError("");
      try {
        const items = await capabilityApi.catalog(2);
        if (active) setCatalog(items);
      } catch {
        if (active) setError("无法读取能力目录，请检查服务后重试。");
      }
      if (active) setLoading(false);
    }
    void load();
    return () => {
      active = false;
    };
  }, [revision]);

  const models = catalog.filter((item) => item.source === "MODEL" && item.enabled);
  const pending = catalog.filter((item) => item.source === "WORKFLOW");

  return (
    <div className="mx-auto w-full max-w-6xl space-y-8 px-6 py-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">创作工作台 / 图像</p>
          <h1 className="text-3xl font-semibold tracking-tight">图像工坊</h1>
          <p className="text-muted-foreground">选择生图能力，进入编辑器制作与预览。原有生成记录保持不变。</p>
        </div>
        <Link href="/assets" className={buttonVariants({ variant: "outline" })}>查看资产</Link>
      </header>

      <section aria-labelledby="image-models" className="space-y-4">
        <h2 id="image-models" className="text-xl font-semibold">已配置的生图能力</h2>
        <p className="text-sm text-muted-foreground">以下状态来自平台配置，不代表本轮已完成出图测试。参考图数量和参数以编辑器为准。</p>
        {loading && <p role="status">正在读取能力目录…</p>}
        {error && (
          <div role="alert" className="space-y-3">
            <p>{error}</p>
            <Button variant="outline" onClick={() => setRevision((v) => v + 1)}>重试</Button>
          </div>
        )}
        {!loading && !error && models.length === 0 && (
          <p className="text-muted-foreground">暂无已启用的生图模型，请联系管理员完成配置。</p>
        )}
        {!error && (
          <div className="grid gap-4 md:grid-cols-2">
            {models.map((model) => (
              <article key={model.id} className="flex flex-col gap-4 rounded-xl border border-border/30 bg-card p-5">
                <div className="space-y-2">
                  <span className="text-xs text-muted-foreground">已启用配置{model.defaultCapability ? " · 默认" : ""}</span>
                  <h3 className="text-lg font-medium">{model.name}</h3>
                  <p className="text-sm text-muted-foreground">{model.description || "参数与参考图要求将在生成编辑器中展示。"}</p>
                </div>
                <div className="mt-auto">
                  <Link className={buttonVariants({ variant: "outline" })} href={`/generate/image?modelId=${model.id}`}>使用此模型</Link>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="pending-workflows" className="space-y-4 border-t border-border/20 pt-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="pending-workflows" className="text-xl font-semibold">待接入能力</h2>
          {isAdmin && <Link className={buttonVariants({ variant: "outline" })} href="/settings/ai-models">管理工作流</Link>}
        </div>
        <p className="text-sm text-muted-foreground">正在接入中的能力不会出现在生成列表；完成配置后会自动进入上方目录。</p>
        {!loading && (
          <ul className="divide-y divide-border/20">
            {pending.map((w) => (
              <li key={w.id} className="space-y-2 py-4">
                <div className="flex flex-wrap items-center gap-3">
                  <h3 className="font-medium">{w.name}</h3>
                  <span className="text-xs text-muted-foreground">{w.pendingReason ?? "待接入"}</span>
                </div>
                <p className="text-sm text-muted-foreground">{w.description}</p>
              </li>
            ))}
          </ul>
        )}
        {!loading && pending.length === 0 && <p className="text-sm text-muted-foreground">暂无待接入能力。</p>}
      </section>
    </div>
  );
}
