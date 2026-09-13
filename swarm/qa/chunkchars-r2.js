// SW-T06 Round2 测试4:自动分块 v2 chunkChars 边界(4000 字小文本真跑)
// A: chunkChars=999999 → 钳12000 → 1 集;B: chunkChars=-1 → 钳2000 → 2-3 集(显式 GLM 模型);
// C: 默认模型(不传 modelId)→ 当前默认指向 Ollama qwen2.5:14b(已宕),观察静默兜底行为;
// D: chunkChars="abc" → 400。临时项目最后删除。
const fs = require('fs');
const { login, call, sleep, log } = require('./lib');

function buildRawText() {
  const sentences = [
    '夜色像一层薄纱压在城市的玻璃幕墙上',
    '她握紧手中的旧怀表听见齿轮转动的声音',
    '远处的火车鸣笛划破了长街的寂静',
    '少年在阁楼里翻出一封没有署名的信',
  ];
  const paras = [];
  let n = 0;
  for (let i = 0; i < 8; i++) {
    let para = '';
    while (para.length < 500) {
      para += '第' + (n + 1) + '，' + sentences[n % sentences.length] + '，事情远没有看起来那么简单，所有线索都指向那扇从不开启的木门。';
      n++;
    }
    paras.push(para.slice(0, 500));
  }
  return paras.join('\n');
}

async function waitDone(token, scriptId, label, timeoutMs) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const { json } = await call(token, 'GET', `/api/script/${scriptId}/auto-split`);
    if (json && json.code === 0 && (json.data.parsingStatus === 2 || json.data.parsingStatus === 3)) {
      log(`  [${label}] 终态: parsingStatus=${json.data.parsingStatus} progress=${json.data.parsingProgress} (耗时 ${Math.round((Date.now() - start) / 1000)}s)`);
      return json.data;
    }
    await sleep(4000);
  }
  log(`  [${label}] 超时`);
  return null;
}

(async () => {
  const token = await login('zhangyz', 'zhangyz');
  const raw = buildRawText();
  log(`原文长度: ${raw.length} 字 (8 段 × 500)`);
  const created = await call(token, 'POST', '/api/project', { name: 'SWT06-R2-分块边界临时项目', description: 'QA Round2,可删除' });
  const projectId = created.json.data.id;
  log(`临时项目 id=${projectId}`);
  try {
    const init = await call(token, 'POST', `/api/project/${projectId}/workspace/initialize`);
    const sid = Number(init.json.data.script.id);
    await call(token, 'PUT', `/api/script/${sid}/source`, { rawContent: raw });
    log(`临时剧本 id=${sid},原文已写入`);

    // D: 非法类型(反序列化失败,不启动任务)
    const bad = await call(token, 'POST', `/api/script/${sid}/auto-split`, { chunkChars: 'abc', modelId: 7 }, true);
    log(`chunkChars="abc" → HTTP ${bad.status} ${bad.text.slice(0, 100)}`);
    const badOk = bad.status === 400;

    const run = async (label, body, expectMin, expectMax, needConvert) => {
      log(`>> ${label}`);
      const start = await call(token, 'POST', `/api/script/${sid}/auto-split`, body);
      if (start.json.code !== 0) { log(`  启动失败: ${start.text.slice(0, 120)}`); return { label, pass: false }; }
      const done = await waitDone(token, sid, label, 10 * 60 * 1000);
      if (!done) return { label, pass: false };
      const eps = (await call(token, 'GET', `/api/script/${sid}/episodes`)).json.data;
      const joined = eps.map((e) => (e.rawContent || '').replace(/\s+/g, '')).join('');
      const noLoss = joined === raw.replace(/\s+/g, '');
      const verbatim = eps.filter((e) => (e.totalScenes === 1)).length; // 粗略:兜底集单场景
      const headings = [];
      for (const e of eps.slice(0, 3)) {
        const sc = (await call(token, 'GET', `/api/script/episode/${e.id}/scenes`)).json.data || [];
        headings.push(sc.map((s) => s.sceneHeading).join('/'));
      }
      const inRange = eps.length >= expectMin && eps.length <= expectMax;
      const pass = inRange && noLoss;
      log(`  分集数=${eps.length} (期望 ${expectMin}-${expectMax}) 无丢字=${noLoss} 单场景集数=${verbatim}`);
      log(`  场次标题样例: ${headings.join(' | ')}`);
      return { label, count: eps.length, noLoss, verbatim, headings, pass };
    };

    const out = [];
    out.push(await run('A: chunkChars=999999(应钳12000,期望1集)', { chunkChars: 999999, modelId: 7 }, 1, 1));
    out.push(await run('B: chunkChars=-1(应钳2000,期望2-3集)', { chunkChars: -1, modelId: 7 }, 2, 3));
    out.push(await run('C: 默认模型(不传modelId,当前默认=Ollama qwen2.5:14b 已宕)', { chunkChars: 2000 }, 2, 4));
    out.push({ label: 'D: chunkChars="abc" → 400', pass: badOk });

    log('== Round2 分块边界 E2E 汇总 ==');
    out.forEach((r) => log(`${r.pass ? 'PASS' : 'FAIL'} ${r.label}${r.count != null ? ' 实际分集=' + r.count : ''}`));
    fs.writeFileSync(__dirname + '/out/chunkchars-r2.json', JSON.stringify(out, null, 2));
  } finally {
    const del = await call(token, 'DELETE', `/api/project/${projectId}`);
    log(`清理临时项目 ${projectId}: HTTP ${del.status}`);
  }
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
