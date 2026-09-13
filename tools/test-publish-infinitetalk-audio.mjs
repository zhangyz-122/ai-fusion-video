#!/usr/bin/env node
/**
 * INFINITETALK 音频试运行 + 发布(含音频文件预处理)。
 *
 * 背景:工作流 11(Wan InfiniteTalk)版本 22 已创建并通过在线验证,但发布前
 * 必须补一次试运行。平台 Native API 客户端目前不支持 uploaded_audio 上传绑定
 * (ComfyUiGenerationExecutor.uploadBoundMedia 直接拒绝,见技术债),因此本脚本:
 *   1. 把音频直接上传到 ComfyUI /upload/image(与平台上传图片同端点,type=input);
 *   2. 通过管理 API 修改未发布版本:LoadAudio 节点指向已上传音频,
 *      并移除暂时无法执行的 referenceAudios 上传绑定;
 *   3. 重新在线验证 → 试运行(视频工作流后端最长等待 60 分钟)→ 下载产出 → 发布。
 * 待后端支持音频上传绑定后,再建新版本恢复 referenceAudios 绑定。
 *
 * 用法:
 *   FUSION_ADMIN_USER=zhangyz FUSION_ADMIN_PASSWORD=*** \
 *     node tools/test-publish-infinitetalk-audio.mjs <workflowId> <versionId> <audioFile> [firstFrameImage]
 *
 * 示例:
 *   node tools/test-publish-infinitetalk-audio.mjs 11 22 ./tmp-voice.wav ./tmp-portrait.png
 * FUSION_SKIP_PREPARE=1 跳过音频上传与绑定修改(已处理过时重跑试运行);
 * FUSION_SKIP_PUBLISH=1 只试运行不发布;FUSION_DOWNLOAD_DIR=.tmp-out 保存产出文件。
 */

import fs from "node:fs";
import path from "node:path";
import http from "node:http";
import https from "node:https";

const BASE = process.env.FUSION_BASE_URL ?? "http://localhost:8081";
const COMFYUI_BASE = process.env.FUSION_COMFYUI_BASE ?? "http://127.0.0.1:8188";
const USER = process.env.FUSION_ADMIN_USER;
const PASSWORD = process.env.FUSION_ADMIN_PASSWORD;
const WORKFLOW_ID = Number(process.argv[2] ?? 11);
const VERSION_ID = Number(process.argv[3] ?? 22);
const AUDIO_FILE = process.argv[4];
const FIRST_FRAME = process.argv[5];
const SKIP_PREPARE = process.env.FUSION_SKIP_PREPARE === "1";
const SKIP_PUBLISH = process.env.FUSION_SKIP_PUBLISH === "1";
const DOWNLOAD_DIR = process.env.FUSION_DOWNLOAD_DIR;
const SEED = Number(process.env.FUSION_TEST_SEED ?? 20260913);

if (!USER || !PASSWORD) {
  console.error("请设置 FUSION_ADMIN_USER 和 FUSION_ADMIN_PASSWORD");
  process.exit(1);
}
if (!AUDIO_FILE || !fs.existsSync(AUDIO_FILE)) {
  console.error("请提供存在的音频文件路径(第 3 个参数)");
  process.exit(1);
}
if (FIRST_FRAME && !fs.existsSync(FIRST_FRAME)) {
  console.error("首帧图不存在: " + FIRST_FRAME);
  process.exit(1);
}

const MIME_BY_EXT = {
  ".wav": "audio/wav",
  ".mp3": "audio/mpeg",
  ".m4a": "audio/mp4",
  ".ogg": "audio/ogg",
  ".flac": "audio/flac",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
};

function assertMime(file) {
  const mime = MIME_BY_EXT[path.extname(file).toLowerCase()];
  if (!mime) throw new Error(`不支持的文件扩展名: ${file}`);
  return mime;
}

function toDataUri(file) {
  const mime = assertMime(file);
  const b64 = fs.readFileSync(file).toString("base64");
  console.log(`已读取 ${file} (${Math.round(b64.length / 1024)} KB base64, ${mime})`);
  return `data:${mime};base64,${b64}`;
}

// 试运行是同步接口(视频最长 60 分钟),fetch 默认 5 分钟响应头超时不够,改用 node:http
function requestJson(url, options = {}, token) {
  const target = new URL(url, BASE);
  const client = target.protocol === "https:" ? https : http;
  const payload = options.body ?? null;
  return new Promise((resolve, reject) => {
    const req = client.request(
      {
        hostname: target.hostname,
        port: target.port,
        path: target.pathname + target.search,
        method: options.method ?? "GET",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": payload ? Buffer.byteLength(payload) : 0,
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        timeout: 60 * 60 * 1000,
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString("utf8");
          let body = null;
          try {
            body = JSON.parse(raw);
          } catch {
            body = null;
          }
          if (res.statusCode >= 400 || !body || body.code !== 0) {
            reject(new Error(
              `${options.method ?? "GET"} ${target.pathname} 失败: HTTP ${res.statusCode} ${raw.slice(0, 300)}`
            ));
            return;
          }
          resolve(body.data);
        });
      }
    );
    req.on("timeout", () => req.destroy(new Error("请求超时(60 分钟)")));
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

// 复刻平台 ComfyUiNativeClient.uploadMedia:multipart POST /upload/image(type=input)
function uploadToComfyui(file) {
  const mime = assertMime(file);
  const boundary = "----t4form" + Date.now().toString(16);
  const safeName = "t4-audio-" + Date.now() + path.extname(file).toLowerCase();
  const fileBytes = fs.readFileSync(file);
  const head = Buffer.from(
    `--${boundary}\r\n` +
    `Content-Disposition: form-data; name="image"; filename="${safeName}"\r\n` +
    `Content-Type: ${mime}\r\n\r\n`
  );
  const tail = Buffer.from(
    `\r\n--${boundary}\r\n` +
    `Content-Disposition: form-data; name="type"\r\n\r\ninput\r\n` +
    `--${boundary}--\r\n`
  );
  const body = Buffer.concat([head, fileBytes, tail]);
  const target = new URL("/upload/image", COMFYUI_BASE);
  const client = target.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const req = client.request(
      {
        hostname: target.hostname,
        port: target.port,
        path: target.pathname + target.search,
        method: "POST",
        headers: {
          "Content-Type": `multipart/form-data; boundary=${boundary}`,
          "Content-Length": body.length,
        },
        timeout: 30_000,
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString("utf8");
          if (res.statusCode >= 400) {
            reject(new Error(`ComfyUI 上传失败: HTTP ${res.statusCode} ${raw.slice(0, 200)}`));
            return;
          }
          const parsed = JSON.parse(raw);
          if (parsed.type !== "input" || !parsed.name) {
            reject(new Error(`ComfyUI 上传响应异常: ${raw.slice(0, 200)}`));
            return;
          }
          resolve(parsed.name);
        });
      }
    );
    req.on("timeout", () => req.destroy(new Error("ComfyUI 上传超时")));
    req.on("error", reject);
    req.write(body);
    req.end();
  });
}

const login = await requestJson("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({ username: USER, password: PASSWORD }),
});
const token = login.accessToken;
console.log(`已登录 ${login.username}`);

const workflow = await requestJson(`/api/ai/comfyui/workflow/get?id=${WORKFLOW_ID}`, {}, token);
console.log(`工作流${WORKFLOW_ID}(${workflow.name}) 当前发布版本=${workflow.activeVersionId}`);

let version = await requestJson(`/api/ai/comfyui/workflow/version/get?id=${VERSION_ID}`, {}, token);
if (version.workflowId !== WORKFLOW_ID) {
  console.error(`版本 ${VERSION_ID} 属于工作流 ${version.workflowId},与参数不一致`);
  process.exit(1);
}
if (version.published || version.id === workflow.activeVersionId) {
  console.log(`版本 ${VERSION_ID} 已是发布版本,无需重复处理。`);
  process.exit(0);
}

if (!SKIP_PREPARE) {
  const audioName = await uploadToComfyui(AUDIO_FILE);
  console.log(`音频已上传 ComfyUI input: ${audioName}`);

  const apiWorkflow = JSON.parse(version.apiWorkflowJson);
  const audioNodes = Object.entries(apiWorkflow)
    .filter(([, node]) => node.class_type === "LoadAudio");
  if (audioNodes.length !== 1) {
    console.error(`期望恰好 1 个 LoadAudio 节点,实际 ${audioNodes.length} 个,中止。`);
    process.exit(1);
  }
  const [audioNodeId, audioNode] = audioNodes[0];
  audioNode.inputs.audio = audioName;

  const inputBindings = JSON.parse(version.inputBindingsJson ?? "{}");
  const removed = delete inputBindings.referenceAudios;
  console.log(
    `已修改版本: LoadAudio 节点 ${audioNodeId} → ${audioName}` +
    (removed ? ",移除 referenceAudios 上传绑定" : "")
  );

  await requestJson("/api/ai/comfyui/workflow/version/update", {
    method: "PUT",
    body: JSON.stringify({
      id: VERSION_ID,
      workflowId: WORKFLOW_ID,
      uiWorkflowJson: version.uiWorkflowJson,
      apiWorkflowJson: JSON.stringify(apiWorkflow),
      inputBindingsJson: JSON.stringify(inputBindings),
      outputBindingsJson: version.outputBindingsJson,
    }),
  }, token);

  const validation = await requestJson(
    `/api/ai/comfyui/workflow/version/validate?versionId=${VERSION_ID}`,
    { method: "POST" }, token);
  console.log(`重新在线验证: ${JSON.stringify(validation).slice(0, 200)}`);
  if (!validation.valid) {
    console.error("在线验证未通过,中止试运行。");
    process.exit(1);
  }
  version = await requestJson(`/api/ai/comfyui/workflow/version/get?id=${VERSION_ID}`, {}, token);
}

const inputs = {
  prompt:
    process.env.FUSION_TEST_PROMPT ??
    "audio workshop smoke test: a person speaking calmly to the camera, natural lip movement",
  seed: SEED,
};
if (FIRST_FRAME) {
  inputs.firstFrame = toDataUri(FIRST_FRAME);
}

console.log("开始音频试运行(真实占用 GPU,视频可能需要数分钟)...");
const test = await requestJson("/api/ai/comfyui/workflow/version/test", {
  method: "POST",
  body: JSON.stringify({ versionId: VERSION_ID, inputs }),
}, token);
console.log(`试运行: passed=${test.passed} durationMillis=${test.durationMillis} message=${test.message}`);
for (const output of test.outputs ?? []) {
  console.log(`  输出[${output.role}/${output.mediaType}] ${output.url} (${Math.round(output.size / 1024)} KB)`);
}
if (!test.passed) {
  console.error("试运行未通过,中止发布。");
  process.exit(1);
}

if (DOWNLOAD_DIR) {
  fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });
  for (const [index, output] of (test.outputs ?? []).entries()) {
    const outputUrl = new URL(output.url, BASE);
    const client = outputUrl.protocol === "https:" ? https : http;
    const file = path.join(DOWNLOAD_DIR, `v${VERSION_ID}-output-${index}.mp4`);
    await new Promise((resolve, reject) => {
      client.get(
        {
          hostname: outputUrl.hostname,
          port: outputUrl.port,
          path: outputUrl.pathname + outputUrl.search,
          headers: { Authorization: `Bearer ${token}` },
        },
        (res) => {
          if (res.statusCode >= 400) {
            reject(new Error(`下载输出失败: HTTP ${res.statusCode}`));
            res.resume();
            return;
          }
          const stream = fs.createWriteStream(file);
          res.pipe(stream);
          stream.on("finish", () => stream.close(resolve));
          stream.on("error", reject);
        }
      ).on("error", reject);
    });
    console.log(`已下载输出: ${file}`);
  }
}

if (SKIP_PUBLISH) {
  console.log("FUSION_SKIP_PUBLISH=1,跳过发布。");
  process.exit(0);
}

await requestJson(
  `/api/ai/comfyui/workflow/publish?workflowId=${WORKFLOW_ID}&versionId=${VERSION_ID}`,
  { method: "POST" },
  token,
);
const confirmed = await requestJson(`/api/ai/comfyui/workflow/get?id=${WORKFLOW_ID}`, {}, token);
console.log(`已发布工作流${WORKFLOW_ID} → 版本${VERSION_ID},当前发布版本=${confirmed.activeVersionId}`);
