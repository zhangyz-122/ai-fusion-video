import fs from "node:fs";

const raw = fs.readFileSync(process.argv[2] ?? "./v16.tmp.json", "utf8");
// 找出 JSON 中不合法的反斜杠转义(合法: \" \\ \/ \b \f \n \r \t \uXXXX)
const re = /\\(?!["\\/bfnrtu])/g;
let m;
const spots = [];
while ((m = re.exec(raw)) !== null) {
  spots.push(m.index);
  console.log(
    `非法转义 @${m.index}: ...${JSON.stringify(raw.slice(Math.max(0, m.index - 45), m.index + 25))}...`
  );
  if (spots.length >= 10) break;
}
console.log(spots.length === 0 ? "未发现非法转义" : `共扫描到前 ${spots.length} 处`);
try {
  JSON.parse(raw);
  console.log("整体解析: 合法");
} catch (e) {
  console.log("整体解析: 非法 →", e.message.slice(0, 150));
}
