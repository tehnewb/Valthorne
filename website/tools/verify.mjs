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
async function settle() {
  await page.waitForTimeout(120);
  await page.waitForFunction(() => !globalThis.websiteMetrics?.revealing);
}
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
  const identity = await page.evaluate(() => ({
    banner: document.querySelector('.brand-banner').getAttribute('src'),
    logo: document.querySelector('.brand img').getAttribute('src'),
    unwantedScreenshot: [...document.images].some(image => image.src.endsWith('/fps.png'))
  }));
  assert.equal(identity.banner, 'assets/banner.png');
  assert.equal(identity.logo, 'assets/valthorne.png');
  assert.equal(identity.unwantedScreenshot, false);
  const primary = await page.locator('#engine-links a[aria-label="Start building"]:visible').first().boundingBox();
  const secondary = await page.locator('#engine-links a[aria-label="Explore demos"]:visible').boundingBox();
  assert.ok(Math.abs((primary.x + secondary.x + secondary.width) / 2 - 720) < 2, 'Hero actions are not centered');
  report.checks.push('Original logo and banner, centered hero actions, and removal of the incorrect FPS screenshot');
  const idle = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(600);
  assert.equal(await page.evaluate(() => websiteMetrics.frames), idle, 'Site keeps rendering while idle');
  await page.keyboard.press('Tab');
  assert.match(await page.evaluate(() => document.activeElement.textContent), /Skip to text/);
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), 'Valthorne home');
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), 'Engine');
  report.checks.push('Keyboard navigation skips the decorative drawing surface');
  await page.locator('#engine-links').getByRole('link',{name:'Docs',exact:true}).click();
  await page.waitForURL('**/docs.html'); await page.waitForFunction(() => globalThis.valthorneReady);
  await settle();
  const search = page.locator('#engine-search');
  // Scroll until the collection search enters the viewport without relying on a particular text height.
  for (let i=0; i<10 && !(await search.isVisible()); i++) { await page.mouse.wheel(0,250); await settle(); }
  await search.fill('physics'); await settle();
  assert.equal(await page.evaluate(() => site.query), 'physics');
  assert.ok(page.url().includes('q=physics'));
  await search.fill('no-such-system-xyz'); await settle();
  assert.equal(await page.locator('#engine-links a[href*="/docs/systems/"]:visible').count(), 0);
  await page.locator('#engine-links a[href="#clear"]').click(); await settle();
  assert.equal(await search.inputValue(), '');
  assert.equal(await page.evaluate(() => site.category), 'all');
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
  assert.equal(await page.evaluate(() => websiteMetrics.animations), true);
  const moving = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(300); assert.ok(await page.evaluate(() => websiteMetrics.frames) > moving + 2);
  await animate.click(); await settle(); const paused = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(300); assert.equal(await page.evaluate(() => websiteMetrics.frames), paused);
  const scene = page.locator('#scene-interaction');
  const sceneBox = await scene.boundingBox();
  await page.mouse.move(sceneBox.x + sceneBox.width / 2, sceneBox.y + sceneBox.height / 2);
  await page.mouse.down(); await page.mouse.move(sceneBox.x + sceneBox.width / 2 + 90, sceneBox.y + sceneBox.height / 2 + 20, { steps: 5 }); await page.mouse.up();
  assert.ok(await page.evaluate(() => site.orbit) > .5, 'Drag did not rotate the scene');
  await scene.focus(); await page.keyboard.press('ArrowRight');
  assert.ok(await page.evaluate(() => site.orbit) > .9, 'Keyboard did not rotate the scene');
  await page.keyboard.press('Home');
  assert.deepEqual(await page.evaluate(() => [site.orbit, site.tilt]), [0, 0]);
  await animate.click(); await settle();
  await page.evaluate(() => scrollTo(0, document.body.scrollHeight)); await settle();
  assert.equal(await page.evaluate(() => websiteMetrics.sceneVisible), false);
  const offscreen = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(400); assert.equal(await page.evaluate(() => websiteMetrics.frames), offscreen, 'Offscreen scene keeps rendering');
  await page.locator('#back-top').click(); await page.waitForFunction(() => scrollY === 0); await settle();
  assert.equal(await page.evaluate(() => websiteMetrics.animations), true);
  await page.locator('#motion-toggle').click(); await settle();
  assert.equal(await page.evaluate(() => websiteMetrics.motionEnabled), false);
  const motionPaused = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(350); assert.equal(await page.evaluate(() => websiteMetrics.frames), motionPaused);
  await open('engine.html'); await settle();
  assert.equal(await page.evaluate(() => websiteMetrics.motionEnabled), false, 'Motion choice did not persist across navigation');
  await page.locator('#motion-toggle').click(); await settle();
  report.checks.push('3D pointer and keyboard orbit, reset, playback controls, offscreen suspension, back to top, and persistent motion preference');

  await page.emulateMedia({ reducedMotion: 'reduce' });
  await open('lab.html'); await settle();
  assert.equal(await page.evaluate(() => websiteMetrics.animations), false);
  assert.equal(await page.locator('#motion-toggle').isDisabled(), true);
  const reduced = await page.evaluate(() => websiteMetrics.frames);
  await page.waitForTimeout(400); assert.equal(await page.evaluate(() => websiteMetrics.frames), reduced);
  await page.locator('#scene-interaction').focus(); await page.keyboard.press('ArrowLeft'); await settle();
  assert.ok(await page.evaluate(() => site.orbit) < 0, 'Reduced motion should retain manual exploration');
  await page.emulateMedia({ reducedMotion: 'no-preference' }); await settle();
  report.checks.push('System reduced motion disables decorative movement while retaining manual scene controls');

  await open('start.html'); await page.evaluate(() => scrollTo(0, 250)); await settle();
  await context.grantPermissions(['clipboard-read','clipboard-write']);
  await page.locator('#engine-links a[href="#copy=0"]').click();
  assert.match(await page.evaluate(() => navigator.clipboard.readText()), /io.github.tehnewb:Valthorne:2.0.0/);
  await page.waitForFunction(() => site.copied === 0); await settle();
  assert.equal(await page.locator('#engine-links a[href="#copy=0"]').getAttribute('aria-label'), 'Copied!');
  const widths = await page.evaluate(() => {
    const c = valthorneHost.nano.get(1), face = c.state.face; c.state.face = 'monospace';
    const result = [site.measure(1, 'iiiiiiii', 13), site.measure(1, 'mmmmmmmm', 13)]; c.state.face = face; return result;
  });
  assert.equal(widths[0], widths[1], 'Code font is not actually monospace');
  report.checks.push('Readable monospace code, original source copying, and visible copy confirmation');

  await page.goto(base + 'docs.html?view=text');
  assert.equal(await page.locator('#content article').count(), 50);
  assert.equal(await page.evaluate(() => globalThis.valthorneReady), undefined);
  assert.equal(await page.locator('#view-toggle').textContent(), 'Engine version');
  const noJS = await browser.newContext({ javaScriptEnabled: false });
  const textPage = await noJS.newPage(); await textPage.goto(base + 'examples.html');
  assert.equal(await textPage.locator('#content a[href*="Valthorne-demo-"]').count(), 10);
  await noJS.close();
  report.checks.push('Text mode avoids engine startup; no-JavaScript view retains all ten downloads');
  const fallbackPage = await context.newPage();
  await fallbackPage.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, ...args) { return type === 'webgl2' ? null : original.call(this,type,...args); };
  });
  await fallbackPage.goto(base + 'index.html');
  await fallbackPage.waitForFunction(() => globalThis.valthorneError);
  assert.equal(await fallbackPage.locator('#content h1').isVisible(), true);
  assert.equal(await fallbackPage.evaluate(() => document.documentElement.classList.contains('engine-ready')), false);
  await fallbackPage.close();
  report.checks.push('Graphics-unavailable browsers retain the readable semantic site');
  assert.deepEqual(errors, [], 'Browser errors or missing resources');
  await fs.writeFile(path.join(root,'build/verification.json'), JSON.stringify(report,null,2) + '\n');
  console.log(JSON.stringify(report,null,2));
} catch (error) {
  console.error('Browser findings:', errors);
  console.error('Last page:', page.url(), await page.evaluate(() => globalThis.valthorneError).catch(() => 'unavailable'));
  await page.screenshot({ path: path.join(root,'build/failure.png') }).catch(() => {});
  throw error;
} finally { await browser.close(); }
