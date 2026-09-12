/** Browser integration for the Java website. Valthorne owns visible layout and drawing. */
import { BrowserGraphics } from './runtime/graphics.js';
import { BrowserPlatform } from './runtime/platform.js';
import { BrowserNano } from './runtime/nano-backend.js';
import { BrowserYoga } from './runtime/yoga-backend.js';
import { BrowserFonts } from './runtime/fonts.js';

const page = JSON.parse(document.querySelector('#page-content').textContent);
const params = new URLSearchParams(location.search);
const semantic = document.querySelector('#content');
const links = document.querySelector('#engine-links');
const search = document.querySelector('#engine-search');
const status = document.querySelector('#status');
let frameCallback, shutdownCallback, raf = 0, lastFrame = 0, closed = false, frames = 0;
let ready = false, linkIndex = 0, searchUsed = false;
const startupTimeout = setTimeout(() => { if (!ready) fail(new Error('Website engine startup timed out')); }, 20000);
const images = new Map(), measurements = new Map(), anchorPool = new Map(), occurrences = new Map();
let anchors = [];

/** Coalesces invalidations. There is no background animation loop on ordinary pages. */
function invalidate() {
  if (closed || document.hidden || !frameCallback || raf) return;
  raf = requestAnimationFrame(now => {
    raf = 0;
    try {
      frameCallback(lastFrame ? Math.min((now - lastFrame) / 1000, .05) : 0);
      lastFrame = now; frames++;
      if (site.animating) invalidate();
    } catch (error) { fail(error); }
  });
}

function fail(error) {
  clearTimeout(startupTimeout);
  closed = true; cancelAnimationFrame(raf);
  document.documentElement.classList.remove('engine-ready');
  document.querySelector('#scene').hidden = true;
  links.hidden = true; search.hidden = true;
  semantic.querySelectorAll('a').forEach(anchor => anchor.removeAttribute('tabindex'));
  semantic.querySelectorAll('pre').forEach(pre => pre.tabIndex = 0);
  status.textContent = 'The engine view could not start. The complete text version is available below.';
  globalThis.valthorneError = String(error.stack || error);
  console.error(error);
}

/** Only browser responsibilities cross this bridge; all geometry comes from Java. */
globalThis.site = {
  page, query: params.get('q') || '', category: 'all', animating: false, font: 'sans-serif',
  begin() { linkIndex = 0; searchUsed = false; anchors = []; occurrences.clear(); },
  end(height) {
    for (const anchor of anchorPool.values()) anchor.hidden = !anchors.includes(anchor);
    search.hidden = !searchUsed;
    document.querySelector('#scroll-space').style.height = Math.max(innerHeight, Math.ceil(height)) + 'px';
    // Header links are painted last. Give them first place in keyboard navigation.
    const header = anchors.slice(Math.max(0, linkIndex - 7), linkIndex);
    const ordered = [...header, ...anchors.slice(0, Math.max(0, linkIndex - 7))];
    ordered.forEach((anchor, index) => {
      anchor.style.zIndex = header.includes(anchor) ? '3' : '1';
      if (links.children[index] !== anchor) links.insertBefore(anchor, links.children[index] || null);
    });
  },
  ready() {
    if (ready) return;
    ready = true;
    clearTimeout(startupTimeout);
    document.documentElement.classList.add('engine-ready');
    semantic.querySelectorAll('a,pre').forEach(element => element.tabIndex = -1);
    status.textContent = 'Rendered with Valthorne';
    document.title = `${page.id === 'index' ? 'Valthorne — Java game engine' : page.title + ' — Valthorne'}`;
    globalThis.valthorneReady = true;
    // These metrics allow the verification tool to check actual rendering and idle behavior.
    globalThis.websiteMetrics = { get frames() { return frames; }, get animations() { return site.animating; } };
  },
  measure(vg, text, size) {
    const key = `${size}:${text}`;
    if (measurements.has(key)) return measurements.get(key);
    const context = valthorneHost.nano.get(vg);
    context.ctx.font = `${size}px "${context.fonts.get('default')?.family || 'sans-serif'}"`;
    const width = context.ctx.measureText(text).width;
    if (measurements.size > 15000) measurements.clear();
    measurements.set(key, width); return width;
  },
  link(label, href, x, y, width, height) {
    if (y + height < 0 || y > innerHeight) return;
    const identity = href;
    const occurrence = occurrences.get(identity) || 0;
    occurrences.set(identity, occurrence + 1);
    const key = identity + '|' + occurrence;
    let anchor = anchorPool.get(key);
    if (!anchor) {
      anchor = document.createElement('a'); anchor.className = 'engine-link';
      anchor.addEventListener('keydown', event => {
        if (event.code === 'Space' && anchor.getAttribute('role') === 'button') { event.preventDefault(); anchor.click(); }
      });
      anchor.addEventListener('click', event => {
        const action = anchor.getAttribute('href');
        if (!action.startsWith('#')) return;
        event.preventDefault();
        if (action === '#animate') { site.animating = !site.animating; lastFrame = 0; }
        else if (action.startsWith('#filter=')) site.category = action.slice(8);
        else if (action.startsWith('#copy=')) copyCode(Number(action.slice(6)));
        invalidate();
      });
      anchorPool.set(key, anchor); links.append(anchor);
    }
    anchors.push(anchor); linkIndex++; anchor.hidden = false;
    anchor.setAttribute('href', href); anchor.setAttribute('aria-label', label);
    anchor.setAttribute('title', label);
    anchor.textContent = label;
    if (href.startsWith('#')) anchor.setAttribute('role', 'button'); else anchor.removeAttribute('role');
    if (href.startsWith('#filter=')) anchor.setAttribute('aria-pressed', String(site.category === href.slice(8)));
    else if (href === '#animate') anchor.setAttribute('aria-pressed', String(site.animating));
    else anchor.removeAttribute('aria-pressed');
    Object.assign(anchor.style, { left: `${x}px`, top: `${y}px`, width: `${width}px`, height: `${height}px` });
  },
  search(x, y, width) {
    searchUsed = y + 46 > 0 && y < innerHeight;
    Object.assign(search.style, { left: `${x}px`, top: `${y}px`, width: `${width}px` });
  },
  image(vg, file, x, y, width, height) {
    if (y + height < 0 || y > innerHeight) return;
    let image = images.get(file);
    if (!image) {
      image = new Image(); image.decoding = 'async';
      image.onload = invalidate; image.onerror = invalidate;
      image.src = 'assets/' + file; images.set(file, image);
    }
    if (!image.complete || !image.naturalWidth) return;
    const context = valthorneHost.nano.get(vg), ctx = valthorneHost.nano.prepare(context);
    const scale = Math.max(width / image.naturalWidth, height / image.naturalHeight);
    ctx.beginPath(); ctx.rect(x, y, width, height); ctx.clip();
    ctx.drawImage(image, x + (width - image.naturalWidth * scale) / 2, y + (height - image.naturalHeight * scale) / 2,
      image.naturalWidth * scale, image.naturalHeight * scale);
    context.dirty = true; ctx.restore();
  }
};

async function copyCode(index) {
  const code = page.sections[index].code;
  try { await navigator.clipboard.writeText(code); status.textContent = 'Code copied.'; }
  catch {
    // The semantic companion provides selectable, correctly indented source when clipboard permission is denied.
    location.href = `?view=text#code-${index}`;
  }
}

search.value = site.query;
search.addEventListener('input', () => {
  site.query = search.value;
  const url = new URL(location.href);
  if (site.query) url.searchParams.set('q', site.query); else url.searchParams.delete('q');
  history.replaceState(null, '', url); invalidate();
});
window.addEventListener('scroll', invalidate, { passive: true });
window.addEventListener('resize', invalidate);
document.addEventListener('visibilitychange', () => { lastFrame = 0; if (document.hidden) { cancelAnimationFrame(raf); raf = 0; } else invalidate(); });
window.addEventListener('pagehide', event => { if (!event.persisted) { closed = true; cancelAnimationFrame(raf); shutdownCallback?.(); } });
window.addEventListener('pageshow', invalidate);

try {
  if (!globalThis.WebAssembly) throw new Error('WebAssembly is required for Yoga layout');
  class WebsitePlatform extends BrowserPlatform {
    // This application uses real HTML links and native text input. It does not capture browser keys or start audio.
    unlockAudio() { }
  }
  const platform = new WebsitePlatform(document.querySelector('#scene'));
  Object.defineProperty(platform, 'legacyKeyEvent', { get: () => null, set: () => {} });
  const host = globalThis.valthorneHost = {
    platform,
    connectApplication(frame, shutdown) { frameCallback = frame; shutdownCallback = shutdown; invalidate(); },
    close() {
      closed = true; cancelAnimationFrame(raf);
      this.graphics.close(); this.nano.close(); this.yoga.close(); this.fonts.close(); this.platform.close();
    }
  };
  host.graphics = new BrowserGraphics(host); host.nano = new BrowserNano(host);
  host.yoga = new BrowserYoga(); host.fonts = new BrowserFonts();
  const gl = host.graphics.context();
  host.graphics.canvas.addEventListener('webglcontextlost', event => { event.preventDefault(); fail(new Error('Graphics context lost')); });
  // Two physical pixels per CSS pixel keeps UI crisp on high-density screens without unbounded GPU allocation.
  host.graphics.resize = function () {
    const ratio = Math.min(devicePixelRatio || 1, 2);
    const w = Math.round(innerWidth * ratio), h = Math.round(innerHeight * ratio);
    if (this.canvas.width !== w || this.canvas.height !== h) { this.canvas.width = w; this.canvas.height = h; gl.viewport(0, 0, w, h); }
  };
  host.graphics.resize();
  window.addEventListener('resize', () => host.graphics.resize());
  main();
} catch (error) { fail(error); }
