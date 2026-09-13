#!/usr/bin/env node
/**
 * 修复 WAN 工作流版本中非法的 Windows 路径反斜杠转义。
 *
 * 背景:历史 PowerShell 注册脚本把 api_workflow_json 以未转义的反斜杠写入
 * (如 wanvideo\Wan2_1_VAE_bf16.safetensors),Jackson 解析报
 * "Unrecognized character escape 'W'"。本脚本通过应用 API:
 *   拉取版本 → 修复转义(单反斜杠→双反斜杠) → 创建新版本(重算规范化哈希)
 *   → 在线验证 → 试运行(真实 GPU) → 发布。
 *
 * 用法:
 *   FUSION_ADMIN_USER=zhangyz FUSION_ADMIN_PASSWORD=*** \
 *     node tools/repair-wan-json-escapes.mjs <workflowId> <firstFrameImagePath>
 *
 * 示例(修 I2V 并验收):
 *   node tools/repair-wan-json-escapes.mjs 9 ./.tmp-frame.png
 * FUSION_SKIP_TEST=1 只建版本+验证,不试运行不发布。
 */

const BASE = process.env.FUSION_BASE_URL ?? "http://localhost:8081";
const USER = process.env.FUSION_ADMIN_USER;
const PASSWORD = process.env.FUSION_ADMIN_PASSWORD;
const WORKFLOW_ID = Number(process.argv[2] ?? 9);
const FIRST_FRAME = process.argv[3];
const SKIP_TEST = process.env.FUSION_SKIP_TEST === "1";

if (!USER || !PASSWORD) {
  console.error("请设置 FUSION_ADMIN_USER 和 FUSION_ADMIN_PASSWORD");
  process.exit(1);
}

async function api(path, options = {}, token) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {}),
    },
  });
  const body = await res.json().catch(() => null);
  if (!res.ok || !body || body.code !== 0) {
    throw new Error(`${path} 失败: HTTP ${res.status} ${JSON.stringify(body)?.slice(0, 300)}`);
  }
  return body.data;
}

const login = await api("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({ username: USER, password: PASSWORD }),
});
const token = login.accessToken;
console.log(`已登录 ${login.username}`);

const workflow = await api(`/api/ai/comfyui/workflow/get?id=${WORKFLOW_ID}`, {}, token);
const activeVersionId = workflow.activeVersionId;
console.log(`工作流${WORKFLOW_ID}(${workflow.name}) 当前发布版本=${activeVersionId}`);
if (!activeVersionId) {
  console.error("工作流没有已发布版本,请先走正常导入流程");
  process.exit(1);
}

const source = await api(
  `/api/ai/comfyui/workflow/version/get?id=${activeVersionId}`, {}, token);

// 修复非法转义:反斜杠后不是合法转义字符的,补成双反斜杠
const legal = new Set(["\\", '"', "/", "b", "f", "n", "r", "t", "u"]);
let fixes = 0;
const fixedApiJson = (source.apiWorkflowJson ?? "").replace(
  /\\(.)/g,
  (match, next) => {
    if (legal.has(next)) return match;
    fixes += 1;
    return "\\\\" + next;
  }
);
console.log(`修复了 ${fixes} 处非法反斜杠转义`);
if (fixes === 0) {
  console.log("原版本 JSON 本身合法,无需修复。");
  process.exit(0);
}
JSON.parse(fixedApiJson); // 修复后必须可解析,否则中止

// 输出绑定修复:历史脚本把单元素数组展开成了对象,应用要求数组
let outputBindings = JSON.parse(source.outputBindingsJson ?? "[]");
if (!Array.isArray(outputBindings)) {
  console.log("输出绑定是对象,包装为数组(修复 PowerShell 单元素数组展开问题)");
  outputBindings = [outputBindings];
}

const existingBindings = JSON.parse(source.inputBindingsJson ?? "{}");
const newVersionId = await api("/api/ai/comfyui/workflow/version/create", {
  method: "POST",
  body: JSON.stringify({
    workflowId: WORKFLOW_ID,
    uiWorkflowJson: source.uiWorkflowJson,
    apiWorkflowJson: fixedApiJson,
    inputBindingsJson: JSON.stringify(existingBindings),
    outputBindingsJson: JSON.stringify(outputBindings),
  }),
}, token);
console.log(`新版本已创建: id=${newVersionId}`);

const validation = await api(
  `/api/ai/comfyui/workflow/version/validate?versionId=${newVersionId}`,
  { method: "POST" }, token);
console.log(`在线验证: ${JSON.stringify(validation).slice(0, 200)}`);

if (SKIP_TEST) {
  console.log("FUSION_SKIP_TEST=1,跳过试运行。注意:未试运行无法发布。");
  process.exit(0);
}

let referenceImages;
if (FIRST_FRAME) {
  const b64 = fs.readFileSync(FIRST_FRAME).toString("base64");
  referenceImages = [`data:image/png;base64,${b64}`];
  console.log(`参考图: ${FIRST_FRAME} (${Math.round(b64.length / 1024)} KB base64)`);
}

console.log("开始试运行(真实占用 GPU,视频工作流可能需要数分钟)...");
const test = await api("/api/ai/comfyui/workflow/version/test", {
  method: "POST",
  body: JSON.stringify({
    versionId: newVersionId,
    inputs: {
      prompt: "repair validation: cabin in misty valley, slow push-in",
      duration: 5,
      seed: 20260913,
      ...(referenceImages ? { referenceImages } : {}),
    },
  }),
}, token);
console.log(`试运行: ${JSON.stringify(test).slice(0, 300)}`);

await api(
  `/api/ai/comfyui/workflow/publish?workflowId=${WORKFLOW_ID}&versionId=${newVersionId}`,
  { method: "POST" }, token);
console.log(`已发布工作流${WORKFLOW_ID} → 版本${newVersionId}`);
