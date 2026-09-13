"use client";

import Link from "next/link";
import { Button, buttonVariants } from "@/components/ui/button";

interface ImageStudioHeaderProps {
  modelName?: string;
  advanced: boolean;
  onModeChange: () => void;
}

export function ImageStudioHeader({ modelName, advanced, onModeChange }: ImageStudioHeaderProps) {
  return (
    <header className="flex flex-wrap items-center justify-between gap-3 pb-4">
      <div className="space-y-1">
        <Link href="/generate/images" className="text-sm text-muted-foreground hover:text-foreground">← 图像工坊 / 能力目录</Link>
        <h1 className="text-2xl font-semibold tracking-tight">图像创作</h1>
        <p className="text-xs text-muted-foreground">{modelName || "请选择生图模型"} · 原有记录与资产持续保留</p>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="outline" onClick={onModeChange}>{advanced ? "切换快捷创作" : "展开创作面板"}</Button>
        <Link href="/assets" className={buttonVariants({ variant: "outline" })}>资产中心</Link>
      </div>
    </header>
  );
}
