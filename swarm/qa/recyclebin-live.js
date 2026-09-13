// SW-T06 Round2 测试1:回收站全链路(真实后端 B1)
// 建→删→回收站可见→恢复→项目内可见→再删→彻底删除(物理);
// 归属校验:uitest 不能恢复/彻底删除 zhangyz 项目的资产;回收站列表按可访问项目隔离。
const fs = require('fs');
const { login, call, log } = require('./lib');

(async () => {
  const T = await login('zhangyz', 'zhangyz');
  const U = await login('uitest', 'Uitest#2026');
  const results = [];
  const record = (name, pass, detail) => { results.push({ name, pass, detail }); log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`); };
  const NAME = 'swt06-r2-recycle-' + Date.now();

  // 1. 创建资产(zhangyz,项目5)
  const created = await call(T, 'POST', '/api/asset', {
    projectId: 5, type: 'prop', name: NAME, description: 'QA Round2 回收站全链路测试资产',
  });
  const assetId = created.json && created.json.data && created.json.data.id;
  record('创建资产', created.status === 200 && !!assetId, `id=${assetId}`);
  if (!assetId) throw new Error('无法创建测试资产: ' + created.text);

  try {
    // 2. 软删除
    const del = await call(T, 'DELETE', `/api/asset/${assetId}`);
    record('软删除资产', del.status === 200 && del.json.data === true, `HTTP ${del.status}`);
    const gone = await call(T, 'GET', `/api/asset/${assetId}`, undefined, true);
    record('删除后项目内不可见(404)', gone.status === 404, `HTTP ${gone.status} ${gone.text.slice(0, 60)}`);

    // 3. 回收站可见
    const rb1 = await call(T, 'GET', '/api/asset/recycle-bin?page=1&size=50');
    const inBin = (rb1.json.data.records || []).find((r) => r.id === assetId);
    record('回收站列表可见(deleted=true)', rb1.status === 200 && !!inBin && inBin.deleted === true,
      `total=${rb1.json.data.total} 命中=${!!inBin}`);

    // 回收站分页边界
    const rbPage = await call(T, 'GET', '/api/asset/recycle-bin?page=1&size=1');
    record('回收站分页 size=1 生效', rbPage.status === 200 && (rbPage.json.data.records || []).length === 1
      && rbPage.json.data.total === rb1.json.data.total, `records=${(rbPage.json.data.records || []).length} total=${rbPage.json.data.total}`);

    // 4. uitest 归属校验(此时资产在回收站,属于 zhangyz 项目5)
    const uRestore = await call(U, 'PUT', `/api/asset/recycle-bin/${assetId}/restore`, undefined, true);
    record('uitest 恢复他人资产被拒(403)', uRestore.status === 403, `HTTP ${uRestore.status} ${uRestore.text.slice(0, 70)}`);
    const uPurge = await call(U, 'DELETE', `/api/asset/recycle-bin/${assetId}`, undefined, true);
    record('uitest 彻底删除他人资产被拒(403)', uPurge.status === 403, `HTTP ${uPurge.status} ${uPurge.text.slice(0, 70)}`);
    const uBin = await call(U, 'GET', '/api/asset/recycle-bin?page=1&size=50');
    const uLeak = (uBin.json.data.records || []).some((r) => r.id === assetId || r.projectId === 5);
    record('uitest 回收站不见他人资产', uBin.status === 200 && !uLeak, `uitest total=${uBin.json.data.total} 泄漏=${uLeak}`);

    // 5. 恢复(保留原 id)
    const restore = await call(T, 'PUT', `/api/asset/recycle-bin/${assetId}/restore`);
    record('恢复成功(保留原 id)', restore.status === 200 && restore.json.data && restore.json.data.id === assetId
      && restore.json.data.deleted === false, `HTTP ${restore.status} id=${restore.json.data && restore.json.data.id}`);
    const back = await call(T, 'GET', `/api/asset/${assetId}`);
    record('恢复后项目内可见', back.status === 200 && back.json.data.name === NAME, `HTTP ${back.status}`);
    const rb2 = await call(T, 'GET', '/api/asset/recycle-bin?page=1&size=50');
    record('恢复后回收站不再列出', !(rb2.json.data.records || []).some((r) => r.id === assetId), `total=${rb2.json.data.total}`);

    // 6. 再删→彻底删除(对应 UI 二次确认后的物理删除)
    await call(T, 'DELETE', `/api/asset/${assetId}`);
    const purge = await call(T, 'DELETE', `/api/asset/recycle-bin/${assetId}`);
    record('彻底删除(物理)', purge.status === 200 && purge.json.data === true, `HTTP ${purge.status}`);
    const afterPurge = await call(T, 'GET', `/api/asset/${assetId}`, undefined, true);
    const rb3 = await call(T, 'GET', '/api/asset/recycle-bin?page=1&size=50');
    record('彻底删除后资产与回收站均不可见', afterPurge.status === 404
      && !(rb3.json.data.records || []).some((r) => r.id === assetId), `GET=${afterPurge.status} bin total=${rb3.json.data.total}`);
  } finally {
    // 兜底清理:若资产仍存在(任何状态),尝试彻底删除
    const bin = await call(T, 'GET', '/api/asset/recycle-bin?page=1&size=50');
    const leftover = (bin.json.data.records || []).find((r) => r.name === NAME);
    if (leftover) { await call(T, 'DELETE', `/api/asset/recycle-bin/${leftover.id}`); log(`兜底清理回收站残留 ${leftover.id}`); }
  }

  // 7. 边界:恢复/彻底删除不存在 id、对未删除资产操作
  const rMiss = await call(T, 'PUT', '/api/asset/recycle-bin/999999/restore', undefined, true);
  log(`restore 不存在 id → HTTP ${rMiss.status} ${rMiss.text.slice(0, 90)}`);
  const pMiss = await call(T, 'DELETE', '/api/asset/recycle-bin/999999', undefined, true);
  log(`purge 不存在 id → HTTP ${pMiss.status} ${pMiss.text.slice(0, 90)}`);

  // 既有软删残留(id=21,t05 开发遗留,只读探测)
  const legacy = await call(T, 'GET', '/api/asset/recycle-bin?page=1&size=50');
  const legacyItem = (legacy.json.data.records || []).find((r) => r.id === 21);
  log(`开发遗留软删资产 id=21 仍在回收站: ${legacyItem ? legacyItem.name : '不在'}`);

  log('== 回收站全链路汇总 ==');
  results.forEach((r) => log(`${r.pass ? 'PASS' : 'FAIL'} ${r.name}`));
  fs.writeFileSync(__dirname + '/out/recyclebin-results.json', JSON.stringify(results, null, 2));
  process.exit(results.some((r) => !r.pass) ? 2 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
