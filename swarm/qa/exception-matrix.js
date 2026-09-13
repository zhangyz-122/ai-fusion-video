// SW-T06 Round2 测试2:B2 异常语义修复回归矩阵
// 期望:越权→403,不存在→404,参数类型错误→400,方法不支持→405;不允许 500
const fs = require('fs');
const { login, call, log } = require('./lib');

(async () => {
  const T = await login('zhangyz', 'zhangyz');
  const U = await login('uitest', 'Uitest#2026');
  const results = [];
  const record = (name, expect, got, detail, pass) => {
    results.push({ name, expect, got, pass }); log(`${pass ? 'PASS' : 'FAIL'} ${name} 期望=${expect} 实际=${got}${detail ? ' — ' + detail : ''}`);
  };

  // 动态取当前首分集 id(脚本5 可能被并发重解析,旧 id 会消失导致 404 竞态伪影)
  const eps = await call(T, 'GET', '/api/script/5/episodes');
  const epId = eps.json.data[0].id;
  log(`动态分集 id=${epId}`);

  // [label, method, path, token, expectStatus]
  const cases = [
    // 越权(uitest → zhangyz 资源)
    ['uitest GET 他人项目详情', 'GET', '/api/project/5', U, 403],
    ['uitest GET 他人分集', 'GET', `/api/script/episode/${epId}`, U, 403],
    ['uitest GET 他人 SRT', 'GET', `/api/script/episode/${epId}/subtitle.srt`, U, 403],
    ['uitest GET 他人生产运行', 'GET', '/api/production/runs/5', U, 403],
    ['uitest PUT 恢复他人回收站资产', 'PUT', '/api/asset/recycle-bin/21/restore', U, 403],
    // 不存在
    ['GET 不存在项目', 'GET', '/api/project/999999', T, 404],
    ['GET 不存在分集', 'GET', '/api/script/episode/999999', T, 404],
    ['GET 不存在 SRT', 'GET', '/api/script/episode/999999/subtitle.srt', T, 404],
    ['GET 不存在生产运行', 'GET', '/api/production/runs/999999', T, 404],
    ['GET 不存在资产', 'GET', '/api/asset/999999', T, 404],
    ['PUT 恢复不存在回收站资产', 'PUT', '/api/asset/recycle-bin/999999/restore', T, 404],
    ['DELETE 彻底删除不存在回收站资产', 'DELETE', '/api/asset/recycle-bin/999999', T, 404],
    // 参数类型错误
    ['SRT secondsPerLine=abc', 'GET', '/api/script/episode/210/subtitle.srt?secondsPerLine=abc', T, 400],
    ['分集 id 非数字(path)', 'GET', '/api/script/episode/abc/subtitle.srt', T, 400],
    ['分集 id 非数字(plain)', 'GET', '/api/script/episode/abc', T, 400],
    ['生产 runId 非数字', 'GET', '/api/production/runs/xyz', T, 400],
    // 方法不支持
    ['PUT 端点用 GET 访问(restore)', 'GET', '/api/asset/recycle-bin/21/restore', T, 405],
    ['回收站列表用 POST 访问', 'POST', '/api/asset/recycle-bin', T, 405],
  ];

  for (const [name, method, path, token, expect] of cases) {
    const r = await call(token, method, path, undefined, true);
    let msg = '';
    try { msg = JSON.parse(r.text).msg || ''; } catch (_) { msg = r.text.slice(0, 50); }
    record(name, expect, r.status, msg.slice(0, 60), r.status === expect);
  }

  log('== 异常语义矩阵汇总 ==');
  const fails = results.filter((r) => !r.pass);
  results.forEach((r) => log(`${r.pass ? 'PASS' : 'FAIL'} ${r.name}: 期望 ${r.expect} 实际 ${r.got}`));
  log(`合计 ${results.length - fails.length}/${results.length} 通过`);
  fs.writeFileSync(__dirname + '/out/exception-matrix.json', JSON.stringify(results, null, 2));
  process.exit(fails.length ? 2 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
