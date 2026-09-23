#!/usr/bin/env node
/**
 * P0 Golden 有界 runner（PR-030）。
 *
 * 它不生成内容，只做三件事：按 fixture 的 12 个镜头驱动既有 Production API、
 * 逐条判定可自动判定的验收 Gate、把结果写成 Run Capsule。
 * 需要人工审美复核或需要注入故障的 Gate 一律记 NOT_RUN，绝不代替人给出 PASS。
 *
 * 用法：
 *   BASE_URL=http://localhost:18080 USERNAME=... PASSWORD=... \
 *     node tools/p0-golden-runner.mjs [--fixture path] [--out path] [--dry-run]
 *
 * 退出码：0 全部可判定 Gate 通过；1 存在 FAIL；2 前置条件不满足；3 等待人工复核。
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const arg = (name) => {
  const index = process.argv.indexOf(`--${name}`);
  return index === -1 ? null : process.argv[index + 1];
};

const BASE_URL = (process.env.BASE_URL || 'http://localhost:18080').replace(/\/$/, '');
const FIXTURE = resolve(ROOT, arg('fixture') || 'evidence/2026-09-13/golden/P0_GOLDEN_FIXTURE_001.json');
const OUT = resolve(ROOT, arg('out') || `tools/out/${process.env.P0_OUT_NAME || 'p0-golden-capsule'}.json`);
const DRY_RUN = process.argv.includes('--dry-run');
const POLL_TIMEOUT_MS = Number(process.env.P0_POLL_TIMEOUT_MS || 1_800_000);
const POLL_INTERVAL_MS = Number(process.env.P0_POLL_INTERVAL_MS || 15_000);

const fixture = JSON.parse(readFileSync(FIXTURE, 'utf8'));
const scope = fixture.scope ?? {};
const policy = fixture.executionPolicy ?? {};

let token = null;

async function call(method, path, body) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let payload;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new Error(`${method} ${path} 返回非 JSON（HTTP ${response.status}）: ${text.slice(0, 200)}`);
  }
  if (!response.ok || payload.code !== 0) {
    throw new Error(`${method} ${path} 失败（HTTP ${response.status}, code ${payload.code}）: ${payload.msg ?? text.slice(0, 200)}`);
  }
  return payload.data;
}

const get = (path) => call('GET', path);
const post = (path, body) => call('POST', path, body ?? {});

function check(code, pass, detail) {
  return { id: code, result: pass ? 'PASS' : 'FAIL', detail };
}
const notRun = (code, reason) => ({ id: code, result: 'NOT_RUN', detail: reason });

async function preflight() {
  const missing = [];
  if (!process.env.USERNAME || !process.env.PASSWORD) {
    missing.push('缺少 USERNAME / PASSWORD 环境变量（需要已初始化的管理员账号）');
  }
  try {
    const login = await call('POST', '/api/auth/login', {
      username: process.env.USERNAME, password: process.env.PASSWORD,
    });
    token = login.accessToken;
  } catch (error) {
    missing.push(`后端不可达或登录失败：${error.message}`);
    return missing;
  }
  try {
    await get('/api/production/runs?pageNo=1&pageSize=1');
  } catch (error) {
    missing.push(`Production API 不可用（数据库/迁移未就绪？）：${error.message}`);
  }
  return missing;
}

async function prepareShot(shotId) {
  const idempotencyKey = `${fixture.goldenId}:${shotId}:r1`;
  // fixture 里给的是 profile 代码，API 要的是标识；未显式提供时交给后端按模型活动版本解析
  const profileId = Number(process.env.P0_PROFILE_ID);
  return post('/api/production/runs', {
    storyboardItemId: shotId,
    idempotencyKey,
    workflowProfileId: Number.isFinite(profileId) && profileId > 0 ? profileId : undefined,
  });
}

async function awaitReconcile(runId) {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  let detail = await get(`/api/production/runs/${runId}`);
  while (Date.now() < deadline) {
    const takes = detail.takes ?? [];
    if (takes.length > 0) {
      return detail;
    }
    if (detail.run?.status === 'FAILED') {
      return detail;
    }
    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
    await post(`/api/production/runs/${runId}/reconcile`);
    detail = await get(`/api/production/runs/${runId}`);
  }
  return detail;
}

async function runShot(shotId) {
  const readiness = await get(`/api/production/shots/${shotId}/readiness`);
  if (readiness.ready !== true) {
    return { shotId, blocked: `readiness 未通过: ${(readiness.blockers ?? []).map((b) => b.code).join(',')}` };
  }
  const started = await prepareShot(shotId);
  const runId = started.run?.id ?? started.id;
  const detail = await awaitReconcile(runId);
  const usage = await get(`/api/production/runs/${runId}/usage`);
  const shotUsage = await get(`/api/production/shots/${shotId}/usage`);
  // 幂等重放：再 reconcile 一次，候选数不得增长
  await post(`/api/production/runs/${runId}/reconcile`);
  const replayed = await get(`/api/production/runs/${runId}/usage`);
  return { shotId, runId, detail, usage, shotUsage, replayed };
}

function evaluate(shots) {
  const gates = [];
  const fanout = [];
  const ingestion = [];
  const technical = [];
  const idempotency = [];
  const selected = [];

  for (const shot of shots) {
    if (shot.blocked) {
      fanout.push(`${shot.shotId}:${shot.blocked}`);
      continue;
    }
    const usage = shot.usage;
    if (usage.requestedCandidates !== 3 || usage.videoTaskSuccessCount !== 3) {
      fanout.push(`${shot.shotId}: requested=${usage.requestedCandidates} success=${usage.videoTaskSuccessCount}`);
    }
    if (usage.candidateCount !== 3) {
      ingestion.push(`${shot.shotId}: candidateCount=${usage.candidateCount}`);
    }
    const passed = usage.technicalStatusCounts?.PASS ?? 0;
    if (passed !== usage.candidateCount || (usage.structuredMetricsCount ?? 0) === 0) {
      technical.push(`${shot.shotId}: technical PASS=${passed}/${usage.candidateCount}, metrics=${usage.structuredMetricsCount}`);
    }
    if (shot.replayed.candidateCount !== usage.candidateCount || shot.shotUsage.runCount !== 1) {
      idempotency.push(`${shot.shotId}: replay 后 candidate=${shot.replayed.candidateCount}, runCount=${shot.shotUsage.runCount}`);
    }
    if (usage.selectedTakeId == null) {
      selected.push(`${shot.shotId}`);
    }
  }

  gates.push(check('G01', shots.every((s) => !s.blocked && s.shotUsage?.runCount === 1),
    `每镜恰好进入一次 ProductionRun；共 ${shots.length} 镜`));
  gates.push(check('G02', fanout.length === 0, fanout.length ? fanout.join(' | ') : '每镜 VideoTask count=3 且 success_count=3'));
  gates.push(check('G03', ingestion.length === 0, ingestion.length ? ingestion.join(' | ') : '每镜落库 3 个 ProductionTake'));
  gates.push(check('G04', technical.length === 0, technical.length ? technical.join(' | ') : '全部候选技术质检 PASS 且带结构化指标'));
  gates.push(notRun('G05', selected.length === 0
    ? '候选已选定，但选定依据仍需人工审美复核记录'
    : `等待人工 QC 后选定的镜头: ${selected.join(',')}`));
  gates.push(notRun('G06', '本 runner 未实现合成调用：写真实媒体产物需要显式决定，避免在无人观察时覆盖分集成片'));
  gates.push(check('G07', idempotency.length === 0, idempotency.length ? idempotency.join(' | ') : 'reconcile 重放未产生重复 run/take'));
  gates.push(notRun('G08', '需要注入 ComfyUI 故障与 Redis 重启，runner 不自动制造故障'));
  gates.push(notRun('G09', 'Legacy/Mixed 合成回归需要专门的旧数据夹具，不在本 runner 范围'));

  return gates;
}

async function main() {
  const missing = await preflight();
  if (missing.length > 0) {
    console.error('前置条件不满足：');
    for (const item of missing) console.error(`  - ${item}`);
    process.exitCode = 2;
    return;
  }
  const shotIds = scope.shotIds ?? [];
  if (shotIds.length === 0) {
    console.error(`fixture 未提供 scope.shotIds：${FIXTURE}`);
    process.exitCode = 2;
    return;
  }
  if (DRY_RUN) {
    console.log(`[dry-run] 将驱动 ${shotIds.length} 个镜头 ${shotIds.join(',')}，profile=${policy.defaultProfile}，base=${BASE_URL}`);
    return;
  }

  const results = [];
  for (const shotId of shotIds) {
    process.stdout.write(`shot ${shotId} … `);
    const shot = await runShot(shotId);
    results.push(shot);
    console.log(shot.blocked ? `跳过（${shot.blocked}）` : `候选 ${shot.usage.candidateCount}，技术 PASS ${shot.usage.technicalStatusCounts?.PASS ?? 0}`);
  }

  const gates = evaluate(results);
  const awaitingManualQc = gates.some((g) => g.id === 'G05' && g.detail.startsWith('等待人工'));
  const capsule = {
    goldenId: fixture.goldenId,
    ranAt: new Date().toISOString(),
    baseUrl: BASE_URL,
    scope,
    shots: results.map((shot) => ({
      shotId: shot.shotId,
      runId: shot.runId ?? null,
      blocked: shot.blocked ?? null,
      usage: shot.usage ?? null,
    })),
    gates,
    summary: {
      shotCount: results.length,
      candidateTotal: results.reduce((sum, s) => sum + (s.usage?.candidateCount ?? 0), 0),
      requiredMinimum: scope.totalCandidateMinimum ?? null,
      firstPassCandidates: results.reduce((sum, s) => sum + (s.usage?.technicalStatusCounts?.PASS ?? 0), 0),
    },
  };

  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, `${JSON.stringify(capsule, null, 2)}\n`, 'utf8');
  for (const gate of gates) {
    console.log(`${gate.result.padEnd(7)} ${gate.id}  ${gate.detail}`);
  }
  console.log(`capsule → ${OUT}`);

  const failed = gates.some((g) => g.result === 'FAIL');
  process.exitCode = failed ? 1 : awaitingManualQc ? 3 : 0;
}

main().catch((error) => {
  console.error(`runner 中断：${error.message}`);
  process.exitCode = 1;
});
