import fs from "node:fs";

const s = fs.readFileSync(process.argv[2] ?? "./v16.tmp.json", "utf8");
const counts = {};
const legal = new Set(['"', "\\", "/", "b", "f", "n", "r", "t", "u"]);
for (let i = 0; i < s.length; i++) {
  if (s[i] === "\\") {
    const next = s[i + 1] ?? "EOF";
    counts[next] = (counts[next] ?? 0) + 1;
    if (!legal.has(next)) {
      console.log(`非法转义 @${i}:`, JSON.stringify(s.slice(Math.max(0, i - 30), i + 15)));
    }
  }
}
console.log("反斜杠后继字符统计:", JSON.stringify(counts));
