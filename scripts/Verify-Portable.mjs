import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const dir = resolve(process.argv[2]);
assert(existsSync(resolve(dir, 'Start.ps1')));
assert(!existsSync(resolve(dir, 'data')), '只在刚解压的测试目录中运行');
const url = 'http://127.0.0.1:18765';
const password = randomUUID();
const answer = '便携版 AI 测试通过';
let modelCalls = 0;
const model = createServer(async (req, res) => {
  let body = '';
  for await (const chunk of req) body += chunk;
  const payload = JSON.parse(body);
  if (req.url !== '/chat/completions' || !payload.tools?.some(t => t.function?.name === 'reply_to_user')) {
    res.writeHead(400).end();
    return;
  }
  modelCalls++;
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({ choices: [{ finish_reason: 'tool_calls', message: { tool_calls: [{
    id: 'mock-reply', type: 'function', function: { name: 'reply_to_user', arguments: JSON.stringify({ answer }) }
  }] } }] }));
});
await new Promise(resolve => model.listen(0, '127.0.0.1', resolve));
let child, logs, token;
function launch(key = '', first = false) {
  logs = '';
  child = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'Start.ps1', '-NoBrowser'], {
    cwd: dir, windowsHide: true,
    env: { ...process.env, BOOTSTRAP_ADMIN_PASSWORD: first ? password : '', DEEPSEEK_API_KEY: key,
      DEEPSEEK_BASE_URL: 'http://127.0.0.1:' + model.address().port },
  });
  child.stdout.on('data', b => logs += b);
  child.stderr.on('data', b => logs += b);
}
function stop() {
  if (child?.pid && child.exitCode === null) {
    spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true });
    const drive = logs.match(/\[runtime-drive\] ([D-Z]:)/)?.[1];
    if (drive) spawnSync('subst.exe', [drive, '/D'], { windowsHide: true });
  }
}
async function api(path, body) {
  const response = await fetch(url + '/api/' + path, {
    method: body === undefined ? 'GET' : 'POST', signal: AbortSignal.timeout(10000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json();
  assert(response.ok && value.code === 0, path + ': ' + response.status + ' ' + value.message);
  return value.data;
}
async function login() {
  token = '';
  for (let attempt = 0; attempt < 90; attempt++) {
    if (child.exitCode !== null) throw Error(logs);
    try {
      token = (await api('auth/login', { username: 'admin', password })).token;
      if (token) return;
    } catch {}
    await delay(1000);
  }
  throw Error('登录等待超时\n' + logs);
}
try {
  launch('synthetic-test-fixture', true);
  await login();
  const loginPage = await fetch(url + '/login');
  assert.equal(loginPage.status, 200);
  const js = (await loginPage.text()).match(/src="(\/assets\/[^"]+\.js)"/)?.[1];
  assert(js);
  assert.equal((await fetch(url + js)).status, 200);
  assert.equal((await fetch(url + '/api/classes')).status, 401);
  assert.equal((await api('classes')).length, 0);
  const className = '便携版验收班';
  await api('classes', { className, grade: '高一', academicYear: '2026-2027' });
  assert.deepEqual(await api('ai/status'), { engine: 'DeepSeek', ready: true });
  const { sessionId } = await api('ai/sessions', {});
  const stream = await fetch(url + '/api/ai/chat/stream', {
    method: 'POST', signal: AbortSignal.timeout(10000),
    headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, message: '你好' }),
  });
  assert.equal(stream.status, 200);
  const result = (await stream.text()).match(/event:result\r?\ndata:([^\r\n]+)/)?.[1];
  assert(result, '没有收到 AI 流式结果');
  assert.equal(JSON.parse(result).answer, answer);
  assert.equal(modelCalls, 1);
  assert.equal((await api('classes'))[0]?.className, className);
  // taskkill /F 不执行数据库关闭流程，先给 H2 默认的延迟写入留出时间。
  await delay(1000);
  stop();
  await delay(2000);
  launch();
  await login();
  assert.equal((await api('classes'))[0]?.className, className);
  assert.deepEqual(await api('ai/status'), { engine: 'DeepSeek', ready: false });
  console.log('PASS：解压启动、登录、静态资源、API 鉴权、重启数据持久化、模拟模型调用及无 Key 启动');
} finally {
  stop();
  model.close();
}
