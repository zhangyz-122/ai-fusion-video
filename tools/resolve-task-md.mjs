import fs from "node:fs";

const file = process.argv[2] ?? "TASK.md";
let s = fs.readFileSync(file, "utf8");
const re = /^<{7} [^\r\n]*\r?\n([\s\S]*?)^={7}\r?\n([\s\S]*?)^>{7} [^\r\n]*\r?\n/gm;
let count = 0;
s = s.replace(re, (_m, head, theirs) => {
  count += 1;
  return head.trimEnd() + "\n\n---\n\n" + theirs.trimEnd() + "\n";
});
fs.writeFileSync(file, s);
console.log(count === 0 ? "无冲突标记" : `已解决 ${count} 处冲突(两侧记录都保留)`);
if (/^<{7}|^={7}$|^>{7}/m.test(fs.readFileSync(file, "utf8"))) {
  console.warn("警告:文件中仍有疑似冲突标记,请人工检查");
}
