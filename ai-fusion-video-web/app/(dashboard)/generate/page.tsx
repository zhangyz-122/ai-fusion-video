"use client";

import Link from "next/link";
import { ArrowRight, ImagePlus, Film, FolderKanban } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";

export default function CreativeHome() {
  return (
    <div className="mx-auto w-full max-w-4xl space-y-8 px-6 py-6">
      <header className="space-y-2">
        <p className="text-sm text-muted-foreground">工坊</p>
        <h1 className="text-2xl font-semibold tracking-tight">图像和视频工具</h1>
        <p className="text-sm text-muted-foreground">默认带着当前项目用。成片主路径仍在项目里的「拍」。</p>
      </header>
      <section className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-4 rounded-xl border border-border/30 bg-card p-5">
          <ImagePlus className="h-6 w-6 text-primary" />
          <div className="space-y-1">
            <h2 className="text-lg font-semibold">图像</h2>
            <p className="text-sm text-muted-foreground">角色、场景、分镜首帧。</p>
          </div>
          <Link className={buttonVariants()} href="/generate/image">开始生图 <ArrowRight /></Link>
        </div>
        <div className="space-y-4 rounded-xl border border-border/30 bg-card p-5">
          <Film className="h-6 w-6 text-primary" />
          <div className="space-y-1">
            <h2 className="text-lg font-semibold">视频</h2>
            <p className="text-sm text-muted-foreground">底座由平台选择，不必挑模型。</p>
          </div>
          <Link className={buttonVariants({ variant: "outline" })} href="/generate/video">打开万能导演台 <ArrowRight /></Link>
        </div>
      </section>
      <section className="flex flex-wrap items-center justify-between gap-4 border-t border-border/20 pt-6">
        <div className="flex items-center gap-3">
          <FolderKanban className="h-5 w-5" />
          <div>
            <h2 className="font-medium">回项目拍这一镜</h2>
            <p className="text-sm text-muted-foreground">剧本、圣经、分镜和生产在项目里完成。</p>
          </div>
        </div>
        <Link className={buttonVariants({ variant: "outline" })} href="/projects">打开项目</Link>
      </section>
    </div>
  );
}
