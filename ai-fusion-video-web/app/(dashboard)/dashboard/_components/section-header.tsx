"use client";

import type { ReactNode } from "react";
import { ArrowRight } from "lucide-react";

/** 区块标题：标题 + 可选的右侧动作链接 */
export function SectionHeader({
  title,
  icon,
  action,
}: {
  title: string;
  icon: ReactNode;
  action?: { label: string; onClick: () => void };
}) {
  return (
    <div className="flex items-center justify-between mb-3">
      <h2 className="text-sm font-semibold flex items-center gap-2 text-foreground/80">
        {icon}
        {title}
      </h2>
      {action && (
        <button
          onClick={action.onClick}
          className="text-xs text-muted-foreground/50 hover:text-foreground flex items-center gap-0.5 transition-colors"
        >
          {action.label}
          <ArrowRight className="h-3 w-3" />
        </button>
      )}
    </div>
  );
}
