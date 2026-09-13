/** Hash-navigation checks for the single Java landing page; artifacts stay in build/. */
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
const report = { browser: selected, checks: [], sections: [] };
const labels = { engine: 'Engine', resources: 'Resources', start: 'Get started' };

/** Identify the section action by destination as well as label; hero CTAs can share its wording. */
function headerLink(page, id) {
  return page.locator('#engine-links').getByRole('link', { name: labels[id], exact: true })
    .and(page.locator(`#engine-links a[href="#${id}"]`));
}

async function scenario(options = {}, suffix = '') {
  const context = await browser.newContext({ viewport: { width: 1440, height: 980 }, ...options });
  context.setDefaultTimeout(20000);
  const page = await context.newPage();
  const activity = { documents: [], runtimes: [], errors: [] };
  page.on('request', request => {
    if (request.resourceType() === 'document') activity.documents.push(request.url());
    if (new URL(request.url()).pathname.endsWith('/runtime/valthorne.js')) activity.runtimes.push(request.url());
  });
  page.on('pageerror', error => activity.errors.push(String(error)));
  page.on('console', message => { if (message.type() === 'error') activity.errors.push(message.text()); });
  page.on('response', response => { if (response.status() >= 400) activity.errors.push(`${response.status()} ${response.url()}`); });
  await page.goto(new URL('index.html' + suffix, base).href);
  await page.waitForFunction(() => globalThis.valthorneReady === true);
  await page.waitForFunction(() => !websiteMetrics.revealing);
  await page.evaluate(() => {
    globalThis.navigationAudit = { host: valthorneHost, canvas: valthorneHost.graphics.canvas, readyRemoved: 0 };
    new MutationObserver(() => {
      if (!document.documentElement.classList.contains('engine-ready')) navigationAudit.readyRemoved++;
    }).observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
  });
  return { context, page, activity };
}

async function settled(page, id) {
  await page.waitForFunction(id => websiteMetrics.activeSection === id && !websiteMetrics.revealing, id);
  // Smooth scrolling must have ended as well as any finite entrance movement.
  let last = -1;
  for (let i = 0; i < 25; i++) {
    const next = await page.evaluate(() => scrollY);
    if (next === last) break;
    last = next;
    await page.waitForTimeout(100);
  }
  const state = await page.evaluate(() => ({
    hash: location.hash, section: websiteMetrics.activeSection, scroll: scrollY,
    liveHost: navigationAudit.host === valthorneHost, liveCanvas: navigationAudit.canvas === valthorneHost.graphics.canvas,
    ready: document.documentElement.classList.contains('engine-ready'), readyRemoved: navigationAudit.readyRemoved,
    error: globalThis.valthorneError, overflow: document.documentElement.scrollWidth > innerWidth
  }));
  assert.equal(state.section, id);
  if (id !== 'top') assert.equal(state.hash, '#' + id, 'The address does not match the selected section');
  assert.equal(state.liveHost, true, 'An anchor recreated the engine host');
  assert.equal(state.liveCanvas, true, 'An anchor recreated the drawing surface');
  assert.equal(state.ready, true);
  assert.equal(state.readyRemoved, 0, 'An anchor exposed the text layout while navigating');
  assert.equal(state.error, undefined);
  assert.equal(state.overflow, false);
  return state;
}

async function click(page, id) {
  const link = headerLink(page, id);
  assert.equal(await link.getAttribute('href'), '#' + id, 'Header navigation is not a real fragment link');
  await link.click();
  return settled(page, id);
}
function noReload(activity) {
  assert.equal(activity.documents.length, 1, 'An anchor loaded a second document: ' + activity.documents.join(', '));
  assert.equal(activity.runtimes.length, 1, 'An anchor loaded another compiled Java runtime');
  assert.deepEqual(activity.errors, [], 'Browser errors during anchor navigation');
}

try {
  const main = await scenario();
  for (const viewport of [
    { name: 'desktop', width: 1440, height: 980 },
    { name: 'mobile', width: 390, height: 844 },
    { name: 'short', width: 1024, height: 600 }
  ]) {
    await main.page.setViewportSize({ width: viewport.width, height: viewport.height });
    for (const id of ['engine', 'resources', 'start']) {
      const state = await click(main.page, id);
      assert.ok(state.scroll > 0, 'The section link did not scroll the landing page');
      report.sections.push({ viewport: viewport.name, id, scroll: state.scroll });
      // The pressed native link stays reachable; the next Tab must not enter the hidden companion.
      await main.page.keyboard.press('Tab');
      assert.equal(await main.page.evaluate(() => document.querySelector('#content').contains(document.activeElement)), false,
        'Keyboard focus entered the hidden semantic companion');
    }
    await main.page.screenshot({ path: path.join(output, `navigation-${viewport.name}.png`) });
  }
  noReload(main.activity);
  report.checks.push('All three section links work at desktop, mobile, and short sizes without restarting the page or engine');

  await main.page.setViewportSize({ width: 1440, height: 980 });
  await click(main.page, 'engine');
  const resources = await click(main.page, 'resources');
  const start = await click(main.page, 'start');
  await main.page.goBack();
  const back = await settled(main.page, 'resources');
  assert.ok(Math.abs(back.scroll - resources.scroll) <= 3, 'Back restored the wrong section position');
  await main.page.goForward();
  const forward = await settled(main.page, 'start');
  assert.ok(Math.abs(forward.scroll - start.scroll) <= 3, 'Forward restored the wrong section position');
  noReload(main.activity);
  report.checks.push('Back and Forward restore section fragments and their scroll positions');

  // Start overlapping scroll requests with genuine clicks on the fixed header.
  for (const id of ['engine', 'start', 'resources']) {
    const bounds = await headerLink(main.page, id).boundingBox();
    await main.page.mouse.click(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  }
  await settled(main.page, 'resources');
  await main.page.waitForTimeout(700);
  await settled(main.page, 'resources');
  const top = main.page.locator('#back-top');
  await top.click();
  await main.page.waitForFunction(() => scrollY <= 2 && websiteMetrics.activeSection === 'top');
  noReload(main.activity);
  report.checks.push('Rapid anchor clicks settle on the last destination, and Back to top returns to the hero');
  await main.context.close();

  const reduced = await scenario({ reducedMotion: 'reduce' });
  assert.equal(await reduced.page.evaluate(() => websiteMetrics.motionEnabled), false);
  await click(reduced.page, 'start');
  await click(reduced.page, 'engine');
  noReload(reduced.activity);
  report.checks.push('Reduced-motion visitors retain all anchor navigation');
  await reduced.context.close();

  const direct = await scenario({}, '#resources');
  await settled(direct.page, 'resources');
  await direct.page.reload();
  await direct.page.waitForFunction(() => globalThis.valthorneReady === true && websiteMetrics.activeSection === 'resources' && !websiteMetrics.revealing);
  assert.equal(new URL(direct.page.url()).hash, '#resources');
  assert.ok(await direct.page.evaluate(() => scrollY > 0), 'Reload discarded the section fragment');
  assert.deepEqual(direct.activity.errors, []);
  report.checks.push('Direct fragment links and reload open the requested section');
  await direct.context.close();

  await fs.writeFile(path.join(output, 'navigation-verification.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
} finally { await browser.close(); }
