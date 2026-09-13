#!/usr/bin/env node
/**
 * 修复工作流7/版本10(文戏低配 H3)缺失的输入绑定。
 *
 * 背景:版本10只绑定了 prompt 和 width,导致 duration/seed/参考图全部被丢弃
 * (模板 PrimitiveFloat=15 生效,所有视频固定 15 秒且为纯文生视频)。
 *
 * 流程:登录管理员 → 拉取版本10 → 合并补齐绑定 → 创建新版本(应用自动重算
 * 规范化哈希) → 在线验证 → 试运行(真实占用 GPU,视频可能数分钟) → 发布。
 *
 * 用法:
 *   FUSION_ADMIN_USER=zhangyz FUSION_ADMIN_PASSWORD=*** \
 *     node tools/repair-workflow7-v10-bindings.mjs
 *
 * 可选环境变量:
 *   FUSION_BASE_URL   默认 http://localhost:8081
 *   FUSION_FIRST_FRAME 默认用验收已生成的首帧:
 *     /media/images/446966c5762a43d9907230182ef45809.png
 *   FUSION_SKIP_TEST=1 跳过试运行(注意:未试运行无法发布)
 */

const BASE = process.env.FUSION_BASE_URL ?? "http://localhost:8081";
const USER = process.env.FUSION_ADMIN_USER;
const PASSWORD = process.env.FUSION_ADMIN_PASSWORD;
const FIRST_FRAME = process.env.FUSION_FIRST_FRAME
  ?? "/media/images/446966c5762a43d9907230182ef45809.png";
const WORKFLOW_ID = 7;
const SOURCE_VERSION_ID = 10;

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
    throw new Error(`${path} 失败: HTTP ${res.status} ${JSON.stringify(body)}`);
  }
  return body.data;
}

const login = await api("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({ username: USER, password: PASSWORD }),
});
const token = login.accessToken;
console.log(`已登录 ${login.username} (userId=${login.userId})`);

const source = await api(
  `/api/ai/comfyui/workflow/version/get?id=${SOURCE_VERSION_ID}`, {}, token);
console.log(`版本10: version_no=${source.versionNo}, 绑定=${source.inputBindingsJson.slice(0, 80)}...`);

const existing = JSON.parse(source.inputBindingsJson ?? "{}");
const merged = {
  ...existing,
  duration: [{ inputName: "value", nodeId: "529", valueType: "number" }],
  seed: [{ inputName: "noise_seed", nodeId: "322", valueType: "integer" }],
  referenceImages: [
    { index: 0, inputName: "image", nodeId: "526", valueType: "uploaded_image" },
    { index: 1, inputName: "image", nodeId: "527", valueType: "uploaded_image" },
    { index: 2, inputName: "image", nodeId: "525", valueType: "uploaded_image" },
    { index: 3, inputName: "image", nodeId: "515", valueType: "uploaded_image" },
    { index: 4, inputName: "image", nodeId: "475", valueType: "uploaded_image" },
    { index: 5, inputName: "image", nodeId: "469", valueType: "uploaded_image" },
  ],
};

const newVersionId = await api("/api/ai/comfyui/workflow/version/create", {
  method: "POST",
  body: JSON.stringify({
    workflowId: WORKFLOW_ID,
    uiWorkflowJson: source.uiWorkflowJson,
    apiWorkflowJson: source.apiWorkflowJson,
    inputBindingsJson: JSON.stringify(merged),
    outputBindingsJson: source.outputBindingsJson,
  }),
}, token);
console.log(`新版本已创建: id=${newVersionId}`);

const validation = await api(
  `/api/ai/comfyui/workflow/version/validate?versionId=${newVersionId}`,
  { method: "POST" }, token);
console.log(`在线验证: ${JSON.stringify(validation)}`);

if (process.env.FUSION_SKIP_TEST === "1") {
  console.log("FUSION_SKIP_TEST=1,跳过试运行。注意:未试运行通过无法发布。");
  process.exit(0);
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
      referenceImages: [FIRST_FRAME],
    },
  }),
}, token);
console.log(`试运行: ${JSON.stringify(test)}`);

await api(
  `/api/ai/comfyui/workflow/publish?workflowId=${WORKFLOW_ID}&versionId=${newVersionId}`,
  { method: "POST" }, token);
console.log(`已发布工作流${WORKFLOW_ID} → 版本${newVersionId}`);
console.log("完成。请重跑一次 duration=5 的生产验收并用 ffprobe 核对时长≈5.2s。");
