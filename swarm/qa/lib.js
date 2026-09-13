// SW-T06 公共库:8081 平台 API 客户端(真实 HTTP)
const BASE = process.env.PLATFORM_BASE || 'http://localhost:8081';
const fs = require('fs');
const OUT = __dirname + '/out';

async function login(username, password) {
  const res = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const body = await res.json();
  if (!body.data || !body.data.accessToken) throw new Error('login failed: ' + JSON.stringify(body));
  return body.data.accessToken;
}

async function call(token, method, path, body, raw) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + token,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (raw) return { status: res.status, headers: res.headers, text };
  let json = null;
  try { json = JSON.parse(text); } catch (_) { /* non-JSON */ }
  return { status: res.status, headers: res.headers, json, text };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function log(line) {
  console.log(line);
  fs.appendFileSync(OUT + '/run-log.txt', line + '\n');
}

module.exports = { BASE, OUT, login, call, sleep, log };
