import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const metadata = JSON.parse(await readFile(new URL('./engine-materials.json', import.meta.url), 'utf8'));
const digest = bytes => createHash('sha256').update(bytes).digest('hex');

for (const material of metadata.materials) {
    const source = await readFile(new URL(
        `../../src/main/resources/valthorne/filament/${material.name}.mat`, import.meta.url), 'utf8');
    // Git checkouts and existing Windows editors can differ only in line endings.
    // Preserve every other source byte so shader edits still require recompilation.
    assert.equal(digest(source.replace(/\r\n/g, '\n')), material.sourceSha256,
        `Recompile browser ${material.name} with ${metadata.compiler} ${metadata.arguments}, then update engine-materials.json`);
    const binary = await readFile(new URL(`./public/engine-${material.name}.filamat`, import.meta.url));
    assert.equal(digest(binary), material.binarySha256, `Browser material hash mismatch: ${material.name}`);
}
console.log('Shared engine material sources and browser packages verified');
