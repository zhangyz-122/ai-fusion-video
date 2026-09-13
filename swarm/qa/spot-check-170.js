// SW-T06 测试1c:170 集长文解析结果抽查(随机 3 集)+ 全量结构校验
const fs = require('fs');
const { login, call, log } = require('./lib');

// 线性同余伪随机(可复现)
let seed = 20260913;
const rand = (n) => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed % n; };

function loadSentences(text) {
  return text.split(/[。！？\n]/).map((s) => s.trim()).filter((s) => s.length >= 30);
}

(async () => {
  const token = await login('zhangyz', 'zhangyz');
  const eps = (await call(token, 'GET', '/api/script/5/episodes')).json.data;
  log(`脚本5 分集数=${eps.length}`);

  // 全量结构断言:编号连续 1..170、场景数≥1、有原文
  let structOk = true;
  eps.forEach((e, i) => {
    if (e.episodeNumber !== i + 1) { structOk = false; log(`  编号断裂 @${i}: ${e.episodeNumber}`); }
    if (!(e.totalScenes >= 1)) { structOk = false; log(`  分集 ${e.id} totalScenes=${e.totalScenes}`); }
    if (!e.rawContent || !e.rawContent.trim()) { structOk = false; log(`  分集 ${e.id} rawContent 空`); }
  });
  log(`全量结构(编号连续/场景数≥1/原文非空): ${structOk ? 'PASS' : 'FAIL'}`);

  // 无丢字:170 集 rawContent 拼接 == 剧本原文
  const script = (await call(token, 'GET', '/api/script/5')).json.data;
  const joined = eps.map((e) => (e.rawContent || '').replace(/\s+/g, '')).join('');
  const noLoss = joined === (script.rawContent || '').replace(/\s+/g, '');
  log(`170 集原文拼接 vs 剧本原文: ${noLoss ? 'PASS(无丢字)' : 'FAIL'}`);
  if (!noLoss) log(`  长度: joined=${joined.length} raw=${(script.rawContent || '').length}`);

  // 随机抽 3 集:场景非空、非原文整段照抄、对白结构
  const picks = [];
  while (picks.length < 3) {
    const e = eps[rand(eps.length)];
    if (!picks.find((p) => p.id === e.id)) picks.push(e);
  }
  log(`随机抽样(种子 20260913): ${picks.map((p) => `#${p.episodeNumber}/id${p.id}`).join(', ')}`);
  const details = [];
  for (const ep of picks) {
    const sc = (await call(token, 'GET', `/api/script/episode/${ep.id}/scenes`)).json.data || [];
    const rawNorm = (ep.rawContent || '').replace(/\s+/g, '');
    const isVerbatimFallback = sc.some((s) => /^(原文片段|第1部分)/.test(s.sceneHeading || ''));
    const copiedScenes = sc.filter((s) => (s.sceneDescription || '').replace(/\s+/g, '') === rawNorm).length;
    // 原文长句照抄率
    const sentences = loadSentences(ep.rawContent || '');
    const descAll = sc.map((s) => s.sceneDescription || '').join('\n');
    const verbatimSent = sentences.filter((s) => descAll.includes(s)).length;
    const copyRate = sentences.length ? verbatimSent / sentences.length : 0;
    const dialogueScenes = sc.filter((s) => /对白：/.test(s.sceneDescription || '') || (s.dialogues || '').includes('"')).length;
    const ok = sc.length >= 1 && sc.length === ep.totalScenes && !isVerbatimFallback && copiedScenes === 0 && copyRate < 0.5;
    log(`分集 #${ep.episodeNumber}「${ep.title}」: scenes=${sc.length}/${ep.totalScenes} 原文兜底=${isVerbatimFallback} 整段照抄场景=${copiedScenes} 长句照抄率=${(copyRate * 100).toFixed(0)}% 含对白场次=${dialogueScenes} → ${ok ? 'PASS' : 'FAIL'}`);
    log(`  场次标题样例: ${sc.slice(0, 3).map((s) => s.sceneHeading).join(' / ')}`);
    log(`  描述字数/原文字数: ${sc.reduce((a, s) => a + (s.sceneDescription || '').length, 0)}/${(ep.rawContent || '').length}`);
    details.push({ ep: ep.id, no: ep.episodeNumber, scenes: sc.length, verbatimFallback: isVerbatimFallback, copyRate, ok });
  }

  const summary = { structOk, noLoss, picks: details };
  fs.writeFileSync(__dirname + '/out/spot-check-170.json', JSON.stringify(summary, null, 2));
  log(`== 170 集抽查汇总 == 结构=${structOk ? 'PASS' : 'FAIL'} 无丢字=${noLoss ? 'PASS' : 'FAIL'} 抽样=${details.map((d) => d.ok ? 'PASS' : 'FAIL').join(',')}`);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
