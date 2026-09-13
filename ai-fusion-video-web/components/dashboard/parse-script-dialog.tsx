"use client";

import { useConfirm } from "@/components/ui/confirm-dialog";
import { useState, type ChangeEvent } from "react";
import { X, Sparkles, Loader2, Upload } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import { scriptApi } from "@/lib/api/script";

interface ParseScriptDialogProps {
  open: boolean;
  scriptId: number;
  /** "create" = 首次写入原文, "reparse" = 替换原文并重置内部内容 */
  mode?: "create" | "reparse";
  onClose: () => void;
  /** 原文更新成功后回调，传入固定剧本信息 */
  onCreated: (script: { id: number; title: string }) => void;
}

export function ParseScriptDialog({
  open,
  scriptId,
  mode = "create",
  onClose,
  onCreated,
}: ParseScriptDialogProps) {
  const { confirm } = useConfirm();
  const [rawContent, setRawContent] = useState("");
  const [fileName, setFileName] = useState("");
  const [loading, setLoading] = useState(false);
  const [readingFile, setReadingFile] = useState(false);
  const [error, setError] = useState("");

  const extractFileText = async (file: File) => {
    const extension = file.name.split(".").pop()?.toLowerCase() || "";
    if (["txt", "md", "markdown", "csv", "json", "xml"].includes(extension)) {
      return file.text();
    }

    if (extension === "html" || extension === "htm") {
      const html = await file.text();
      const doc = new DOMParser().parseFromString(html, "text/html");
      return doc.body?.innerText || doc.body?.textContent || html;
    }

    if (extension === "rtf") {
      const rtf = await file.text();
      return rtf
        .replace(/\\'[0-9a-f]{2}/gi, "")
        .replace(/\\par[d]?/gi, "\n")
        .replace(/\\[a-z]+-?\\d* ?/gi, "")
        .replace(/[{}]/g, "")
        .replace(/\\\\/g, "\\")
        .trim();
    }

    if (extension === "docx") {
      const mammothModule = await import("mammoth");
      const mammoth = mammothModule.default ?? mammothModule;
      const result = await mammoth.extractRawText({
        arrayBuffer: await file.arrayBuffer(),
      });
      return result.value;
    }

    if (extension === "pdf") {
      const pdfjs = await import("pdfjs-dist/legacy/build/pdf.mjs");
      const pdf = await pdfjs.getDocument({
        data: new Uint8Array(await file.arrayBuffer()),
        disableWorker: true,
      } as never).promise;
      const pages: string[] = [];
      for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber++) {
        const page = await pdf.getPage(pageNumber);
        const content = await page.getTextContent();
        pages.push(
          content.items
            .map((item) => ("str" in item ? item.str : ""))
            .join(" "),
        );
      }
      return pages.join("\n\n");
    }

    throw new Error("不支持的文件格式");
  };

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    const extension = file.name.split(".").pop()?.toLowerCase() || "";
    const supportedExtensions = [
      "txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "rtf", "docx", "pdf",
    ];
    if (!supportedExtensions.includes(extension)) {
      setError("支持 TXT、Markdown、CSV、JSON、XML、HTML、RTF、DOCX 和 PDF 文件");
      return;
    }
    if (file.size > 100 * 1024 * 1024) {
      setError("文件大小不能超过 100MB");
      return;
    }

    try {
      setReadingFile(true);
      setError("");
      const text = await extractFileText(file);
      if (!text.trim()) {
        setError("文件内容为空，请选择有内容的剧本文件");
        return;
      }
      setRawContent(text);
      setFileName(file.name);
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "文件读取失败，请重试");
    } finally {
      setReadingFile(false);
    }
  };

  const handleSubmit = async () => {
    if (!rawContent.trim()) {
      setError("请粘贴剧本原文");
      return;
    }
    if (mode === "reparse") {
      const ok = await confirm({ title: "重新解析剧本", description: "重新解析会清空当前剧本的分集和场次，并使用新原文重新生成。顶层剧本记录会保留，确定继续？", variant: "ai", confirmText: "确定重新解析" });
      if (!ok) return;
    }
    setLoading(true);
    setError("");
    try {
      const script = await scriptApi.replaceSource(scriptId, rawContent.trim());
      setRawContent("");
      setFileName("");
      onCreated({ id: script.id, title: script.title });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (loading || readingFile) return;
    onClose();
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="modal-overlay fixed inset-0 z-50"
            onClick={handleClose}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            transition={{ duration: 0.2 }}
            className="fixed left-1/2 top-1/2 z-50 -translate-x-1/2 -translate-y-1/2 w-full max-w-lg"
          >
            <div className="modal-surface rounded-2xl border border-border/40 p-6">
              <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-5 w-5 text-purple-400" />
                  <h2 className="text-lg font-semibold">
                    {mode === "reparse" ? "重新解析剧本" : "AI 解析剧本"}
                  </h2>
                </div>
                <button
                  onClick={handleClose}
                  className="p-1.5 rounded-lg hover:bg-muted transition-colors"
                >
                  <X className="h-4 w-4 text-muted-foreground" />
                </button>
              </div>

              <div className="space-y-4">
                {/* 剧本原文 */}
                <div>
                  <label className="block text-sm font-medium mb-1.5">
                    剧本原文 <span className="text-destructive">*</span>
                  </label>
                  <div className="mb-2 flex items-center gap-2">
                    <label
                      className={cn(
                        "inline-flex cursor-pointer items-center gap-2 rounded-lg border border-border/40",
                        "bg-muted/40 px-3 py-2 text-xs font-medium transition-colors",
                        "hover:border-primary/50 hover:bg-primary/5",
                        (loading || readingFile) && "pointer-events-none opacity-50",
                      )}
                    >
                      <Upload className="h-3.5 w-3.5" />
                      {readingFile ? "正在读取" : "从文件导入"}
                      <input
                        type="file"
                        accept=".txt,.md,.markdown,.csv,.json,.xml,.html,.htm,.rtf,.docx,.pdf,text/plain,text/markdown,text/csv,application/json,application/xml,text/html,application/rtf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/pdf"
                        className="sr-only"
                        onChange={handleFileChange}
                        disabled={loading || readingFile}
                      />
                    </label>
                    <span className="text-[11px] text-muted-foreground">
                      支持 TXT / Markdown / CSV / JSON / XML / HTML / RTF / DOCX / PDF
                    </span>
                  </div>
                  {fileName && (
                    <p className="mb-2 truncate text-xs text-primary" title={fileName}>
                      已载入：{fileName}
                    </p>
                  )}
                  <textarea
                    value={rawContent}
                    onChange={(e) => setRawContent(e.target.value)}
                    placeholder="可直接粘贴，也可以点击上方“从文件导入”选择剧本文件..."
                    rows={10}
                    autoFocus
                    className={cn(
                      "w-full px-3.5 py-2.5 rounded-xl text-sm resize-none",
                      "bg-muted/50 border border-border/40",
                      "focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary/50",
                      "placeholder:text-muted-foreground/50 transition-all"
                    )}
                    disabled={loading}
                  />
                </div>

                {error && <p className="text-sm text-destructive">{error}</p>}
              </div>

              <div className="flex justify-end gap-3 mt-6">
                <button
                  onClick={handleClose}
                  disabled={loading}
                  className="px-4 py-2 rounded-xl text-sm font-medium hover:bg-muted transition-colors disabled:opacity-50"
                >
                  取消
                </button>
                <button
                  onClick={handleSubmit}
                  disabled={loading}
                  className={cn(
                    "flex items-center gap-2 px-5 py-2 rounded-xl text-sm font-medium",
                    "bg-linear-to-r from-purple-600 to-pink-600",
                    "text-white shadow-lg shadow-purple-500/20",
                    "hover:shadow-purple-500/30 active:scale-[0.98] transition-all",
                    "disabled:opacity-50 disabled:cursor-not-allowed"
                  )}
                >
                  {loading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      解析中...
                    </>
                  ) : (
                    <>
                      <Sparkles className="h-4 w-4" />
                      开始解析
                    </>
                  )}
                </button>
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
