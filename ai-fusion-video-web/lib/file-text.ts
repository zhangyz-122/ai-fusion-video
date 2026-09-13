/** 支持导入的文本文件扩展名 */
export const SUPPORTED_IMPORT_EXTENSIONS = [
  "txt",
  "md",
  "markdown",
  "csv",
  "json",
  "xml",
  "html",
  "htm",
  "rtf",
  "docx",
  "pdf",
] as const;

export const SUPPORTED_IMPORT_ACCEPT =
  ".txt,.md,.markdown,.csv,.json,.xml,.html,.htm,.rtf,.docx,.pdf,text/plain,text/markdown,text/csv,application/json,application/xml,text/html,application/rtf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/pdf";

export const SUPPORTED_IMPORT_HINT =
  "支持 TXT / Markdown / CSV / JSON / XML / HTML / RTF / DOCX / PDF";

/** 导入文件大小上限：100MB */
export const MAX_IMPORT_FILE_SIZE = 100 * 1024 * 1024;

/** 直接按纯文本读取的扩展名 */
const PLAIN_TEXT_EXTENSIONS = ["txt", "md", "markdown", "csv", "json", "xml"];

/** 从任意支持的文件中抽取纯文本内容 */
export async function extractFileText(file: File): Promise<string> {
  const extension = file.name.split(".").pop()?.toLowerCase() || "";
  if (PLAIN_TEXT_EXTENSIONS.includes(extension)) {
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
}
