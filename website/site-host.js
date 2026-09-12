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
const interaction = document.querySelector('#scene-interaction');
const motionToggle = document.querySelector('#motion-toggle');
const backTop = document.querySelector('#back-top');
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
let motionPaused = false;
try { motionPaused = sessionStorage.getItem('valthorne-motion') === 'paused'; } catch { /* Storage is optional. */ }
const motion = { now: 0, active: false, groups: new Map(), opacity: 1, offset: 0 };
const motionEnabled = () => !motionPaused && !reducedMotion.matches;
let frameCallback, shutdownCallback, raf = 0, lastFrame = 0, closed = false, frames = 0;
let urgentFrame = false;
let ready = false, linkIndex = 0, searchUsed = false, sceneUsed = false, sceneVisible = false;
const startupTimeout = setTimeout(() => { if (!ready) fail(new Error('Website engine startup timed out')); }, 20000);
const images = new Map(), measurements = new Map(), anchorPool = new Map(), occurrences = new Map();
let anchors = [];
// Generic font families are CSS keywords. Quoting "monospace" changes it into a missing named font.
const cssFamily = family => ['serif','sans-serif','monospace','system-ui'].includes(family) ? family : JSON.stringify(family);
/** UI aliases use the loaded Urbanist family with identical drawing and measurement weights. */
function fontSpec(context, size) {
  const face = context.state.face;
  if (face === 'display' || face === 'ui-medium' || face === 'default') return `${face === 'display' ? 700 : face === 'ui-medium' ? 600 : 400} ${size}px UrbanistWebsite, sans-serif`;
  const family = context.fonts.get(face)?.family || face || 'sans-serif';
  return `${size}px ${cssFamily(family)}`;
}

/** Finite reveals settle completely. Only an on-screen, unpaused scene keeps drawing. */
function invalidate(continuous = false) {
  if (continuous !== true) urgentFrame = true;
  if (closed || document.hidden || !frameCallback || raf) return;
  raf = requestAnimationFrame(now => {
    raf = 0;
    try {
      // Reveal motion can use 60 fps; the slow ornamental scene needs only 30.
      // Scroll, focus, pointer input, and image completion still redraw immediately.
      if (!urgentFrame && lastFrame && now - lastFrame < 1000 / (motion.active ? 60 : 30) - 1) { invalidate(true); return; }
      urgentFrame = false;
      motion.now = now;
      frameCallback(lastFrame ? Math.min((now - lastFrame) / 1000, .05) : 0);
      lastFrame = now; frames++;
      if (motion.active || (site.animating && sceneVisible && motionEnabled())) invalidate(true);
    } catch (error) { fail(error); }
  });
}

function fail(error) {
  clearTimeout(startupTimeout);
  closed = true; cancelAnimationFrame(raf);
  document.documentElement.classList.remove('engine-ready');
  document.querySelector('#scene').hidden = true;
  links.hidden = true; search.hidden = true; interaction.hidden = true; motionToggle.hidden = true; backTop.hidden = true;
  semantic.querySelectorAll('a').forEach(anchor => anchor.removeAttribute('tabindex'));
  semantic.querySelectorAll('pre').forEach(pre => pre.tabIndex = 0);
  status.textContent = 'The engine view could not start. The complete text version is available below.';
  globalThis.valthorneError = String(error.stack || error);
  console.error(error);
}

/** Only browser responsibilities cross this bridge; all geometry comes from Java. */
globalThis.site = {
  page, query: params.get('q') || '', category: 'all', animating: true, orbit: 0, tilt: 0, copied: -1, font: 'sans-serif',
  motionEnabled,
  running() { return site.animating && sceneVisible && motionEnabled(); },
  begin() {
    linkIndex = 0; searchUsed = false; sceneUsed = false; motion.active = false;
    site.clearEffect(); anchors = []; occurrences.clear();
  },
  end(height) {
    for (const anchor of anchorPool.values()) anchor.hidden = !anchors.includes(anchor);
    search.hidden = !searchUsed;
    interaction.hidden = !sceneUsed;
    sceneVisible = sceneUsed;
    document.querySelector('#scroll-space').style.height = Math.max(innerHeight, Math.ceil(height)) + 'px';
    document.querySelector('#access-bar').style.top = Math.max(innerHeight - 58, height - 58) + 'px';
    backTop.hidden = scrollY < 650;
    // Header links are painted last. Give them first place in keyboard navigation.
    const header = anchors.slice(Math.max(0, linkIndex - 7), linkIndex);
    const ordered = [...header, ...anchors.slice(0, Math.max(0, linkIndex - 7))];
    ordered.forEach((anchor, index) => {
      const isHeader = header.includes(anchor), headerHeight = innerWidth < 900 ? 120 : 88;
      const covered = isHeader ? 0 : Math.max(0, headerHeight - parseFloat(anchor.style.top));
      anchor.style.zIndex = isHeader ? '3' : '1';
      anchor.style.clipPath = covered ? `inset(${covered}px 0 0)` : '';
      anchor.tabIndex = covered ? -1 : 0;
      if (covered >= parseFloat(anchor.style.height)) anchor.hidden = true;
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
    globalThis.websiteMetrics = {
      get frames() { return frames; }, get animations() { return site.running(); },
      get revealing() { return motion.active; }, get sceneVisible() { return sceneVisible; },
      get reducedMotion() { return reducedMotion.matches; }, get motionEnabled() { return motionEnabled(); }
    };
  },
  /** Reveal each visible group once. Logical layout never shifts; links follow the painted offset. */
  reveal(key, y) {
    site.clearEffect();
    if (!motionEnabled()) return;
    let started = motion.groups.get(key);
    if (started === undefined) {
      if (y >= innerHeight - 28) return;
      started = y < (innerWidth < 900 ? 120 : 88) ? motion.now - 600 : motion.now;
      motion.groups.set(key, started);
    }
    const progress = Math.min(1, Math.max(0, (motion.now - started) / 600));
    const eased = 1 - Math.pow(1 - progress, 3);
    // Keep text at its full contrast throughout the entrance; only position changes.
    motion.opacity = 1; motion.offset = (1 - eased) * 20;
    if (progress < 1) motion.active = true;
  },
  clearEffect() { motion.opacity = 1; motion.offset = 0; },
  scene(x, y, width, height) {
    const header = innerWidth < 900 ? 120 : 88;
    const top = Math.max(y + motion.offset, header), bottom = Math.min(y + height + motion.offset, innerHeight);
    sceneUsed = bottom > top && y + height > header;
    Object.assign(interaction.style, { left: `${x}px`, top: `${top}px`, width: `${width}px`, height: `${Math.max(0, bottom - top)}px` });
  },
  measure(vg, text, size) {
    const context = valthorneHost.nano.get(vg);
    const font = fontSpec(context, size);
    const key = `${font}:${text}`;
    if (measurements.has(key)) return measurements.get(key);
    context.ctx.font = font;
    const width = context.ctx.measureText(text).width;
    if (measurements.size > 15000) measurements.clear();
    measurements.set(key, width); return width;
  },
  link(label, href, x, y, width, height) {
    y += motion.offset;
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
        if (action === '#animate' && motionEnabled()) { site.animating = !site.animating; lastFrame = 0; }
        else if (action === '#reset') { site.orbit = 0; site.tilt = 0; }
        else if (action === '#clear') { site.category = 'all'; search.value = ''; search.dispatchEvent(new Event('input')); search.focus({ preventScroll: true }); }
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
    else if (href === '#animate') anchor.setAttribute('aria-pressed', String(site.running()));
    else anchor.removeAttribute('aria-pressed');
    if (href === '#animate' && !motionEnabled()) anchor.setAttribute('aria-disabled', 'true');
    else anchor.removeAttribute('aria-disabled');
    if (href === page.id + '.html' || (page.id === 'index' && href === 'index.html')) anchor.setAttribute('aria-current', 'page');
    else anchor.removeAttribute('aria-current');
    anchor.classList.toggle('primary-action', label === 'Start building');
    anchor.classList.toggle('pill-action', height === 44);
    Object.assign(anchor.style, { left: `${x}px`, top: `${y}px`, width: `${width}px`, height: `${height}px` });
  },
  search(x, y, width) {
    y += motion.offset;
    searchUsed = y >= (innerWidth < 900 ? 120 : 88) && y < innerHeight;
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
    const branding = file === 'banner.png' || file === 'valthorne.png';
    const scale = (branding ? Math.min : Math.max)(width / image.naturalWidth, height / image.naturalHeight);
    ctx.beginPath();
    if (branding) ctx.rect(x, y, width, height); else ctx.roundRect(x, y, width, height, 4);
    ctx.clip();
    ctx.drawImage(image, x + (width - image.naturalWidth * scale) / 2, y + (height - image.naturalHeight * scale) / 2,
      image.naturalWidth * scale, image.naturalHeight * scale);
    context.dirty = true; ctx.restore();
  }
};

/** Pointer and keyboard controls only own the contained scene; vertical touch scrolling stays native. */
let pointer = null;
interaction.addEventListener('pointerdown', event => {
  if (event.button !== 0) return;
  pointer = { id: event.pointerId, x: event.clientX, y: event.clientY };
  interaction.setPointerCapture(event.pointerId); interaction.classList.add('dragging');
});
interaction.addEventListener('pointermove', event => {
  if (!pointer || event.pointerId !== pointer.id) return;
  site.orbit += (event.clientX - pointer.x) * .009;
  site.tilt = Math.max(-.45, Math.min(.45, site.tilt + (event.clientY - pointer.y) * .004));
  pointer.x = event.clientX; pointer.y = event.clientY; invalidate();
});
for (const name of ['pointerup','pointercancel','lostpointercapture']) interaction.addEventListener(name, () => {
  pointer = null; interaction.classList.remove('dragging');
});
interaction.addEventListener('keydown', event => {
  if (!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home'].includes(event.key)) return;
  event.preventDefault();
  if (event.key === 'Home') { site.orbit = 0; site.tilt = 0; }
  else if (event.key === 'ArrowLeft') site.orbit -= .15;
  else if (event.key === 'ArrowRight') site.orbit += .15;
  else site.tilt = Math.max(-.45, Math.min(.45, site.tilt + (event.key === 'ArrowDown' ? .06 : -.06)));
  invalidate();
});

function syncMotion() {
  const enabled = motionEnabled();
  document.documentElement.classList.toggle('motion-paused', !enabled);
  motionToggle.setAttribute('aria-label', reducedMotion.matches ? 'Reduced motion is enabled in your system settings' : enabled ? 'Pause motion' : 'Enable motion');
  motionToggle.title = motionToggle.getAttribute('aria-label');
  motionToggle.setAttribute('aria-pressed', String(!enabled));
  motionToggle.disabled = reducedMotion.matches;
  if (!enabled) motion.groups.forEach((_, key) => motion.groups.set(key, -1000));
  lastFrame = 0; invalidate();
}
motionToggle.addEventListener('click', () => {
  motionPaused = !motionPaused;
  try { sessionStorage.setItem('valthorne-motion', motionPaused ? 'paused' : 'enabled'); } catch { /* No storage is required. */ }
  syncMotion();
});
reducedMotion.addEventListener('change', syncMotion);
syncMotion();
backTop.addEventListener('click', () => { scrollTo({ top: 0, behavior: motionEnabled() ? 'smooth' : 'instant' }); });

let copyTimeout;
async function copyCode(index) {
  const code = page.sections[index].code;
  try {
    await navigator.clipboard.writeText(code); status.textContent = 'Code copied.';
    site.copied = index; clearTimeout(copyTimeout); invalidate();
    copyTimeout = setTimeout(() => { site.copied = -1; invalidate(); }, 2400);
  }
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
  // Load the exact self-hosted variable font before Java measures a single line.
  // The semantic document remains readable with its fallback while loading.
  const displayFont = new FontFace('UrbanistWebsite', 'url(assets/fonts/Urbanist-Variable.ttf)', { weight: '100 900', style: 'normal' });
  await displayFont.load(); document.fonts.add(displayFont);
  class WebsitePlatform extends BrowserPlatform {
    // This application uses real HTML links and native text input. It does not capture browser keys or start audio.
    unlockAudio() { }
  }
  const platform = new WebsitePlatform(document.querySelector('#scene'));
  // The visual surface is aria-hidden; native links and search own keyboard focus.
  document.querySelector('#scene').tabIndex = -1;
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
  const font = host.nano.font.bind(host.nano);
  host.nano.font = context => {
    font(context);
    context.ctx.font = fontSpec(context, context.state.size);
  };
  // Compose finite motion around the existing vector backend without changing the engine snapshot.
  const prepare = host.nano.prepare.bind(host.nano);
  host.nano.prepare = context => {
    const ctx = prepare(context); ctx.globalAlpha *= motion.opacity; ctx.translate(0, motion.offset); return ctx;
  };
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
