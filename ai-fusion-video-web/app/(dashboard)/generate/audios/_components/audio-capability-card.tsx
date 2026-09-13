import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import type { ComfyUiWorkflow } from "@/lib/api/comfyui-workflow";

interface AudioCapabilityCardProps {
  loading: boolean;
  /** 管理员成功拉取到的工作流状态;普通用户或失败时为 null。 */
  workflow: ComfyUiWorkflow | null;
}

/** 声音工坊能力状态卡片:已接入显示工作流真实状态,否则显示接入中占位。 */
export function AudioCapabilityCard({ loading, workflow }: AudioCapabilityCardProps) {
  if (loading) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        正在读取能力状态…
      </p>
    );
  }

  if (!workflow?.activeVersionId || workflow.status !== 1) {
    return (
      <div className="rounded-xl border border-dashed border-border/40 bg-card/30 px-6 py-12 text-center">
        <p className="text-sm font-medium">配音能力接入中</p>
        <p className="mt-1 text-sm text-muted-foreground">
          对白驱动的口播视频能力正在接入,完成后将在此展示。
        </p>
      </div>
    );
  }

  return (
    <article className="rounded-xl border border-border/30 bg-card/50 p-4 backdrop-blur-sm">
      <div className="flex flex-wrap items-center gap-3">
        <Badge>已接入</Badge>
        <h3 className="text-lg font-medium">{workflow.name}</h3>
        <span className="text-xs text-muted-foreground">
          当前版本 v{workflow.activeVersionId}
        </span>
      </div>
      <p className="mt-2 text-sm text-muted-foreground">
        {workflow.description || "音频驱动的口播视频能力,已通过平台音频试运行。"}
      </p>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border/20 bg-background/70 px-3 py-2">
        <span className="text-xs text-muted-foreground">
          更新时间 {new Date(workflow.updateTime).toLocaleString("zh-CN", { hour12: false })}
        </span>
        <span className="text-xs text-muted-foreground">配音编辑器与试听将在 G01 批次提供</span>
      </div>
    </article>
  );
}

/** 管理员入口:跳转工作流管理页配置音频能力。 */
export function AudioCapabilityAdminLink() {
  return (
    <Link href="/settings/ai-models" className={buttonVariants({ variant: "outline" })}>
      管理工作流
    </Link>
  );
}
