"use client";

import { useEffect, useState, type ChangeEvent } from "react";
import { Loader2, Upload, FileText } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { scriptApi, type Script } from "@/lib/api/script";
import { useConfirm } from "@/components/ui/confirm-dialog";
import {
  extractFileText,
  MAX_IMPORT_FILE_SIZE,
  SUPPORTED_IMPORT_ACCEPT,
  SUPPORTED_IMPORT_EXTENSIONS,
  SUPPORTED_IMPORT_HINT,
} from "@/lib/file-text";

interface SourceImportDialogProps {
  open: boolean;
  scriptId: number;
  /** 当前已保存的原文，作为编辑初始值；为空表示首次导入 */
  initialContent: string;
  /** 已生成的剧集数量，用于替换前的清空警示 */
  episodeCount: number;
  onClose: () => void;
  onSaved: (script: Script) => void | Promise<void>;
}

/**
 * 原文导入/编辑弹窗：只负责保存原文本身，不触发任何 AI 解析。
 * 替换已有原文会重置已生成的剧集与场次，需要明确确认。
 */
export function SourceImportDialog({
  open,
  scriptId,
  initialContent,
  episodeCount,
  onClose,
  onSaved,
}: SourceImportDialogProps) {
  const { confirm } = useConfirm();

  const [rawContent, setRawContent] = useState("");
  const [fileName, setFileName] = useState("");
  const [saving, setSaving] = useState(false);
  const [readingFile, setReadingFile] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (open) {
      setRawContent(initialContent);
      setFileName("");
      setError("");
    }
  }, [open, initialContent]);

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    const extension = file.name.split(".").pop()?.toLowerCase() || "";
    if (!SUPPORTED_IMPORT_EXTENSIONS.includes(extension as (typeof SUPPORTED_IMPORT_EXTENSIONS)[number])) {
      setError("支持 TXT、Markdown、CSV、JSON、XML、HTML、RTF、DOCX 和 PDF 文件");
      return;
    }
    if (file.size > MAX_IMPORT_FILE_SIZE) {
      setError("文件大小不能超过 100MB");
      return;
    }

    try {
      setReadingFile(true);
      setError("");
      const text = await extractFileText(file);
      if (!text.trim()) {
        setError("文件内容为空，请选择有内容的文件");
        return;
      }
      setRawContent(text);
      setFileName(file.name);
    } catch (err) {
      setError(err instanceof Error ? err.message : "文件读取失败，请重试");
    } finally {
      setReadingFile(false);
    }
  };

  const handleSave = async () => {
    if (!rawContent.trim()) {
      setError("请粘贴或导入原文内容");
      return;
    }
    if (episodeCount > 0 && initialContent.trim()) {
      const ok = await confirm({
        title: "替换原文",
        description: `项目已生成 ${episodeCount} 集剧本。保存新原文会清空这些剧集与场次（原文本身保留），确定继续？`,
        variant: "destructive",
        confirmText: "确定替换",
      });
      if (!ok) return;
    }
    setSaving(true);
    setError("");
    try {
      const script = await scriptApi.replaceSource(scriptId, rawContent.trim());
      toast.success("原文已保存");
      await onSaved(script);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败，请重试");
    } finally {
      setSaving(false);
    }
  };

  const handleClose = () => {
    if (saving || readingFile) return;
    onClose();
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="modal-overlay absolute inset-0" onClick={handleClose} />
      <div className="modal-surface relative z-10 w-full max-w-lg rounded-2xl border border-border/40 p-6">
        <div className="flex items-center gap-2 mb-5">
          <FileText className="h-5 w-5 text-primary" />
          <h2 className="text-lg font-semibold">
            {initialContent ? "编辑原文" : "导入原文"}
          </h2>
        </div>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1.5">
              原文内容 <span className="text-destructive">*</span>
            </label>
            <div className="mb-2 flex items-center gap-2">
              <label
                className={cn(
                  "inline-flex cursor-pointer items-center gap-2 rounded-lg border border-border/40",
                  "bg-muted/40 px-3 py-2 text-xs font-medium transition-colors",
                  "hover:border-primary/50 hover:bg-primary/5",
                  (saving || readingFile) && "pointer-events-none opacity-50",
                )}
              >
                <Upload className="h-3.5 w-3.5" />
                {readingFile ? "正在读取" : "从文件导入"}
                <input
                  type="file"
                  accept={SUPPORTED_IMPORT_ACCEPT}
                  className="sr-only"
                  onChange={handleFileChange}
                  disabled={saving || readingFile}
                />
              </label>
              <span className="text-[11px] text-muted-foreground">{SUPPORTED_IMPORT_HINT}</span>
            </div>
            {fileName && (
              <p className="mb-2 truncate text-xs text-primary" title={fileName}>
                已载入：{fileName}（{rawContent.length.toLocaleString()} 字）
              </p>
            )}
            <textarea
              value={rawContent}
              onChange={(e) => setRawContent(e.target.value)}
              placeholder="可直接粘贴，也可以点击上方“从文件导入”选择小说或故事文件..."
              rows={10}
              className={cn(
                "w-full px-3.5 py-2.5 rounded-xl text-sm resize-none",
                "bg-muted/50 border border-border/40",
                "focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary/50",
                "placeholder:text-muted-foreground/50 transition-colors",
              )}
              disabled={saving}
            />
            <p className="mt-1.5 text-[11px] text-muted-foreground">
              共 {rawContent.length.toLocaleString()} 字。这里只保存原文，不会触发任何解析。
            </p>
          </div>

          {episodeCount > 0 && (
            <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
              项目已生成 {episodeCount} 集剧本。保存新原文会清空这些剧集与场次（原文本身保留），请确认后再保存。
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <div className="flex justify-end gap-3 mt-6">
          <button
            onClick={handleClose}
            disabled={saving}
            className="px-4 py-2 rounded-xl text-sm font-medium hover:bg-muted transition-colors disabled:opacity-50"
          >
            取消
          </button>
          <button
            onClick={() => void handleSave()}
            disabled={saving || readingFile}
            className="inline-flex items-center gap-2 px-5 py-2 rounded-xl text-sm font-medium bg-primary text-primary-foreground shadow-sm hover:bg-primary/90 transition-colors disabled:opacity-50"
          >
            {saving && <Loader2 className="h-4 w-4 animate-spin" />}
            保存原文
          </button>
        </div>
      </div>
    </div>
  );
}
