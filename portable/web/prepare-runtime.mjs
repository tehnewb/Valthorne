// Official browser build, pinned to the same Filament version as the desktop adapter.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {gunzipSync} from 'node:zlib';
const version='1.75.0';
const expected='81c9e82ad209ae6cb6e24c384a05c0eacf22c9316203995fed75d8649c89faa6';
const dir=new URL('./build/runtime/',import.meta.url),archive=new URL('filament.tgz',dir);
await mkdir(dir,{recursive:true});
let compressed;
try{compressed=await readFile(archive);}catch{}
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
if(!compressed||sha(compressed)!==expected){
    const response=await fetch(`https://github.com/google/filament/releases/download/v${version}/filament-v${version}-web.tgz`);
    if(!response.ok)throw new Error(`Filament download failed: ${response.status}`);
    compressed=Buffer.from(await response.arrayBuffer());
    if(sha(compressed)!==expected)throw new Error('Filament archive integrity mismatch');
    await writeFile(archive,compressed);
}
const tar=gunzipSync(compressed),wanted=new Set(['filament.js','filament.wasm','filament.d.ts']);
for(let offset=0;offset+512<=tar.length;){
    const name=tar.subarray(offset,offset+100).toString().replace(/\0.*$/s,'');
    if(!name)break;
    const size=parseInt(tar.subarray(offset+124,offset+136).toString().replace(/\0.*$/s,'').trim(),8);
    if(!Number.isSafeInteger(size)||size<0||offset+512+size>tar.length)throw new Error('Invalid runtime archive');
    if(wanted.delete(name))await writeFile(new URL(name,dir),tar.subarray(offset+512,offset+512+size));
    offset+=512+Math.ceil(size/512)*512;
}
if(wanted.size)throw new Error('Runtime archive is incomplete');
console.log(`Filament ${version} browser runtime verified`);

// Tint's SPIR-V -> WGSL browser build, from the pinned Babylon.js release.
await mkdir(new URL('twgsl/',dir),{recursive:true});
for(const [name,hash] of [['twgsl.js','b4f1f66263b801210f955f74aa71f1939be647ce0fd80ea5befd12a78499d5ff'],['twgsl.wasm','a434c2decdbb38caadf5f486d806384a1543554828c983c77922b2d92579914c']]){
 const destination=new URL('twgsl/'+name,dir);let bytes;try{bytes=await readFile(destination);}catch{}
 if(!bytes||sha(bytes)!==hash){const response=await fetch('https://raw.githubusercontent.com/BabylonJS/Babylon.js/9.26.0/packages/tools/babylonServer/public/twgsl/'+name);if(!response.ok)throw new Error('Tint download failed: '+response.status);bytes=Buffer.from(await response.arrayBuffer());if(sha(bytes)!==hash)throw new Error('Tint integrity mismatch');await writeFile(destination,bytes);}
}
