/** Browser acceptance checks; artifacts stay in build/, with no test source directory. */
import { createRequire } from 'node:module';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
let playwright;
try { playwright = require('@playwright/test'); }
catch { playwright = require('../../portable/web/node_modules/@playwright/test'); }
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
await fs.mkdir(path.join(root, 'build'), { recursive: true });
const base = process.env.SITE_URL || 'http://127.0.0.1:8097/Valthorne/';
const selected = process.env.TEST_BROWSER || 'chromium';
const browser = await (playwright[selected] || playwright.chromium).launch({
  headless: true, ...(selected === 'chrome' || selected === 'msedge' ? { channel: selected } : {})
});
const context = await browser.newContext({ viewport: { width: 1440, height: 980 } });
context.setDefaultTimeout(20000);
const page = await context.newPage(), errors = [];
page.on('pageerror', error => errors.push(String(error)));
page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
async function open(name = '') {
  await page.goto(base + name);
  await page.waitForFunction(() => globalThis.valthorneReady === true);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'Horizontal overflow');
  assert.equal(await page.evaluate(() => globalThis.valthorneError), undefined, 'Engine failure');
}
async function settle() { await page.waitForTimeout(200); }
const report = { browser: selected, pages: [], checks: [] };
try {
  for (const name of ['index','engine','examples','docs','start','lab','about']) {
    console.log('Verifying ' + name);
    await open(name + '.html');
    await settle();
    const ink = await page.evaluate(() => {
      const canvas = valthorneHost.nano.get(1).canvas;
      // Inspect the engine's vector surface before WebGL composition; WebGL's
      // non-preserved drawing buffer may already be cleared after presentation.
      const copy = document.createElement('canvas'); copy.width = canvas.width; copy.height = canvas.height;
      const ctx = copy.getContext('2d'); ctx.drawImage(canvas, 0, 0);
      const data = ctx.getImageData(0, 0, copy.width, copy.height).data;
      let nonBackground = 0;
      for (let i = 0; i < data.length; i += 64) if (data[i] > 80 || data[i + 1] > 80 || data[i + 2] > 80) nonBackground++;
      return nonBackground;
    });
    assert.ok(ink > 100, 'Canvas has no visible content on ' + name);
    report.pages.push({ name, coloredSamples: ink });
    if (name === 'index') await page.screenshot({ path: path.join(root,'build/home-desktop.png') });
    if (name === 'lab') await page.screenshot({ path: path.join(root,'build/lab-desktop.png') });
    await page.evaluate(() => scrollTo(0, document.body.scrollHeight)); await settle();
    await page.setViewportSize({ width: 390, height: 844 }); await settle();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'Mobile overflow on ' + name);
    await page.evaluate(() => scrollTo(0, 0)); await settle();
    await page.screenshot({ path: path.join(root,`build/${name}-mobile.png`) });
    await page.setViewportSize({ width: 1440, height: 980 });
  }
  report.checks.push('Seven pages render visible engine pixels at desktop and mobile sizes');

  await open('index.html'); await page.waitForTimeout(800);
  const idle = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(600);
  assert.equal(await page.evaluate(() => websiteMetrics.frames), idle, 'Site keeps rendering while idle');
  await page.locator('#engine-links').getByRole('link',{name:'Docs',exact:true}).click();
  await page.waitForURL('**/docs.html'); await page.waitForFunction(() => globalThis.valthorneReady);
  await page.evaluate(() => scrollTo(0, 520)); await settle();
  const search = page.locator('#engine-search');
  // Scroll until the collection search enters the viewport without relying on a particular text height.
  for (let i=0; i<10 && !(await search.isVisible()); i++) { await page.mouse.wheel(0,250); await settle(); }
  await search.fill('physics'); await settle();
  assert.equal(await page.evaluate(() => site.query), 'physics');
  assert.ok(page.url().includes('q=physics'));
  await search.fill('no-such-system-xyz'); await settle();
  assert.equal(await page.locator('#engine-links a[href*="/docs/systems/"]:visible').count(), 0);
  await search.fill(''); await settle();
  await page.locator('#engine-links a[href="#filter=ui"]').click(); await settle();
  assert.equal(await page.evaluate(() => site.category), 'ui');
  assert.ok((await page.locator('#engine-links a[href*="/docs/systems/ui-"]:visible').count()) > 0);
  report.checks.push('Documentation search, URL persistence, empty results, and category filtering');

  await open('examples.html?q=fps');
  await page.evaluate(() => scrollTo(0, 350)); await settle();
  const download = page.locator('#engine-links a[href*="Valthorne-demo-fps-"]:visible');
  assert.equal(await download.count(), 1);
  assert.match(await download.getAttribute('href'), /releases\/download\/v2\.0\.1\/.+\.zip$/);
  report.checks.push('FPS search exposes the direct release ZIP');

  await open('lab.html'); await page.evaluate(() => scrollTo(0, 300)); await settle();
  const animate = page.locator('#engine-links a[href="#animate"]');
  await animate.click(); await settle(); const moving = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(300); assert.ok(await page.evaluate(() => websiteMetrics.frames) > moving + 2);
  await animate.click(); await settle(); const paused = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(300); assert.equal(await page.evaluate(() => websiteMetrics.frames), paused);
  report.checks.push('Lab animation starts and pauses; ordinary pages render on demand');

  await open('start.html'); await page.evaluate(() => scrollTo(0, 250)); await settle();
  await context.grantPermissions(['clipboard-read','clipboard-write']);
  await page.locator('#engine-links a[href="#copy=0"]').click();
  assert.match(await page.evaluate(() => navigator.clipboard.readText()), /io.github.tehnewb:Valthorne:2.0.0/);
  report.checks.push('Copy code copies the original, unwrapped source');

  await page.goto(base + 'docs.html?view=text');
  assert.equal(await page.locator('#content article').count(), 50);
  assert.equal(await page.evaluate(() => globalThis.valthorneReady), undefined);
  assert.equal(await page.locator('#view-toggle').textContent(), 'Engine version');
  const noJS = await browser.newContext({ javaScriptEnabled: false });
  const textPage = await noJS.newPage(); await textPage.goto(base + 'examples.html');
  assert.equal(await textPage.locator('#content a[href*="Valthorne-demo-"]').count(), 10);
  await noJS.close();
  report.checks.push('Text mode avoids engine startup; no-JavaScript view retains all ten downloads');
  assert.deepEqual(errors, [], 'Browser errors or missing resources');
  await fs.writeFile(path.join(root,'build/verification.json'), JSON.stringify(report,null,2) + '\n');
  console.log(JSON.stringify(report,null,2));
} catch (error) {
  console.error('Browser findings:', errors);
  console.error('Last page:', page.url(), await page.evaluate(() => globalThis.valthorneError).catch(() => 'unavailable'));
  await page.screenshot({ path: path.join(root,'build/failure.png') }).catch(() => {});
  throw error;
} finally { await browser.close(); }
