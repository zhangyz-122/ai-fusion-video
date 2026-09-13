"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { buttonVariants } from "@/components/ui/button";

export default function ProjectDeliveryPage() {
  const params = useParams();
  const projectId = Number(params.id);

  return (
    <div className="mx-auto w-full max-w-4xl space-y-6 px-6 py-6">
      <header className="space-y-2">
        <p className="text-sm text-muted-foreground">剪 · 成片</p>
        <h1 className="text-2xl font-semibold tracking-tight">合成与交付</h1>
        <p className="text-sm text-muted-foreground">
          先在分镜里给每一镜选出 QC 通过的 Take，再用选中结果合成这一集。
        </p>
      </header>
      <section className="space-y-4 rounded-xl border border-border/30 bg-card p-6">
        <p className="text-sm text-muted-foreground">成片时间轴会接在现有合成服务上，不另做一套剪辑软件。</p>
        <Link className={buttonVariants()} href={`/projects/${projectId}/storyboards`}>
          去分镜选片并合成
        </Link>
      </section>
    </div>
  );
}
