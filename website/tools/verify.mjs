/** Single-page browser acceptance checks. Reports and screenshots stay in ignored build/. */
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
const output = path.join(root, 'build');
await fs.mkdir(output, { recursive: true });
const base = process.env.SITE_URL || 'http://127.0.0.1:8097/Valthorne/';
const selected = process.env.TEST_BROWSER || 'chromium';
const browser = await (playwright[selected] || playwright.chromium).launch({
  headless: true, ...(selected === 'chrome' || selected === 'msedge' ? { channel: selected } : {})
});
const context = await browser.newContext({ viewport: { width: 1440, height: 980 } });
context.setDefaultTimeout(20000);
const page = await context.newPage();
const errors = [];
page.on('pageerror', error => errors.push(String(error)));
page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
const report = { browser: selected, viewports: [], checks: [] };

async function settle(target = page) {
  await target.waitForTimeout(150);
  await target.waitForFunction(() => globalThis.websiteMetrics && !websiteMetrics.revealing);
}
async function open() {
  await page.goto(new URL('index.html', base).href);
  await page.waitForFunction(() => globalThis.valthorneReady === true);
  await page.waitForLoadState('networkidle');
  await settle();
  assert.equal(await page.evaluate(() => globalThis.valthorneError), undefined, 'The Java landing page failed');
}
async function assertIdle(target = page) {
  await settle(target);
  await target.waitForTimeout(200);
  const before = await target.evaluate(() => websiteMetrics.frames);
  await target.waitForTimeout(450);
  assert.equal(await target.evaluate(() => websiteMetrics.frames), before, 'An idle landing page keeps rendering');
}
async function semanticContent(target) {
  assert.equal(await target.locator('#content h1').count(), 1, 'The landing page needs one primary heading');
  for (const id of ['engine', 'resources', 'start']) {
    assert.equal(await target.locator(`#content #${id}`).count(), 1, `Missing semantic section: ${id}`);
    assert.ok((await target.locator(`#content #${id}`).textContent()).trim().length > 40, `Empty semantic section: ${id}`);
  }
  const content = await target.locator('#content').innerText();
  assert.doesNotMatch(content, /\b(?:examples?|demos?)\b/i, 'Retired example content remains on the landing page');
  const hrefs = await target.locator('a[href]').evaluateAll(anchors => anchors.map(anchor => anchor.getAttribute('href')));
  for (const href of hrefs) {
    assert.doesNotMatch(href, /(?:examples?|demos?)(?:[/.?#_-]|$)|\.(?:zip|jar|exe|msi|dmg)(?:[?#]|$)/i,
      'The landing page still links to an example or packaged application: ' + href);
  }
  assert.ok(hrefs.some(href => /github\.com\/tehnewb\/Valthorne\/blob\/main\/docs\//.test(href)), 'No direct documentation link');
  assert.ok(hrefs.some(href => href.includes('#start-a-game')), 'No direct quick-start link');
}

try {
  await open();
  await semanticContent(page);
  assert.equal(await page.locator('#engine-search,#scene-interaction,a[href^="#copy="],a[href^="#filter="]').count(), 0,
    'Retired catalog, copy, or lab controls remain');
  const branding = await page.evaluate(() => ({
    logo: document.querySelector('.brand img')?.getAttribute('src'),
    artwork: document.querySelector('.hero-media img')?.getAttribute('src'),
    artworkLoaded: document.querySelector('.hero-media img')?.naturalWidth > 0,
    fontLoaded: document.fonts.check('400 20px UrbanistWebsite') && document.fonts.check('700 20px UrbanistWebsite')
  }));
  assert.equal(branding.logo, 'assets/valthorne.png', 'The original logo is missing');
  assert.equal(branding.artwork, 'assets/world.svg', 'The Java-generated hero artwork is missing');
  assert.equal(branding.artworkLoaded, true, 'The hero artwork did not decode');
  assert.equal(branding.fontLoaded, true, 'The self-hosted Urbanist font did not load');
  report.checks.push('One complete landing page, original branding, Java-generated artwork, local typography, and direct documentation links');

  for (const viewport of [
    { name: 'desktop', width: 1440, height: 980 },
    { name: 'mobile', width: 390, height: 844 },
    { name: 'short', width: 1024, height: 600 }
  ]) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.evaluate(() => scrollTo(0, 0));
    await settle();
    const pixels = await page.evaluate(() => {
      // Read the persistent vector surface; WebGL may clear its displayed buffer after presentation.
      const surface = valthorneHost.nano.get(1);
      const data = surface.ctx.getImageData(0, 0, surface.canvas.width, surface.canvas.height).data;
      let colored = 0, dark = 0, light = 0;
      for (let i = 0; i < data.length; i += 64) {
        const low = Math.min(data[i], data[i + 1], data[i + 2]), high = Math.max(data[i], data[i + 1], data[i + 2]);
        if (high - low > 20) colored++;
        if (high < 70) dark++;
        if (low > 190) light++;
      }
      return { colored, dark, light };
    });
    assert.ok(pixels.colored > 30 && pixels.dark > 50 && pixels.light > 50, 'The engine did not paint the landing composition at ' + viewport.name);
    const stops = await page.evaluate(() => [0, Math.round((document.body.scrollHeight - innerHeight) / 2), document.body.scrollHeight]);
    for (const top of stops) {
      await page.evaluate(top => scrollTo(0, top), top);
      await settle();
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'Horizontal overflow at ' + viewport.name);
      const overflow = await page.locator('#engine-links a:visible').evaluateAll(anchors => anchors
        .map(anchor => ({ label: anchor.getAttribute('aria-label'), rect: anchor.getBoundingClientRect().toJSON() }))
        .filter(({ rect }) => rect.left < -1 || rect.right > innerWidth + 1));
      assert.deepEqual(overflow, [], 'A native action extends outside the viewport');
    }
    await page.screenshot({ path: path.join(output, `landing-${viewport.name}-footer.png`) });
    await page.evaluate(() => scrollTo(0, 0)); await settle();
    await page.screenshot({ path: path.join(output, `landing-${viewport.name}.png`) });
    report.viewports.push({ ...viewport, pixels });
  }
  report.checks.push('Hero, middle, and footer render without overflow at desktop, mobile, and short viewport sizes');

  await page.setViewportSize({ width: 1440, height: 980 });
  await settle();
  await assertIdle();
  // Check actual keyboard behavior without tying the test to a particular skip-link label.
  await page.locator('body').click({ position: { x: 2, y: 2 } });
  const focused = [];
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab');
    focused.push(await page.evaluate(() => ({ tag: document.activeElement.tagName, label: document.activeElement.getAttribute('aria-label') })));
  }
  assert.equal(focused.some(item => item.tag === 'CANVAS'), false, 'The decorative canvas captures keyboard focus');
  assert.ok(focused.some(item => item.label === 'Engine'), 'Keyboard navigation cannot reach the engine section');
  report.checks.push('Finite entrance motion settles completely; native navigation remains keyboard accessible');

  const motion = page.locator('#motion-toggle');
  await motion.scrollIntoViewIfNeeded(); await motion.click();
  await page.waitForFunction(() => websiteMetrics.motionEnabled === false);
  await assertIdle();
  await open();
  assert.equal(await page.evaluate(() => websiteMetrics.motionEnabled), false, 'Motion preference did not survive reload');
  await motion.scrollIntoViewIfNeeded(); await motion.click();
  await page.waitForFunction(() => websiteMetrics.motionEnabled === true);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await settle();
  assert.equal(await page.evaluate(() => websiteMetrics.reducedMotion), true);
  assert.equal(await page.evaluate(() => websiteMetrics.motionEnabled), false);
  assert.equal(await motion.isDisabled(), true, 'System reduced motion should disable the motion toggle');
  await assertIdle();
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  report.checks.push('Motion preference persists, system reduced motion is respected, and settled pages stop drawing');

  const text = await context.newPage();
  const textErrors = [];
  text.on('pageerror', error => textErrors.push(String(error)));
  await text.goto(new URL('index.html?view=text', base).href);
  await text.waitForFunction(() => document.querySelector('#view-toggle')?.textContent === 'Engine version');
  await semanticContent(text);
  assert.equal(await text.locator('#content h1').isVisible(), true);
  assert.equal(await text.evaluate(() => globalThis.valthorneReady), undefined);
  assert.equal(await text.evaluate(() => globalThis.valthorneHost), undefined, 'Text mode started graphics');
  assert.deepEqual(textErrors, []);
  await text.close();
  const noScript = await browser.newContext({ javaScriptEnabled: false, viewport: { width: 390, height: 844 } });
  const readable = await noScript.newPage();
  await readable.goto(new URL('index.html', base).href);
  await semanticContent(readable);
  assert.equal(await readable.locator('#content h1').isVisible(), true);
  assert.equal(await readable.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await noScript.close();
  report.checks.push('The complete landing page remains readable in text mode and with JavaScript disabled');

  const unavailable = await context.newPage();
  await unavailable.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, ...args) { return type === 'webgl2' ? null : original.call(this, type, ...args); };
  });
  await unavailable.goto(new URL('index.html', base).href);
  await unavailable.waitForFunction(() => globalThis.valthorneError);
  assert.equal(await unavailable.locator('#content h1').isVisible(), true);
  assert.equal(await unavailable.evaluate(() => document.documentElement.classList.contains('engine-ready')), false);
  await unavailable.close();
  report.checks.push('Graphics-unavailable browsers retain the semantic landing page');

  // Probe removed content independently so expected 404 responses do not mask actual page errors.
  const removed = [
    'engine.html', 'examples.html', 'docs.html', 'start.html', 'lab.html', 'about.html',
    ...['audio', 'ui', 'scene', 'physics', 'physics-studio', 'path-tracing', 'lighting2d', 'lighting-studio', 'fps'].map(name => `assets/${name}.png`)
  ];
  const responses = await Promise.all(removed.map(async file => ({ file, status: (await context.request.get(new URL(file, base).href)).status() })));
  for (const response of responses) assert.equal(response.status, 404, 'Retired site content is still served: ' + response.file);
  report.checks.push('All six retired pages and former example captures return 404');
  assert.deepEqual(errors, [], 'Browser errors or missing landing resources');
  await fs.writeFile(path.join(output, 'verification.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
} catch (error) {
  console.error('Browser findings:', errors);
  console.error('Last page:', page.url(), await page.evaluate(() => globalThis.valthorneError).catch(() => 'unavailable'));
  await page.screenshot({ path: path.join(output, 'failure.png') }).catch(() => {});
  throw error;
} finally { await browser.close(); }
