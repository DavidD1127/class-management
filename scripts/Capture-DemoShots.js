// 演示截图：用系统 Edge 跑一遍主要页面，输出到 output/demo-frames/。
// 只截图，不改业务数据；登录用本地演示账号。
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');

const BASE = process.env.HJ_WEB_BASE || 'http://127.0.0.1:15174';
const USER = process.env.HJ_DEMO_USER || '12345';
const PASS = process.env.HJ_DEMO_PASSWORD || '12345';
const OUT = path.join(__dirname, '..', 'output', 'demo-frames');

const shots = [];
async function snap(page, name, label) {
  const file = path.join(OUT, `${String(shots.length + 1).padStart(2, '0')}-${name}.png`);
  await page.waitForTimeout(600);
  await page.screenshot({ path: file, fullPage: false });
  shots.push({ file, label });
  console.log(`  ✓ ${name} → ${path.basename(file)}`);
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
    args: ['--force-device-scale-factor=1'],
  });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' });
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') console.log('    [console]', m.text().slice(0, 120)); });

  console.log('打开首页…');
  await page.goto(BASE, { waitUntil: 'networkidle', timeout: 60000 });
  await snap(page, 'login', '登录页');

  console.log('登录…');
  const inputs = page.locator('input');
  await inputs.nth(0).fill(USER);
  await inputs.nth(1).fill(PASS);
  await page.keyboard.press('Enter');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(1800);
  await snap(page, 'dashboard', '仪表盘');

  // 依次点侧栏一级入口
  const navs = [
    ['班级与学生', 'students'],
    ['成绩管理', 'scores'],
    ['日常记录', 'records'],
    ['AI 班级助手', 'ai'],
    ['回收站', 'recycle'],
  ];
  for (const [text, name] of navs) {
    const link = page.locator(`text=${text}`).first();
    if (await link.count()) {
      await link.click({ timeout: 8000 }).catch(() => {});
      await page.waitForTimeout(1500);
      await snap(page, name, text);
    } else {
      console.log(`  跳过 ${text}（未找到入口）`);
    }
  }

  // 作业子页
  const assignment = page.locator('text=作业').first();
  if (await assignment.count()) {
    await assignment.click({ timeout: 8000 }).catch(() => {});
    await page.waitForTimeout(1500);
    await snap(page, 'assignments', '作业管理');
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'frames.json'), JSON.stringify(shots, null, 1), 'utf-8');
  console.log(`\n共 ${shots.length} 张，清单写入 frames.json`);
})().catch((e) => { console.error('失败:', e.message); process.exit(1); });
