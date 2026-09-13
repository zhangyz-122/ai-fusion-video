"use client";

import Link from "next/link";
import { ArrowRight, ImagePlus, Film, FolderKanban } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";

export default function CreativeHome() {
  return (
    <div className="mx-auto w-full max-w-6xl space-y-8 px-6 py-6">
      <header className="space-y-3">
        <p className="text-sm text-muted-foreground">创作工作台</p>
        <h1 className="text-3xl font-semibold tracking-tight">从一个想法，到一段作品</h1>
        <p className="text-muted-foreground">先制作素材，或进入项目继续编排。生成记录与资产沿用现有系统。</p>
      </header>
      <section className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 rounded-xl border border-border/30 bg-card p-6 lg:col-span-2">
          <ImagePlus className="h-8 w-8 text-primary" />
          <div className="space-y-2"><h2 className="text-2xl font-semibold">图像工坊</h2><p className="text-muted-foreground">制作角色、场景和分镜参考图。查看已配置模型，以及正在接入的图片编辑工作流。</p></div>
          <Link className={buttonVariants()} href="/generate/images">开始图像创作 <ArrowRight /></Link>
        </div>
        <div className="space-y-6 rounded-xl border border-border/30 bg-card/50 p-6 backdrop-blur-sm">
          <Film className="h-6 w-6 text-muted-foreground" />
          <div className="space-y-2"><h2 className="text-xl font-semibold">视频创作</h2><p className="text-sm text-muted-foreground">继续使用现有文戏生成与视频工具。本批界面改造不变更运行配置。</p></div>
          <Link className={buttonVariants({variant:"outline"})} href="/generate/video">进入视频生成</Link>
        </div>
      </section>
      <section className="flex flex-wrap items-center justify-between gap-4 border-t border-border/20 pt-6">
        <div className="flex items-center gap-3"><FolderKanban className="h-5 w-5" /><div><h2 className="font-medium">以项目组织作品</h2><p className="text-sm text-muted-foreground">剧本、分镜、角色和生成素材，在项目中继续制作。</p></div></div>
        <Link className={buttonVariants({variant:"outline"})} href="/projects">打开项目中心</Link>
      </section>
    </div>
  );
}
