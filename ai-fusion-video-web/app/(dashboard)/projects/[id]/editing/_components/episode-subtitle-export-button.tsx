"use client";

import { useState } from "react";
import { Download, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { toastApiError } from "@/lib/api/toast-api-error";
import { downloadEpisodeSubtitle } from "@/lib/api/script";

interface EpisodeSubtitleExportButtonProps {
  /** 绑定的剧本分集 ID，未绑定时不可导出 */
  scriptEpisodeId: number | null;
  /** 分集展示名，用于提示文案 */
  episodeLabel: string;
}

/** 分集行内的“导出字幕”入口：下载绑定剧本分集的 SRT 字幕文件 */
export function EpisodeSubtitleExportButton({
  scriptEpisodeId,
  episodeLabel,
}: EpisodeSubtitleExportButtonProps) {
  const [exporting, setExporting] = useState(false);
  const disabled = scriptEpisodeId == null || exporting;
  const title =
    scriptEpisodeId == null
      ? "该分集未绑定剧本分集，无法导出字幕"
      : `导出「${episodeLabel}」的 SRT 字幕`;

  const handleExport = async () => {
    if (scriptEpisodeId == null) return;
    setExporting(true);
    try {
      await downloadEpisodeSubtitle(scriptEpisodeId);
      toast.success(`已导出「${episodeLabel}」字幕`);
    } catch (err) {
      toastApiError(err, "导出字幕失败");
    } finally {
      setExporting(false);
    }
  };

  return (
    <Button
      variant="ghost"
      size="icon-xs"
      className="shrink-0"
      disabled={disabled}
      title={title}
      aria-label={title}
      onClick={() => void handleExport()}
    >
      {exporting ? <Loader2 className="animate-spin" /> : <Download />}
    </Button>
  );
}
