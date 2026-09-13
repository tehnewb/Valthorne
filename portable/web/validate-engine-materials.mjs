import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
const metadata=JSON.parse(await readFile(new URL('./engine-materials.json',import.meta.url),'utf8'));
const digest=async url=>createHash('sha256').update(await readFile(url)).digest('hex');
for(const material of metadata.materials){
    assert.equal(await digest(new URL(`../../src/main/resources/valthorne/filament/${material.name}.mat`,import.meta.url)),material.sourceSha256,`Recompile browser ${material.name} with ${metadata.compiler} ${metadata.arguments}, then update engine-materials.json`);
    assert.equal(await digest(new URL(`./public/engine-${material.name}.filamat`,import.meta.url)),material.binarySha256,`Browser material hash mismatch: ${material.name}`);
}
console.log('Shared engine material sources and browser packages verified');
