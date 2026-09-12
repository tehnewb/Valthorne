/* Load the compiled engine only for the visual view. The semantic document needs no JavaScript. */
'use strict';
if (new URLSearchParams(location.search).get('view') === 'text') {
  document.querySelector('#status').textContent = 'Text version';
  const toggle = document.querySelector('#view-toggle');
  toggle.textContent = 'Engine version';
  const url = new URL(location.href); url.searchParams.delete('view'); toggle.href = url.pathname + url.search;
} else {
  const timeout = setTimeout(() => {
    if (!globalThis.valthorneReady) document.querySelector('#status').textContent = 'Text version is available while the engine loads.';
  }, 8000);
  const runtime = document.createElement('script'); runtime.src = 'runtime/valthorne.js';
  runtime.onload = () => { clearTimeout(timeout); import('./site-host.js').catch(error => {
    document.querySelector('#status').textContent = 'Engine unavailable. Text version is ready.';
    console.error(error);
  }); };
  runtime.onerror = () => { clearTimeout(timeout); document.querySelector('#status').textContent = 'Engine unavailable. Text version is ready.'; };
  document.head.append(runtime);
}
