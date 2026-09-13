// SW-T06 测试2:字幕导出真实运行
// 1) 真实分集下载:序号连续/时间轴单调/每条时长=secondsPerLine/Content-Type
// 2) secondsPerLine 越界:0/-5/61/abc → 400;1/60 → 200 且时间轴吻合
// 3) 空对白分集:应 400"该分集暂无可导出的对白"
// 4) 越权:uitest 访问 zhangyz 分集 → 403
const fs = require('fs');
const { OUT, login, call, log } = require('./lib');

const TIMESTAMP_RE = /^\d{2}:\d{2}:\d{2},\d{3}$/;

function toMillis(ts) {
  const [h, m, s, ms] = ts.split(/[:,]/).map(Number);
  return ((h * 60 + m) * 60 + s) * 1000 + ms;
}

function parseSrt(text) {
  const blocks = text.replace(/\r\n/g, '\n').trim().split('\n\n');
  return blocks.map((b) => {
    const [idx, range, ...content] = b.split('\n');
    const [start, end] = range.split(' --> ');
    return { idx: Number(idx), start, end, content: content.join('\n') };
  });
}

function assertCues(cues, perLineMs, label) {
  let ok = true;
  let prevEnd = -1;
  cues.forEach((c, i) => {
    if (c.idx !== i + 1) { log(`  [${label}] FAIL 第${i + 1}条序号=${c.idx}`); ok = false; }
    if (!TIMESTAMP_RE.test(c.start) || !TIMESTAMP_RE.test(c.end)) { log(`  [${label}] FAIL 时间格式 ${c.start}`); ok = false; }
    const s = toMillis(c.start), e = toMillis(c.end);
    if (s < prevEnd) { log(`  [${label}] FAIL 时间轴回退 @${i + 1}`); ok = false; }
    if (e - s !== perLineMs) { log(`  [${label}] FAIL 第${i + 1}条时长=${e - s}`); ok = false; }
    if (!c.content || !c.content.trim()) { log(`  [${label}] FAIL 第${i + 1}条空文本`); ok = false; }
    prevEnd = e;
  });
  log(`  [${label}] ${cues.length} 条 cue 校验 → ${ok ? 'PASS' : 'FAIL'}`);
  return ok;
}

(async () => {
  const token = await login('zhangyz', 'zhangyz');
  const uitest = await login('uitest', 'Uitest#2026');
  const results = [];
  const record = (name, pass, detail) => { results.push({ name, pass, detail }); log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`); };

  // ---------- 真实分集下载 ----------
  const eps = (await call(token, 'GET', '/api/script/5/episodes')).json.data;
  // 挑一个有较多场次的分集(第 1 集 id=210)
  const ep = eps[0];
  const scenes = (await call(token, 'GET', `/api/script/episode/${ep.id}/scenes`)).json.data;
  log(`下载目标: 分集 id=${ep.id}「${ep.title}」 scenes=${scenes.length}`);

  const dl = await call(token, 'GET', `/api/script/episode/${ep.id}/subtitle.srt?secondsPerLine=2`, undefined, true);
  log(`HTTP ${dl.status} Content-Type=${dl.headers.get('content-type')} Disposition=${(dl.headers.get('content-disposition') || '').slice(0, 80)}`);
  fs.writeFileSync(OUT + `/sample-ep${ep.id}-spl2.srt`, dl.text);
  if (dl.status === 200 && /application\/x-subrip/.test(dl.headers.get('content-type') || '')) {
    const cues = parseSrt(dl.text);
    const ok = assertCues(cues, 2000, 'secondsPerLine=2');
    // 抽查内容来源:cue 文本应出现在场次描述/对白中(去除说话人前缀后至少部分命中)
    const descAll = scenes.map((s) => (s.sceneDescription || '') + '\n' + (s.dialogues || '')).join('\n');
    const hit = cues.filter((c) => descAll.includes(c.content.replace(/^[^：:]{1,16}[：:]/, ''))).length;
    log(`  内容溯源: ${hit}/${cues.length} 条 cue 文本可在场次数据中找到`);
    record('真实分集下载(序号/时间轴/时长)', ok, `ep=${ep.id} cues=${cues.length}`);
  } else {
    record('真实分集下载(序号/时间轴/时长)', false, `HTTP ${dl.status}`);
  }

  // ---------- secondsPerLine 越界 ----------
  for (const bad of [0, -5, 61, 999999]) {
    const r = await call(token, 'GET', `/api/script/episode/${ep.id}/subtitle.srt?secondsPerLine=${bad}`, undefined, true);
    const body = r.text.slice(0, 120);
    log(`secondsPerLine=${bad} → HTTP ${r.status} ${body}`);
    record(`secondsPerLine=${bad} 拒绝`, r.status === 400, `HTTP ${r.status}`);
  }
  const badStr = await call(token, 'GET', `/api/script/episode/${ep.id}/subtitle.srt?secondsPerLine=abc`, undefined, true);
  log(`secondsPerLine=abc → HTTP ${badStr.status} ${badStr.text.slice(0, 120)}`);
  record('secondsPerLine=abc 拒绝', badStr.status === 400, `HTTP ${badStr.status}`);

  for (const good of [1, 60]) {
    const r = await call(token, 'GET', `/api/script/episode/${ep.id}/subtitle.srt?secondsPerLine=${good}`, undefined, true);
    if (r.status === 200) {
      const ok = assertCues(parseSrt(r.text), good * 1000, `secondsPerLine=${good}`);
      fs.writeFileSync(OUT + `/sample-ep${ep.id}-spl${good}.srt`, r.text);
      record(`secondsPerLine=${good} 边界接受`, ok, `${r.text.trim().split('\n\n').length} cues`);
    } else {
      record(`secondsPerLine=${good} 边界接受`, false, `HTTP ${r.status} ${r.text.slice(0, 100)}`);
    }
  }

  // ---------- 空对白分集 ----------
  // 扫描脚本5 的候选小分集,找描述中不含对白行(无"角色:"模式且 dialogues 无效/空)的分集
  const candidates = eps.filter((e) => e.totalScenes <= 2).slice(0, 12);
  let emptyEp = null;
  for (const c of candidates) {
    const sc = (await call(token, 'GET', `/api/script/episode/${c.id}/scenes`)).json.data || [];
    const hasDialogue = sc.some((s) => {
      if (s.dialogues && s.dialogues.trim().startsWith('[') && s.dialogues.includes('"')) {
        try {
          const arr = JSON.parse(s.dialogues);
          if (Array.isArray(arr) && arr.length) return true;
        } catch (_) { /* fallthrough */ }
      }
      return /(^|\n)\s*\*{0,2}[^*\n:：，。；！？、\n]{1,16}\*{0,2}[：:]/.test(s.sceneDescription || '');
    });
    if (!hasDialogue && sc.length) { emptyEp = { ep: c, scenes: sc }; break; }
  }
  if (emptyEp) {
    const r = await call(token, 'GET', `/api/script/episode/${emptyEp.ep.id}/subtitle.srt`, undefined, true);
    log(`空对白分集 id=${emptyEp.ep.id}「${emptyEp.ep.title}」 → HTTP ${r.status} ${r.text.slice(0, 140)}`);
    record('空对白分集拒绝(400,不产空文件)', r.status === 400, r.text.slice(0, 100));
  } else {
    record('空对白分集拒绝(400,不产空文件)', false, '脚本5 中未找到无对白分集,需另行构造');
  }

  // ---------- 越权与不存在 ----------
  // 集成版语义:他人分集/不存在分集统一 404(反枚举;与 project/run 模块的 403 不一致,见 BUGS)
  const cross = await call(uitest, 'GET', `/api/script/episode/${ep.id}/subtitle.srt`, undefined, true);
  log(`uitest 访问 zhangyz 分集 SRT → HTTP ${cross.status} ${cross.text.slice(0, 100)}`);
  record('uitest 越权访问分集 SRT 被拒(404/403)', cross.status === 404 || cross.status === 403, `HTTP ${cross.status}`);
  const missing = await call(token, 'GET', '/api/script/episode/999999/subtitle.srt', undefined, true);
  log(`不存在分集 → HTTP ${missing.status} ${missing.text.slice(0, 100)}`);
  record('不存在分集 → 404', missing.status === 404, `HTTP ${missing.status}`);

  log('== 字幕导出汇总 ==');
  results.forEach((r) => log(`${r.pass ? 'PASS' : 'FAIL'} ${r.name}`));
  fs.writeFileSync(OUT + '/subtitle-results.json', JSON.stringify(results, null, 2));
  process.exit(results.some((r) => !r.pass) ? 2 : 0);
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
