/** Packages the Java-authored website, verifies its compiled snapshot, and serves local previews. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.dirname(root), output = path.join(root, 'dist');
const sourceRoot = path.join(root, 'src/main/java/valthorne/website');
const exportClasses = path.join(root, 'build/export-classes');
const command = process.argv[2] || 'build';
const read = file => fs.readFile(path.join(root, file), 'utf8');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const normalize = text => text.replaceAll('\r\n', '\n');
// Match Git's LF text normalization on every OS; binary fonts keep exact byte hashes.
const fingerprint = (file, bytes) => hash(file.endsWith('.ttf') ? bytes : normalize(bytes.toString('utf8')));
const processOptions = { cwd: repo, encoding: 'utf8', timeout: 60000, maxBuffer: 1024 * 1024, windowsHide: true };
let compiledExporter;

async function files(dir) {
  const result = [];
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) result.push(...await files(file)); else result.push(file);
  }
  return result.sort();
}

/** Uses JAVA_HOME when configured, otherwise the JDK available on PATH. */
function javaTool(name) {
  return process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', name + (process.platform === 'win32' ? '.exe' : ''))
    : name;
}

/** Builds the dependency-free Java exporter once per packaging/check invocation. */
function compileExporter() {
  return compiledExporter ??= (async () => {
    const groups = await Promise.all(['content', 'export'].map(name => files(path.join(sourceRoot, name))));
    const sources = groups.flat().filter(file => file.endsWith('.java')).sort();
    if (!sources.length) throw new Error('The Java website content/export sources are missing.');
    await fs.mkdir(exportClasses, { recursive: true });
    try {
      execFileSync(javaTool('javac'), ['--release', '17', '-encoding', 'UTF-8', '-d', exportClasses, ...sources], processOptions);
    } catch (error) {
      throw new Error('Unable to compile the website exporter. Install JDK 17 or later and configure JAVA_HOME or PATH. ' + (error.stderr || error.message), { cause: error });
    }
  })();
}

/** Java owns page validation, content, HTML, stylesheet generation, and search-engine files. */
async function runExporterClass(name, args) {
  await compileExporter();
  const result = execFileSync(javaTool('java'), ['-cp', exportClasses, 'valthorne.website.export.' + name, ...args], processOptions);
  if (result.trim()) console.log(result.trim());
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
  const manifest = { description: 'Compiled Java-authored Valthorne website runtime; rebuild with the development portable target.', mainClass: 'valthorne.website.WebsiteApplication', files: {} };
  for (const file of await files(runtime)) if (!file.endsWith('manifest.json')) manifest.files[path.relative(runtime, file).replaceAll('\\', '/')] = fingerprint(file, await fs.readFile(file));
  // Capture every website Java source, including content, browser behavior, and the semantic exporter.
  manifest.sources = {};
  for (const file of await files(path.join(root, 'src'))) manifest.sources[path.relative(root, file).replaceAll('\\', '/')] = fingerprint(file, await fs.readFile(file));
  await fs.writeFile(path.join(runtime, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Captured the website UI runtime and Java source fingerprints.');
}

async function build() {
  // Normalize source line endings so the same checkout receives the same build ID on every OS.
  const sources = (await files(path.join(root, 'src'))).filter(file => file.endsWith('.java'));
  const revisionFiles = [...sources, ...['runtime/manifest.json', 'browser-host.js', 'tools/site.mjs'].map(file => path.join(root, file))];
  const revision = hash((await Promise.all(revisionFiles.map(async file => path.relative(root, file).replaceAll('\\', '/') + '\n' + normalize(await fs.readFile(file, 'utf8'))))).join('\n')).slice(0, 16);
  // Clear only the verified output directory so deleted assets cannot leak into a later deployment.
  if (path.dirname(output) !== root || path.basename(output) !== 'dist') throw new Error('Unsafe output directory');
  await fs.rm(output, { recursive: true, force: true });
  await fs.mkdir(output, { recursive: true });
  await runExporterClass('HtmlExporter', [output, revision]);
  await fs.copyFile(path.join(root, 'browser-host.js'), path.join(output, 'browser-host.js'));
  await fs.cp(path.join(root, 'assets'), path.join(output, 'assets'), { recursive: true });
  await fs.cp(path.join(root, 'runtime'), path.join(output, 'runtime'), { recursive: true });
  await fs.cp(path.join(root, 'licenses'), path.join(output, 'licenses'), { recursive: true });
  await fs.copyFile(path.join(root, 'THIRD_PARTY_NOTICES.md'), path.join(output, 'THIRD_PARTY_NOTICES.md'));
  // UIRoot resolves its bundled font relative to the document, not to its JS module.
  await fs.cp(path.join(root, 'runtime/ui'), path.join(output, 'ui'), { recursive: true });
  console.log('Packaged the Java-authored website in website/dist.');
}

async function check() {
  const manifest = JSON.parse(await read('runtime/manifest.json'));
  for (const [file, digest] of Object.entries(manifest.files)) if (fingerprint(file, await fs.readFile(path.join(root, 'runtime', file))) !== digest) throw new Error('Runtime fingerprint mismatch: ' + file);
  for (const [file, digest] of Object.entries(manifest.sources)) if (fingerprint(file, await fs.readFile(path.join(root, file))) !== digest) throw new Error('Java changed; compile and capture the runtime again: ' + file);
  const javaFiles = (await files(path.join(root, 'src'))).map(file => path.relative(root, file).replaceAll('\\', '/'));
  if (JSON.stringify(javaFiles.sort()) !== JSON.stringify(Object.keys(manifest.sources).sort())) throw new Error('Java sources added or removed; compile and recapture the runtime.');
  for (const file of ['browser-host.js', 'runtime/valthorne.js']) execFileSync(process.execPath, ['--check', path.join(root, file)], processOptions);
  await runExporterClass('ContentValidator', [path.join(root, 'assets')]);
  console.log('Verified Java/runtime fingerprints and browser bootstrap syntax.');
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
else if (command === 'build') { await check(); await build(); }
else if (command === 'check') await check();
else if (command === 'serve') serve();
else throw new Error('Use build, check, serve, or capture-runtime.');
