// SW-T06 测试3+4:系统状态类型断言/uitest 403 + 生产运行分页与过滤边界
const fs = require('fs');
const { login, call, log } = require('./lib');

const isNum = (v) => typeof v === 'number' && Number.isFinite(v) && v >= 0;
const isStr = (v) => typeof v === 'string';
const isBool = (v) => typeof v === 'boolean';

(async () => {
  const admin = await login('zhangyz', 'zhangyz');
  const uitest = await login('uitest', 'Uitest#2026');
  const results = [];
  const record = (name, pass, detail) => { results.push({ name, pass, detail }); log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`); };

  // ---------- 测试3:系统状态 ----------
  const health = await call(admin, 'GET', '/api/system/status/health');
  {
    const d = health.json && health.json.data;
    const checks = d && isStr(d.status) && ['UP', 'DOWN'].includes(d.status)
      && isStr(d.checkedAt) && !Number.isNaN(Date.parse(d.checkedAt))
      && d.components && isStr(d.components.backend.status) && isStr(d.components.database.status);
    record('health 管理员 200 + 类型', health.status === 200 && !!checks,
      `status=${d && d.status} db=${d && d.components && d.components.database.status} checkedAt=${d && d.checkedAt}`);
  }

  const storage = await call(admin, 'GET', '/api/system/status/storage');
  {
    const d = storage.json && storage.json.data;
    const c = d && d.categories;
    const statOk = (x) => x && isNum(x.fileCount) && isNum(x.totalBytes);
    const checks = d && isStr(d.storageType) && isStr(d.basePath) && isBool(d.exists)
      && statOk(c && c.images) && statOk(c && c.videos) && statOk(c && c.composed)
      && isNum(d.totalFiles) && isNum(d.totalBytes)
      && d.totalFiles === c.images.fileCount + c.videos.fileCount + c.composed.fileCount
      && d.totalBytes === c.images.totalBytes + c.videos.totalBytes + c.composed.totalBytes;
    record('storage 管理员 200 + 类型 + 加和一致', storage.status === 200 && !!checks,
      d ? `files=${d.totalFiles} bytes=${d.totalBytes} images=${c.images.fileCount} videos=${c.videos.fileCount} composed=${c.composed.fileCount}` : 'null');
  }

  const vq = await call(admin, 'GET', '/api/system/status/video-queue');
  {
    const d = vq.json && vq.json.data;
    const sumOk = d && Array.isArray(d.queues)
      && d.totalPending === d.queues.reduce((a, q) => a + (q.pending || 0), 0)
      && d.totalRunning === d.queues.reduce((a, q) => a + (q.running || 0), 0);
    const checks = d && isBool(d.available) && Array.isArray(d.queues)
      && isNum(d.totalPending) && isNum(d.totalRunning) && sumOk
      && d.queues.every((q) => isStr(q.name) && isNum(q.pending) && isNum(q.running) && isNum(q.maxConcurrent));
    record('video-queue 管理员 200 + 类型 + 加和一致', vq.status === 200 && !!checks,
      d ? `available=${d.available} queues=${d.queues.length} pending=${d.totalPending} running=${d.totalRunning}` : 'null');
  }

  for (const ep of ['/api/system/status/health', '/api/system/status/storage', '/api/system/status/video-queue']) {
    const r = await call(uitest, 'GET', ep);
    record(`uitest 访问 ${ep.split('/').pop()} → 403`, r.status === 403, `HTTP ${r.status}`);
    const anon = await fetch('http://localhost:8081' + ep);
    record(`匿名访问 ${ep.split('/').pop()} → 401/403`, anon.status === 401 || anon.status === 403, `HTTP ${anon.status}`);
  }

  // ---------- 测试4:生产运行 ----------
  const base = (await call(admin, 'GET', '/api/production/runs?pageNo=1&pageSize=100')).json.data;
  const total = base.total !== undefined ? base.total : base.list.length;
  log(`zhangyz 运行总数 total=${total}, status 分布=${JSON.stringify(base.list.reduce((m, r) => ({ ...m, [r.status]: (m[r.status] || 0) + 1 }), {}))}`);

  // 分页边界 pageNo=0
  const p0 = await call(admin, 'GET', '/api/production/runs?pageNo=0&pageSize=10', undefined, true);
  log(`pageNo=0 → HTTP ${p0.status} ${p0.text.slice(0, 160)}`);
  const p0Body = JSON.parse(p0.text);
  const ids0 = (p0Body.data.list || []).map((r) => r.id);
  const ids1 = (await call(admin, 'GET', '/api/production/runs?pageNo=1&pageSize=10')).json.data.list.map((r) => r.id);
  const samePage = JSON.stringify(ids0) === JSON.stringify(ids1);
  record('pageNo=0 行为(与第1页一致或 400)', (p0.status === 200 && samePage) || p0.status === 400,
    `ids0=${JSON.stringify(ids0)} ids1=${JSON.stringify(ids1)}`);

  // 分页边界 pageNo=999
  const p999 = await call(admin, 'GET', '/api/production/runs?pageNo=999&pageSize=10');
  record('pageNo=999 → 200 空列表且 total 保留', p999.status === 200 && p999.json.code === 0
    && (p999.json.data.list || []).length === 0 && p999.json.data.total === total,
  `list=${(p999.json.data.list || []).length} total=${p999.json.data.total}`);

  // pageSize 边界
  for (const ps of [0, -1, 1000]) {
    const r = await call(admin, 'GET', `/api/production/runs?pageNo=1&pageSize=${ps}`, undefined, true);
    let n = null;
    try { n = JSON.parse(r.text).data.list.length; } catch (_) { /* ignore */ }
    log(`pageSize=${ps} → HTTP ${r.status} list=${n} ${r.status === 200 ? '' : r.text.slice(0, 120)}`);
    record(`pageSize=${ps} 不 500 且结构完整`, r.status === 200 && Array.isArray(JSON.parse(r.text).data.list),
      `HTTP ${r.status} list=${n}`);
  }

  // 状态过滤
  const statuses = ['CREATED', 'WAITING_GENERATION', 'QC_PENDING', 'SELECTED', 'FAILED'];
  let filteredSum = 0;
  for (const st of statuses) {
    const r = await call(admin, 'GET', `/api/production/runs?status=${st}&pageNo=1&pageSize=100`);
    const list = (r.json.data && r.json.data.list) || [];
    filteredSum += list.length;
    const allMatch = list.every((x) => x.status === st);
    record(`status=${st} 过滤(全部匹配)`, r.status === 200 && allMatch, `count=${list.length}`);
  }
  log(`各状态计数之和=${filteredSum} (应等于 total=${total} 或小于=存在未知状态)`);
  const bad = await call(admin, 'GET', '/api/production/runs?status=NOT_A_STATUS&pageNo=1&pageSize=100');
  record('status=NOT_A_STATUS → 200 空列表', bad.status === 200 && (bad.json.data.list || []).length === 0, `HTTP ${bad.status}`);
  const lower = await call(admin, 'GET', '/api/production/runs?status=selected&pageNo=1&pageSize=100');
  const lowerCount = (lower.json.data.list || []).length;
  log(`status=selected(小写) → count=${lowerCount} (大小写敏感则为 0)`);
  const empty = await call(admin, 'GET', '/api/production/runs?status=&pageNo=1&pageSize=100');
  record('status=(空串) 等价于不过滤', empty.status === 200 && (empty.json.data.list || []).length === base.list.length,
    `count=${(empty.json.data.list || []).length}`);

  // uitest 隔离
  const u = await call(uitest, 'GET', '/api/production/runs?pageNo=1&pageSize=100');
  const uList = (u.json.data && u.json.data.list) || [];
  const uIds = uList.map((r) => r.id);
  const aIds = base.list.map((r) => r.id);
  const overlap = uIds.filter((id) => aIds.includes(id));
  const uUsers = [...new Set(uList.map((r) => r.userId))];
  log(`uitest 运行数=${uList.length} userId 集合=${JSON.stringify(uUsers)} 与 zhangyz 重叠 id=${JSON.stringify(overlap)}`);
  record('uitest 只见己方运行', u.status === 200 && overlap.length === 0
    && uList.every((r) => r.userId !== 1), `count=${uList.length}`);
  if (aIds.length) {
    const cross = await call(uitest, 'GET', `/api/production/runs/${aIds[0]}`, undefined, true);
    log(`uitest 访问 zhangyz 运行 ${aIds[0]} → HTTP ${cross.status} ${cross.text.slice(0, 120)}`);
    record('uitest 访问他人 run 详情 → 非 200', cross.status !== 200, `HTTP ${cross.status}`);
  }

  log('== 系统状态/生产运行 汇总 ==');
  results.forEach((r) => log(`${r.pass ? 'PASS' : 'FAIL'} ${r.name}`));
  fs.writeFileSync(__dirname + '/out/status-production-results.json', JSON.stringify(results, null, 2));
  process.exit(results.some((r) => !r.pass) ? 2 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
