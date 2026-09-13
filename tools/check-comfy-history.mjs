// 从 ComfyUI 历史中提取指定 prompt 的产物文件名
const ids = process.argv.slice(2);
async function main() {
  for (const pid of ids) {
    const res = await fetch(`http://127.0.0.1:8188/history/${pid}`);
    const h = await res.json();
    const entry = h[pid];
    if (!entry) { console.log(pid, "无历史记录"); continue; }
    const files = [];
    for (const [nodeId, out] of Object.entries(entry.outputs ?? {})) {
      for (const [field, arr] of Object.entries(out)) {
        if (Array.isArray(arr)) {
          for (const item of arr) {
            if (item && typeof item === "object" && item.filename) {
              files.push(`${field}: ${item.subfolder ? item.subfolder + "/" : ""}${item.filename} (type=${item.type ?? "output"})`);
            }
          }
        }
      }
    }
    console.log(pid.slice(0, 8), "→", files.length ? files.join(" | ") : "无产物");
  }
}
main().catch(e => { console.error(e.message); process.exit(1); });
