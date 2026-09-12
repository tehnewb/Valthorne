/** Dependency-free website packaging, validation, and local preview. Run from any directory. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.dirname(root), output = path.join(root, 'dist');
const publicURL = 'https://tehnewb.github.io/Valthorne/';
const command = process.argv[2] || 'build';
const read = file => fs.readFile(path.join(root, file), 'utf8');
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const json = value => JSON.stringify(value).replaceAll('<', '\\u003c');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
// Match Git's LF text normalization on every OS; binary fonts keep exact byte hashes.
const fingerprint = (file, bytes) => hash(file.endsWith('.ttf') ? bytes : bytes.toString('utf8').replaceAll('\r\n', '\n'));

async function files(dir) {
  const result = [];
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) result.push(...await files(file)); else result.push(file);
  }
  return result.sort();
}

/** Captures only the browser UI runtime used by the site, never game models or demo resources. */
async function captureRuntime() {
  const dist = path.join(repo, 'portable/web/build/dist');
  const runtime = path.join(root, 'runtime');
  await fs.mkdir(runtime, { recursive: true });
  const names = ['valthorne.js', 'graphics.js', 'pathtrace.js', 'platform.js', 'window-backend.js', 'nano-backend.js', 'canvas.js', 'fonts.js', 'yoga-backend.js'];
  for (const name of names) await fs.copyFile(path.join(dist, name), path.join(runtime, name));
  await fs.cp(path.join(dist, 'vendor/yoga'), path.join(runtime, 'vendor/yoga'), { recursive: true });
  await fs.cp(path.join(dist, 'vendor/opentype'), path.join(runtime, 'vendor/opentype'), { recursive: true });
  await fs.cp(path.join(repo, 'src/main/resources/ui'), path.join(runtime, 'ui'), { recursive: true });
  await fs.copyFile(path.join(repo, 'LICENSE'), path.join(runtime, 'LICENSE-Valthorne.txt'));
  const manifest = { description: 'Compiled Valthorne website UI runtime; rebuild with the development portable target.', mainClass: 'valthorne.website.WebsiteApplication', files: {} };
  for (const file of await files(runtime)) if (!file.endsWith('manifest.json')) manifest.files[path.relative(runtime, file).replaceAll('\\', '/')] = fingerprint(file, await fs.readFile(file));
  // Record website Java sources too, so CI rejects stale compiled website code.
  manifest.sources = {};
  for (const file of await files(path.join(root, 'src'))) manifest.sources[path.relative(root, file).replaceAll('\\', '/')] = fingerprint(file, await fs.readFile(file));
  await fs.writeFile(path.join(runtime, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Captured the website UI runtime and source fingerprints.');
}

function demoCards() {
  const demos = [
    ['starter','Application starter','systems','','A minimal application with lifecycle callbacks and a clean starting point.'],
    ['scene','3D scene','3d','scene.png','Explore models, materials, cameras, and scene composition.'],
    ['physics','Physics playground','3d','physics.png','Drop and interact with rigid bodies powered by Jolt.'],
    ['lighting2d','2D lighting','2d','lighting2d.png','Explore colored lights, occlusion, and real-time 2D shadows.'],
    ['ui','UI gallery','systems','ui.png','Explore layouts, controls, themes, and text editing.'],
    ['audio','Audio studio','systems','audio.png','Experiment with sound playback, controls, and queues.'],
    ['lighting-studio','Lighting studio','3d','lighting-studio.png','Inspect a lit 3D environment and adjust the scene.'],
    ['path-tracing','Path tracing','3d','path-tracing.png','Explore the optional path-tracing renderer and its settings.'],
    ['physics-studio','Physics studio','3d','physics-studio.png','Manipulate objects and examine an interactive physics scene.'],
    ['fps','FPS arena','3d','','Explore a playable first-person scene with physics, shooting, and effects.']
  ];
  return demos.map(([id,title,category,image,text]) => ({title,category,image,text: text + ' Windows x64; Java included.',label:'Download ZIP',
    href:`https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-demo-${id}-windows-x64-2.0.1.zip`,
    guide:`https://github.com/tehnewb/Valthorne-examples/blob/main/docs/${id}.md`}));
}

async function content() {
  const data = JSON.parse(await read('content.json'));
  data.pages.find(p => p.id === 'examples').sections[0].cards = demoCards();
  const guides = JSON.parse(await read('guides.json'));
  data.pages.find(p => p.id === 'docs').sections[1].cards = guides;
  return data;
}

/** Import the published manual index explicitly; ordinary builds never read unrelated checkout changes. */
async function updateGuides() {
  const manual = execFileSync('git', ['show', 'HEAD:docs/systems/README.md'], { cwd: repo, encoding: 'utf8' });
  const guides = [...manual.matchAll(/^\| \[([^\]]+)\]\(([^)]+)\) \|[^|]+\| ([^|]+)\|/gm)].map(([,title,file,components]) => {
    const category = file.startsWith('ui-') ? 'ui' : /array|stack|bits|data-structures|cache|compression|encryption|buffers|files|pooling|math|utilities/.test(file) ? 'utilities'
      : /runtime|events|timing|state|scenes|assets|audio|plugins|diagnostics|physics/.test(file) ? 'runtime' : 'graphics';
    return {title,category,text:components.replaceAll('`','').replaceAll('…','and related components').trim(),href:`https://github.com/tehnewb/Valthorne/blob/main/docs/systems/${file}`,label:'Read the guide'};
  });
  if (guides.length < 40) throw new Error('The system manual could not be parsed; review its format.');
  await fs.writeFile(path.join(root, 'guides.json'), JSON.stringify(guides, null, 2) + '\n');
  console.log(`Imported ${guides.length} published system guides.`);
}

function html(page, revision) {
  const nav = [['index','Home'],['engine','Engine'],['examples','Demos'],['docs','Docs'],['start','Start'],['lab','Lab'],['about','About']];
  const body = page.sections.map((section,index) => `<section><h2>${escape(section.title)}</h2>${section.description ? `<p>${escape(section.description)}</p>` : ''}
    ${section.code ? `<pre id="code-${index}" tabindex="0"><code>${escape(section.code)}</code></pre>` : ''}
    ${section.lab ? '<p>The interactive canvas is available in the engine view. It starts paused.</p>' : ''}
    <div class="cards">${(section.cards || []).map(card => `<article>${card.image ? `<img src="assets/${escape(card.image)}" alt="${escape(card.title)} running in Valthorne" loading="lazy" width="800" height="460">` : ''}<h3>${escape(card.title)}</h3><p>${escape(card.text)}</p><a href="${escape(card.href)}">${escape(card.label)}</a>${card.guide ? `<a href="${escape(card.guide)}">Controls and source</a>` : ''}</article>`).join('')}</div></section>`).join('');
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(page.id === 'index' ? 'Valthorne — Java game engine' : page.title + ' — Valthorne')}</title>
<meta name="description" content="${escape(page.description)}"><meta name="theme-color" content="#070e19"><meta name="valthorne-build" content="${revision}">
<link rel="canonical" href="${publicURL}${page.id === 'index' ? '' : page.id + '.html'}">
<meta property="og:title" content="${escape(page.title)}"><meta property="og:description" content="${escape(page.description)}"><meta property="og:type" content="website"><meta property="og:url" content="${publicURL}${page.id === 'index' ? '' : page.id + '.html'}"><meta property="og:image" content="${publicURL}assets/banner.png">
<link rel="icon" href="assets/valthorne.png" type="image/png"><link rel="stylesheet" href="shell.css?v=${revision}"></head>
<body><a class="skip-link" href="?view=text#content">Skip to text content</a>
<canvas id="scene" aria-hidden="true"></canvas><div id="scroll-space" aria-hidden="true"></div>
<nav id="engine-links" aria-label="Engine view navigation and actions"></nav>
<input id="engine-search" type="search" aria-label="Search this collection" placeholder="Search titles and systems…" hidden>
<div id="access-bar"><span id="status" role="status">Loading Valthorne…</span><a id="view-toggle" href="?view=text">Text version</a><a href="https://github.com/tehnewb/Valthorne">GitHub ↗</a></div>
<main id="content"><a class="brand" href="index.html"><img src="assets/valthorne.png" alt="Valthorne logo" width="38" height="57">VALTHORNE</a><nav aria-label="Main navigation">${nav.map(([id,label]) => `<a href="${id}.html"${id === page.id ? ' aria-current="page"' : ''}>${label}</a>`).join('')}</nav><div class="eyebrow">${escape(page.eyebrow)}</div>${page.id === 'index' ? '<img class="brand-banner" src="assets/banner.png" alt="Valthorne — gold lettering and blue flame" width="1511" height="623">' : ''}<h1>${escape(page.title)}</h1><p class="intro">${escape(page.description)}</p>${body}<section><p>Created by Albert Beaupre. Valthorne is open source under Apache-2.0.</p><a href="about.html">About the project</a></section></main>
<script id="page-content" type="application/json">${json(page)}</script><script src="boot.js?v=${revision}"></script>
</body></html>\n`;
}

async function build() {
  const data = await content();
  // A revision ties the compiled Java, host, and stylesheet together across browser caches.
  const revision = hash((await Promise.all(['runtime/manifest.json','site-host.js','boot.js','shell.css'].map(read))).join('\n')).slice(0, 16);
  // Clear only the verified output directory so deleted assets cannot leak into a later deployment.
  if (path.dirname(output) !== root || path.basename(output) !== 'dist') throw new Error('Unsafe output directory');
  await fs.rm(output, { recursive: true, force: true });
  await fs.mkdir(output, { recursive: true });
  // Output is a fixed child of this module; no computed recursive deletion is used.
  for (const page of data.pages) await fs.writeFile(path.join(output, page.id + '.html'), html(page, revision));
  for (const name of ['shell.css', 'site-host.js', 'boot.js']) await fs.copyFile(path.join(root, name), path.join(output, name));
  await fs.cp(path.join(root, 'assets'), path.join(output, 'assets'), { recursive: true });
  await fs.cp(path.join(root, 'runtime'), path.join(output, 'runtime'), { recursive: true });
  await fs.cp(path.join(root, 'licenses'), path.join(output, 'licenses'), { recursive: true });
  await fs.copyFile(path.join(root, 'THIRD_PARTY_NOTICES.md'), path.join(output, 'THIRD_PARTY_NOTICES.md'));
  // UIRoot resolves its bundled font relative to the document, not to its JS module.
  await fs.cp(path.join(root, 'runtime/ui'), path.join(output, 'ui'), { recursive: true });
  await fs.writeFile(path.join(output, '.nojekyll'), '');
  await fs.writeFile(path.join(output, 'robots.txt'), `User-agent: *\nAllow: /\nSitemap: ${publicURL}sitemap.xml\n`);
  await fs.writeFile(path.join(output, 'sitemap.xml'), `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${data.pages.map(page => `<url><loc>${publicURL}${page.id === 'index' ? '' : page.id + '.html'}</loc></url>`).join('')}</urlset>`);
  await fs.writeFile(path.join(output, '404.html'), `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Page not found — Valthorne</title><link rel="stylesheet" href="/Valthorne/shell.css"><link rel="icon" href="/Valthorne/assets/valthorne.png"></head><body><main id="content"><img class="brand-banner" src="/Valthorne/assets/banner.png" alt="Valthorne" width="1511" height="623"><div class="eyebrow">404 / PAGE NOT FOUND</div><h1>The page could not be found.</h1><p>Check the address, or continue exploring the engine.</p><a href="/Valthorne/">Return to Valthorne</a></main></body></html>`);
  console.log(`Built ${data.pages.length} Valthorne pages in website/dist.`);
}

async function check() {
  const data = await content();
  const manifest = JSON.parse(await read('runtime/manifest.json'));
  for (const [file, digest] of Object.entries(manifest.files)) if (fingerprint(file, await fs.readFile(path.join(root, 'runtime', file))) !== digest) throw new Error('Runtime fingerprint mismatch: ' + file);
  for (const [file, digest] of Object.entries(manifest.sources)) if (fingerprint(file, await fs.readFile(path.join(root, file))) !== digest) throw new Error('Java changed; compile and capture the runtime again: ' + file);
  const javaFiles = (await files(path.join(root, 'src'))).map(file => path.relative(root, file).replaceAll('\\', '/'));
  if (JSON.stringify(javaFiles.sort()) !== JSON.stringify(Object.keys(manifest.sources).sort())) throw new Error('Java sources added or removed; compile and recapture the runtime.');
  for (const file of ['boot.js','site-host.js','runtime/valthorne.js']) execFileSync(process.execPath, ['--check', path.join(root, file)]);
  const pages = new Set(data.pages.map(page => page.id + '.html'));
  let count = 0;
  for (const page of data.pages) {
    if (!page.title || !page.description || !page.sections.length) throw new Error('Incomplete page: ' + page.id);
    for (const section of page.sections) for (const card of section.cards || []) {
      for (const href of [card.href,card.guide].filter(Boolean)) {
        if (/^https:\/\//.test(href)) { new URL(href); }
        else if (!href.startsWith('?') && !pages.has(href.split('?')[0])) throw new Error('Unknown local link: ' + href);
        count++;
      }
      if (card.image) await fs.access(path.join(root, 'assets', card.image));
    }
  }
  console.log(`Validated ${data.pages.length} pages, ${count} content links, Java/runtime fingerprints, and script syntax.`);
}

function serve() {
  const mime = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.wasm':'application/wasm','.png':'image/png','.svg':'image/svg+xml','.ttf':'font/ttf','.txt':'text/plain','.xml':'application/xml'};
  createServer(async (request,response) => {
    try {
      if (!['GET','HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return; }
      const url = new URL(request.url, 'http://localhost');
      if (url.pathname === '/') { response.writeHead(302,{Location:'/Valthorne/' + url.search}); response.end(); return; }
      if (!url.pathname.startsWith('/Valthorne/')) throw new Error('Outside site');
      const local = decodeURIComponent(url.pathname.slice('/Valthorne/'.length)) || 'index.html';
      const file = path.resolve(output, local);
      if (!file.startsWith(output + path.sep)) throw new Error('Outside site');
      const bytes = await fs.readFile(file);
      response.writeHead(200,{'Content-Type':mime[path.extname(file)] || 'application/octet-stream','Cache-Control':'no-store'});
      response.end(request.method === 'HEAD' ? undefined : bytes);
    } catch { response.writeHead(404,{'Content-Type':'text/html'}); response.end(await fs.readFile(path.join(output,'404.html'))); }
  }).listen(Number(process.env.PORT || 8097), '127.0.0.1', () => console.log(`Valthorne preview: http://127.0.0.1:${process.env.PORT || 8097}/Valthorne/`));
}

if (command === 'capture-runtime') await captureRuntime();
else if (command === 'update-guides') await updateGuides();
else if (command === 'build') { await check(); await build(); }
else if (command === 'check') await check();
else if (command === 'serve') serve();
else throw new Error('Use build, check, serve, capture-runtime, or update-guides.');
