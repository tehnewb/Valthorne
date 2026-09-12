/** Internal navigation keeps the Java application, font, and graphics context alive. */
export function installNavigation({ getState, render, prepare, surface, motionEnabled }) {
  const root = new URL('.', location.href);
  const pages = new Set(['index', 'engine', 'examples', 'docs', 'start', 'lab', 'about']);
  const revision = document.querySelector('meta[name="valthorne-build"]').content;
  const cache = new Map(), visits = new Map();
  let sequence = 0, request = 0, navigating = false, navigations = 0, overlay, animation;
  const newKey = () => `${Date.now()}-${++sequence}`;
  let currentKey = history.state?.valthorneVisit || newKey();
  history.scrollRestoration = 'manual';
  history.replaceState({ ...history.state, valthorneVisit: currentKey }, '');

  /** Only ordinary links to this site's visual pages participate. Browser gestures remain native. */
  function destination(anchor) {
    if (!anchor || anchor.hasAttribute('download') || (anchor.target && anchor.target !== '_self')) return null;
    const href = anchor.getAttribute('href');
    if (!href || href.startsWith('#')) return null;
    const url = new URL(href, location.href);
    if (url.origin !== root.origin || url.hash || url.searchParams.has('view')) return null;
    const file = url.pathname.slice(root.pathname.length) || 'index.html';
    if (!url.pathname.startsWith(root.pathname) || !pages.has(file.replace(/\.html$/, '')) || !file.endsWith('.html')) return null;
    return url;
  }

  /** Cache the document and warm its opening media before replacing anything on screen. */
  function load(url) {
    if (!cache.has(url.pathname)) {
      const controller = new AbortController();
      let timeout;
      const pending = Promise.race([(async () => {
        // Revalidate the first visit so a previous deployment's HTTP cache cannot
        // mix old documents with the currently running engine. Later visits use cache above.
        const response = await fetch(url.pathname, { signal: controller.signal, cache: 'no-cache' });
        if (!response.ok) throw new Error('Page could not be loaded');
        const document = new DOMParser().parseFromString(await response.text(), 'text/html');
        if (document.querySelector('meta[name="valthorne-build"]')?.content !== revision) throw new Error('Website version changed');
        const data = JSON.parse(document.querySelector('#page-content').textContent);
        if (!pages.has(data.id) || !document.querySelector('#content')) throw new Error('Invalid website page');
        await prepare(data);
        return { document, data };
      })(), new Promise((_, reject) => {
        timeout = setTimeout(() => { controller.abort(); reject(new Error('Page preparation timed out')); }, 8000);
      })]).finally(() => clearTimeout(timeout));
      cache.set(url.pathname, pending);
      pending.catch(() => cache.delete(url.pathname));
    }
    return cache.get(url.pathname);
  }

  function finishTransition() {
    animation?.cancel(); animation = null;
    overlay?.remove(); overlay = null;
  }

  /** Snapshot only the old body. The fixed header never fades or changes its geometry. */
  function snapshot() {
    finishTransition();
    if (!motionEnabled() || !Element.prototype.animate) return null;
    const canvas = surface(), copy = document.createElement('canvas'), cover = document.createElement('div');
    copy.width = canvas.width; copy.height = canvas.height;
    copy.getContext('2d').drawImage(canvas, 0, 0);
    cover.className = 'page-transition'; cover.setAttribute('aria-hidden', 'true');
    cover.append(copy); document.body.append(cover); overlay = cover;
    return cover;
  }

  function remember() {
    const state = getState(); visits.set(currentKey, state); return state;
  }

  /** Update the semantic companion and page metadata with the same content Java will paint. */
  function updateDocument(next) {
    const content = document.querySelector('#content');
    content.replaceChildren(...[...next.querySelector('#content').childNodes].map(node => document.importNode(node, true)));
    content.querySelectorAll('a,pre').forEach(node => node.tabIndex = -1);
    document.querySelector('#page-content').textContent = next.querySelector('#page-content').textContent;
    document.title = next.title;
    for (const selector of ['link[rel="canonical"]', 'meta[name="description"]', 'meta[property^="og:"]']) {
      document.head.querySelectorAll(selector).forEach(node => node.remove());
      next.head.querySelectorAll(selector).forEach(node => document.head.append(document.importNode(node, true)));
    }
  }

  async function navigate(url, visit) {
    const token = ++request;
    navigating = true;
    document.querySelector('#engine-links').setAttribute('aria-busy', 'true');
    const previous = remember();
    try {
      const next = await load(url);
      if (token !== request) return;
      const restored = visit ? visits.get(visit.key) || visit.saved : undefined;
      const old = snapshot();
      if (!visit) {
        // Back/Forward changes history before its page finishes loading. A new
        // click must not overwrite that destination with the still-visible page.
        if (history.state?.valthorneVisit === currentKey) {
          history.replaceState({ ...history.state, valthorneSaved: previous }, '');
        }
        currentKey = newKey();
        history.pushState({ valthorneVisit: currentKey }, '', url);
      } else currentKey = visit.key;
      updateDocument(next.document);
      render(next.data, url, restored);
      remember(); navigations++;
      // Native page links precede the hidden HTML companion. Focus their landmark
      // so the next Tab reaches page navigation instead of skipping to the footer.
      const navigation = document.querySelector('#engine-links');
      navigation.setAttribute('aria-label', `${next.data.title} — navigation and actions`);
      navigation.tabIndex = -1; navigation.focus({ preventScroll: true });
      document.querySelector('#status').textContent = 'Rendered with Valthorne';
      if (old) {
        // Fade through the page background: two different headlines never overlap.
        animation = old.firstChild.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 90, easing: 'ease-out', fill: 'forwards' });
        await animation.finished.catch(() => {});
        if (overlay === old) {
          animation = old.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 110, easing: 'ease-out' });
          await animation.finished.catch(() => {});
        }
        if (overlay === old) finishTransition();
      }
    } catch {
      // Network failures and deployments with new runtime versions retain normal document navigation.
      if (token === request) {
        if (visit) location.reload(); else location.assign(url.href);
      }
    } finally {
      if (token === request) {
        navigating = false;
        document.querySelector('#engine-links').removeAttribute('aria-busy');
      }
    }
  }

  document.addEventListener('click', event => {
    if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const url = destination(event.target.closest('a'));
    if (!url) return;
    event.preventDefault(); void navigate(url);
  });
  for (const name of ['pointerover', 'focusin']) document.addEventListener(name, event => {
    const url = destination(event.target.closest('a'));
    if (url) void load(url).catch(() => {});
  });
  window.addEventListener('popstate', event => {
    const url = new URL(location.href);
    if (!event.state?.valthorneVisit || url.searchParams.has('view')) { location.reload(); return; }
    void navigate(url, { key: event.state.valthorneVisit, saved: event.state.valthorneSaved });
  });
  window.addEventListener('resize', finishTransition);
  document.addEventListener('visibilitychange', () => { if (document.hidden) finishTransition(); });
  return { get navigating() { return navigating; }, get navigations() { return navigations; }, finishTransition };
}
