/** Navigation acceptance checks; browser artifacts stay in build/, without a test directory. */
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
const report = { browser: selected, checks: [], routes: [] };
const labels = { index: 'Valthorne home', engine: 'Engine', examples: 'Demos', docs: 'Docs', start: 'Get started', lab: 'Lab', about: 'About' };

/** Each scenario owns its errors so deliberate delays cannot conceal another page's failures. */
async function scenario(options = {}, setup, initialPage = 'index.html') {
  const context = await browser.newContext({ viewport: { width: 1440, height: 980 }, ...options });
  context.setDefaultTimeout(20000);
  if (setup) await setup(context);
  const page = await context.newPage();
  const activity = { documents: [], runtimes: [], errors: [] };
  page.on('request', request => {
    if (request.resourceType() === 'document') activity.documents.push(request.url());
    if (new URL(request.url()).pathname.endsWith('/runtime/valthorne.js')) activity.runtimes.push(request.url());
  });
  page.on('pageerror', error => activity.errors.push(String(error)));
  page.on('response', response => { if (response.status() >= 400) activity.errors.push(`${response.status()} ${response.url()}`); });
  page.on('console', message => { if (message.type() === 'error') activity.errors.push(message.text()); });
  await page.goto(new URL(initialPage, base).href);
  await page.waitForFunction(() => globalThis.valthorneReady === true);
  await page.waitForFunction(() => !websiteMetrics.revealing);
  await page.evaluate(() => {
    globalThis.navigationAudit = {
      host: valthorneHost, canvas: valthorneHost.graphics.canvas, marker: {}, readyRemoved: 0,
      snapshots: 0, invalidSnapshots: 0, animationCalls: 0
    };
    new MutationObserver(records => {
      for (const record of records) {
        if (record.type === 'attributes' && record.attributeName === 'class' && record.target === document.documentElement && (!document.documentElement.classList.contains('engine-ready') || !String(record.oldValue).split(/\s+/).includes('engine-ready'))) navigationAudit.readyRemoved++;
        for (const node of record.addedNodes || []) {
          if (node.nodeType === 1 && node.matches('.page-transition')) {
            navigationAudit.snapshots++;
            if (node.getAttribute('aria-hidden') !== 'true' || getComputedStyle(node).pointerEvents !== 'none') navigationAudit.invalidSnapshots++;
          }
        }
      }
    }).observe(document.documentElement, { attributes: true, attributeOldValue: true, attributeFilter: ['class'], childList: true, subtree: true });
  });
  return { context, page, activity };
}

async function settled(page, id) {
  await page.waitForFunction(id => globalThis.site?.page.id === id && globalThis.websiteMetrics?.navigating === false, id);
  await page.waitForFunction(() => !websiteMetrics.revealing && !document.querySelector('.page-transition'));
  const state = await page.evaluate(() => ({
    id: site.page.id, payload: JSON.parse(document.querySelector('#page-content').textContent).id,
    title: document.title,
    expectedTitle: site.page.id === 'index' ? 'Valthorne — Java game engine' : `${site.page.title} — Valthorne`,
    liveHost: navigationAudit.host === valthorneHost, liveCanvas: navigationAudit.canvas === valthorneHost.graphics.canvas,
    readyRemoved: navigationAudit.readyRemoved, invalidSnapshots: navigationAudit.invalidSnapshots,
    ready: document.documentElement.classList.contains('engine-ready'), error: globalThis.valthorneError,
    overflow: document.documentElement.scrollWidth > innerWidth,
    navigations: websiteMetrics.navigations,
    textURL: document.querySelector('#view-toggle').href
  }));
  assert.equal(state.id, id);
  assert.equal(state.payload, id, 'Semantic page content did not follow navigation');
  assert.equal(state.title, state.expectedTitle, 'Document title did not follow navigation');
  assert.equal(state.liveHost, true, 'Navigation recreated the engine host');
  assert.equal(state.liveCanvas, true, 'Navigation recreated the drawing surface');
  assert.equal(state.ready, true, 'Navigation exposed the fallback document');
  assert.equal(state.readyRemoved, 0, 'Navigation temporarily removed engine-ready');
  assert.equal(state.invalidSnapshots, 0, 'A transition snapshot intercepts input or appears in accessibility content');
  assert.equal(state.error, undefined, 'Engine failed during navigation');
  assert.equal(state.overflow, false, 'Navigation introduced horizontal overflow');
  assert.equal(new URL(state.textURL).pathname, new URL(page.url()).pathname, 'Text-version link points to the previous page');
  assert.equal(new URL(state.textURL).searchParams.get('view'), 'text');
  return state;
}

async function click(page, id) {
  const mobile = page.viewportSize().width < 900;
  const label = id === 'start' && mobile ? 'Start' : labels[id];
  await page.locator('#engine-links').getByRole('link', { name: label, exact: true }).click();
  await settled(page, id);
  const scrollBeforeTab = await page.evaluate(() => scrollY);
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), 'Valthorne home', 'Keyboard focus did not return to the first visible navigation link');
  assert.equal(await page.evaluate(() => scrollY), scrollBeforeTab, 'Keyboard navigation jumped to a stale off-screen link');
}

function noReload(activity) {
  assert.equal(activity.documents.length, 1, 'Internal navigation loaded another document: ' + activity.documents.join(', '));
  assert.equal(activity.runtimes.length, 1, 'Internal navigation loaded another Java runtime');
  assert.deepEqual(activity.errors, [], 'Browser errors during navigation');
}

try {
  const main = await scenario();
  const { page } = main;
  for (const viewport of [{ name: 'desktop', width: 1440, height: 980 }, { name: 'mobile', width: 390, height: 844 }]) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    for (const id of ['engine', 'examples', 'docs', 'start', 'lab', 'about', 'index']) {
      await click(page, id);
      report.routes.push({ size: viewport.name, id, url: page.url() });
    }
    await page.screenshot({ path: path.join(output, `navigation-${viewport.name}.png`) });
  }
  noReload(main.activity);
  report.checks.push('Fourteen desktop/mobile header clicks preserve the document, engine, canvas, semantic content and keyboard navigation without scroll jumps');

  await page.setViewportSize({ width: 1440, height: 980 });
  await click(page, 'docs');
  await page.evaluate(() => scrollTo(0, Math.min(2800, document.body.scrollHeight - innerHeight - 100)));
  await page.waitForTimeout(150);
  const longScroll = await page.evaluate(() => scrollY);
  assert.ok(longScroll > 2000, 'The long-document history scenario did not reach the directory');
  await click(page, 'about');
  assert.equal(await page.evaluate(() => scrollY), 0, 'A new page inherited the previous scroll position');
  await page.goBack(); await settled(page, 'docs');
  await page.waitForFunction(y => Math.abs(scrollY - y) <= 2, longScroll);
  await page.goForward(); await settled(page, 'about');
  assert.equal(await page.evaluate(() => scrollY), 0, 'Forward navigation restored the wrong scroll position');
  await page.goBack(); await settled(page, 'docs');
  await page.evaluate(() => scrollTo(0, 0));
  await page.waitForTimeout(120);
  await page.locator('#engine-search').fill('ui');
  await page.locator('#engine-links a[href="#filter=ui"]').click();
  await page.waitForTimeout(150);
  assert.equal(await page.evaluate(() => site.category), 'ui');
  await page.evaluate(() => scrollTo(0, Math.min(400, document.body.scrollHeight - innerHeight)));
  await page.waitForTimeout(120);
  const filteredScroll = await page.evaluate(() => scrollY);
  await click(page, 'about');
  assert.equal(await page.evaluate(() => site.query), '', 'Search state leaked into the next page');
  await page.goBack(); await settled(page, 'docs');
  assert.equal(await page.evaluate(() => site.query), 'ui', 'Back navigation lost the search query');
  assert.equal(await page.evaluate(() => site.category), 'ui', 'Back navigation lost the category filter');
  assert.equal(await page.locator('#engine-search').inputValue(), 'ui');
  assert.equal(new URL(page.url()).searchParams.get('q'), 'ui');
  await page.waitForFunction(y => Math.abs(scrollY - y) <= 2, filteredScroll);
  assert.ok((await page.evaluate(() => websiteMetrics.navigations)) >= 20, 'Navigation metrics did not record route changes');
  noReload(main.activity);
  report.checks.push('Back and Forward restore long-page scroll positions, queries and filters without stale state on new pages');
  await main.context.close();

  const rapid = await scenario({}, async context => {
    // Slow destinations to create overlapping user requests even with a fast local server.
    await context.route(/\/(?:engine|docs)\.html(?:\?.*)?$/, async route => {
      await new Promise(resolve => setTimeout(resolve, 300));
      await route.continue();
    });
  });
  const engine = await rapid.page.locator('#engine-links').getByRole('link', { name: 'Engine', exact: true }).boundingBox();
  const docs = await rapid.page.locator('#engine-links').getByRole('link', { name: 'Docs', exact: true }).boundingBox();
  const about = await rapid.page.locator('#engine-links').getByRole('link', { name: 'About', exact: true }).boundingBox();
  for (const target of [engine, docs, about]) await rapid.page.mouse.click(target.x + target.width / 2, target.y + target.height / 2);
  await settled(rapid.page, 'about');
  await rapid.page.waitForTimeout(600);
  await settled(rapid.page, 'about');
  assert.match(new URL(rapid.page.url()).pathname, /\/about\.html$/);
  noReload(rapid.activity);
  report.checks.push('Rapid header clicks settle on the last requested page, with no stale response overwrite');
  await rapid.context.close();

  const rootURL = new URL('./', base).href;
  const interruptedBack = await scenario({}, async context => {
    // The initial root document loads normally; only the cold Back fetch waits.
    await context.route(rootURL, async route => {
      if (!route.request().isNavigationRequest()) await new Promise(resolve => setTimeout(resolve, 500));
      await route.continue();
    });
  }, './');
  await interruptedBack.page.evaluate(() => scrollTo(0, 1500));
  await interruptedBack.page.waitForTimeout(120);
  assert.equal(await interruptedBack.page.evaluate(() => scrollY), 1500);
  await click(interruptedBack.page, 'engine');
  await interruptedBack.page.evaluate(() => scrollTo(0, 400));
  await interruptedBack.page.waitForTimeout(120);
  const pendingHome = interruptedBack.page.waitForRequest(request => request.url() === rootURL && !request.isNavigationRequest());
  await interruptedBack.page.goBack();
  await pendingHome;
  assert.equal(await interruptedBack.page.evaluate(() => site.page.id === 'engine' && websiteMetrics.navigating), true, 'The history fixture did not interrupt a pending Back navigation');
  await click(interruptedBack.page, 'docs');
  await interruptedBack.page.waitForTimeout(600);
  await settled(interruptedBack.page, 'docs');
  await interruptedBack.page.goBack();
  await settled(interruptedBack.page, 'index');
  assert.equal(interruptedBack.page.url(), rootURL, 'Back returned to an unexpected history entry');
  assert.equal(await interruptedBack.page.evaluate(() => scrollY), 1500, 'Interrupting Back replaced the original home scroll position with the outgoing page position');
  noReload(interruptedBack.activity);
  report.checks.push('A new route clicked during a delayed Back navigation preserves the original destination history and scroll position');
  await interruptedBack.context.close();

  const reduced = await scenario({ reducedMotion: 'reduce' });
  await click(reduced.page, 'engine');
  await click(reduced.page, 'docs');
  assert.equal(await reduced.page.evaluate(() => navigationAudit.snapshots), 0, 'Reduced motion still displayed animated page snapshots');
  noReload(reduced.activity);
  report.checks.push('Reduced-motion navigation swaps content without an animated snapshot');
  await reduced.context.close();

  const compatible = await scenario({}, context => context.addInitScript(() => {
    Object.defineProperty(document, 'startViewTransition', { value: undefined, configurable: true });
    Object.defineProperty(Element.prototype, 'animate', { value: undefined, configurable: true });
  }));
  await click(compatible.page, 'engine');
  await click(compatible.page, 'about');
  noReload(compatible.activity);
  report.checks.push('Navigation remains functional without View Transition or Web Animations APIs');
  await compatible.context.close();

  await fs.writeFile(path.join(output, 'navigation-verification.json'), JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
} finally {
  await browser.close();
}
