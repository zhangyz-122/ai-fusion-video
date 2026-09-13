// SW-T06 测试1b:自动分块 chunkChars 钳制端到端真实运行
// 临时项目 + 12500 字原文,分别以 chunkChars=999999(应钳 12000→约2块)
// 与 chunkChars=-1(应钳 2000→约9块)真实跑解析,用分集数证明钳制生效。
// 完成后删除临时项目。
const fs = require('fs');
const { login, call, sleep, log } = require('./lib');

function buildRawText() {
  // 25 段 × 500 汉字 ≈ 12500 字,段间换行,无章节标题
  const sentences = [
    '夜色像一层薄纱压在城市的玻璃幕墙上',
    '她握紧手中的旧怀表听见齿轮转动的声音',
    '远处的火车鸣笛划破了长街的寂静',
    '少年在阁楼里翻出一封没有署名的信',
    '海风带着咸味吹过码头的旧仓库',
  ];
  let paras = [];
  let n = 0;
  for (let i = 0; i < 25; i++) {
    let para = '';
    while (para.length < 500) {
      para += '第' + (n + 1) + '，' + sentences[n % sentences.length] + '，事情远没有看起来那么简单，所有线索都指向那扇从不开启的木门。';
      n++;
    }
    paras.push(para.slice(0, 500));
  }
  return paras.join('\n');
}

async function waitSplitDone(token, scriptId, label, timeoutMs) {
  const start = Date.now();
  let last = '';
  while (Date.now() - start < timeoutMs) {
    const { status, json } = await call(token, 'GET', `/api/script/${scriptId}/auto-split`);
    if (status !== 200 || !json || json.code !== 0) {
      log(`  [${label}] 状态查询异常: HTTP ${status} ${JSON.stringify(json)}`);
      return null;
    }
    last = `parsingStatus=${json.data.parsingStatus} progress=${json.data.parsingProgress}`;
    if (json.data.parsingStatus === 2) {
      log(`  [${label}] 完成: ${last}, totalEpisodes=${json.data.totalEpisodes} (耗时 ${Math.round((Date.now() - start) / 1000)}s)`);
      return json.data;
    }
    if (json.data.parsingStatus === 3) {
      log(`  [${label}] 失败: ${last}`);
      return null;
    }
    await sleep(5000);
  }
  log(`  [${label}] 超时: ${last}`);
  return null;
}

(async () => {
  const token = await login('zhangyz', 'zhangyz');
  const raw = buildRawText();
  log(`原文长度: ${raw.length} 字 (25 段 × 500)`);

  // 1. 创建临时项目并初始化工作区
  const created = await call(token, 'POST', '/api/project', {
    name: 'SWT06-chunkChars-边界临时项目',
    description: 'QA 自动分块边界测试,可删除',
  });
  if (created.json.code !== 0) throw new Error('创建项目失败: ' + created.text);
  const projectId = created.json.data.id;
  log(`临时项目 id=${projectId}`);
  try {
    const init = await call(token, 'POST', `/api/project/${projectId}/workspace/initialize`);
    if (init.json.code !== 0) throw new Error('初始化工作区失败: ' + init.text);
    const sid = Number(init.json.data.script.id);
    if (!sid) throw new Error('无法取得剧本 id');
    log(`临时剧本 id=${sid}`);

    // 2. 写入原文
    const put = await call(token, 'PUT', `/api/script/${sid}/source`, { rawContent: raw });
    if (put.json.code !== 0) throw new Error('写入原文失败: ' + put.text);
    log('原文已写入');

    // 3. 跑两次解析:先 999999(钳 12000,约2-4块),再 -1(钳 2000,约6-9块)
    const cases = [
      { chunkChars: 999999, label: 'chunkChars=999999(应钳12000)', expectMin: 2, expectMax: 5 },
      { chunkChars: -1, label: 'chunkChars=-1(应钳2000)', expectMin: 6, expectMax: 13 },
    ];
    const results = [];
    for (const c of cases) {
      log(`>> ${c.label}`);
      const start = await call(token, 'POST', `/api/script/${sid}/auto-split`, { chunkChars: c.chunkChars });
      if (start.json.code !== 0) {
        log(`  启动失败: ${start.text}`);
        results.push({ ...c, pass: false });
        continue;
      }
      log(`  任务已启动 taskId=${start.json.data}`);
      const done = await waitSplitDone(token, sid, c.label, 12 * 60 * 1000);
      if (!done) { results.push({ ...c, pass: false }); continue; }
      const eps = await call(token, 'GET', `/api/script/${sid}/episodes`);
      const count = eps.json.data.length;
      // 无丢字:所有分集 rawContent 去空白后拼接 = 原文去空白
      const joined = eps.json.data
        .map((e) => (e.rawContent || '').replace(/\s+/g, '')).join('');
      const noLoss = joined === raw.replace(/\s+/g, '');
      const sizesOk = eps.json.data.every((e) => (e.rawContent || '').length <= c.expectMax * 2100);
      const pass = count >= c.expectMin && count <= c.expectMax && noLoss;
      log(`  分集数=${count} (期望 ${c.expectMin}-${c.expectMax}) 无丢字=${noLoss} → ${pass ? 'PASS' : 'FAIL'}`);
      results.push({ ...c, count, noLoss, pass });
    }

    log('== chunkChars 钳制 E2E 汇总 ==');
    for (const r of results) log(`${r.pass ? 'PASS' : 'FAIL'} ${r.label} 实际分集=${r.count}`);
    fs.writeFileSync(__dirname + '/out/chunkchars-e2e.json',
      JSON.stringify({ projectId, scriptId: sid, results }, null, 2));
  } finally {
    const del = await call(token, 'DELETE', `/api/project/${projectId}`);
    log(`清理临时项目 ${projectId}: HTTP ${del.status} code=${del.json && del.json.code}`);
  }
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
