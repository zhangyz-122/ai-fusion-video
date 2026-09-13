/**
 * 前端架构守护:行数预算棘轮(SW-T14,"先红后拆")。
 *
 * 规则来源:AGENTS.md「前端单文件不得超过 1000 行」;白名单=swarm/TECH_DEBT_PLAN.md Top 清单
 * (storyboard-ref-panel / storyboards page / asset-detail-sheet / pipeline-store /
 * notification detail),钉在登记时的行数。
 *
 * 守护语义(与后端 BackendArchitectureGuardTests 一致):
 * - 白名单之外出现 >1000 行文件 → 红(新债必须拆分或经 Architect 评审登记);
 * - 白名单文件行数继续增长 → 红(拆分前冻结);
 * - 白名单文件已降到 ≤1000 行而条目残留 → 红(白名单只减不增);
 * - 500–1000 行为观察档,仅打印统计,不拦截。
 *
 * 运行:在 ai-fusion-video-web/ 目录执行
 * `node --test "tests/architecture/*.test.mjs"`(Windows 下目录形式参数不可靠,用 glob 或直呼文件)。
 * 无第三方依赖,仅需 Node ≥ 18。
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const MODULE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const SCAN_DIRS = ['app', 'components', 'lib'];
const EXTENSIONS = new Set(['.ts', '.tsx']);
const LINE_BUDGET = 1000;
const WARN_FLOOR = 500;

/** key=模块根 POSIX 相对路径,value=登记行数(只许减少)。owner=Architect(Agent-1)。 */
const WHITELIST = {
  'components/dashboard/asset-detail-sheet.tsx': 1465,
  'lib/store/pipeline-store.ts': 1140,
  'components/dashboard/notification-panel/detail.tsx': 1028,
};

function listSourceFiles(dir) {
  const out = [];
  const stack = [dir];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
      } else if (EXTENSIONS.has(path.extname(entry.name))) {
        out.push(full);
      }
    }
  }
  return out;
}

test('frontend files stay within line budget', () => {
  const newViolations = [];
  const grownWhitelisted = [];
  const payableWhitelisted = [];
  const warnTier = [];
  const whitelistedNow = new Map();

  for (const dir of SCAN_DIRS) {
    for (const file of listSourceFiles(path.join(MODULE_ROOT, dir))) {
      const rel = path.relative(MODULE_ROOT, file).split(path.sep).join('/');
      const lines = fs.readFileSync(file, 'utf8').split('\n').length - 1; // 与 wc -l 同口径
      if (lines > LINE_BUDGET) {
        const pinned = WHITELIST[rel];
        if (pinned === undefined) {
          newViolations.push(`${rel} = ${lines} 行(>${LINE_BUDGET}):必须拆分,或经 Architect 评审后登记白名单`);
        } else {
          whitelistedNow.set(rel, lines);
          if (lines > pinned) {
            grownWhitelisted.push(`${rel} = ${lines} 行 > 白名单上限 ${pinned} 行:拆分前禁止继续增长`);
          }
        }
      } else if (Object.prototype.hasOwnProperty.call(WHITELIST, rel)) {
        payableWhitelisted.push(`${rel} 已降到 ${lines} 行(≤${LINE_BUDGET}):请从白名单移除该条目`);
      } else if (lines > WARN_FLOOR) {
        warnTier.push(`${rel} = ${lines}`);
      }
    }
  }

  assert.deepEqual(
    newViolations,
    [],
    `出现白名单外的超行文件(先红后拆:拆分或登记,禁止静默超标):\n${newViolations.join('\n')}`,
  );
  assert.deepEqual(
    grownWhitelisted,
    [],
    `白名单文件行数增长(拆分前冻结):\n${grownWhitelisted.join('\n')}`,
  );
  assert.deepEqual(
    payableWhitelisted,
    [],
    `白名单存在已达标条目(白名单只减不增):\n${payableWhitelisted.join('\n')}`,
  );

  console.log(
    `[arch-guard] 前端 >${LINE_BUDGET} 行白名单在册 ${whitelistedNow.size}/${Object.keys(WHITELIST).length}:`,
    Object.fromEntries(whitelistedNow),
  );
  console.log(
    `[arch-guard] 前端 ${WARN_FLOOR}-${LINE_BUDGET} 行观察档 ${warnTier.length} 个(不拦截)`,
  );
});
