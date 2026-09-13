// Reproducible conversion of the bundled CC0 Kenney examples, not a general glTF repair tool.
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import validator from 'gltf-validator';
const sources={tree:'tree_oak.glb',tent:'tent_detailedClosed.glb'};
const manifest=[];
for(const [name,source] of Object.entries(sources)) {
    const input=await readFile(new URL(`../../assets/3d/kenney/nature-kit/Models/GLTF format/${source}`,import.meta.url));
    const length=input.readUInt32LE(12),json=JSON.parse(input.subarray(20,20+length));
    if(json.asset.generator!=='UniGLTF-1.27'||json.nodes[0].name!=='tmpParent'
        ||JSON.stringify(json.nodes[0].children)!=='[1]'||JSON.stringify(json.scenes[0].nodes)!=='[1]')
        throw new Error(`Unexpected source structure: ${source}`);
    // The exporter made child 1 a scene root while leaving its identity parent 0.
    json.scenes[0].nodes=[0];
    // Art conversion: these foliage/fabric/wood examples use nonmetallic surfaces.
    for(const material of json.materials)material.pbrMetallicRoughness.metallicFactor=0;
    let encoded=Buffer.from(JSON.stringify(json));
    encoded=Buffer.concat([encoded,Buffer.alloc((4-encoded.length%4)%4,0x20)]);
    const rest=input.subarray(20+length),header=Buffer.alloc(20);
    header.writeUInt32LE(0x46546c67,0);header.writeUInt32LE(2,4);header.writeUInt32LE(20+encoded.length+rest.length,8);
    header.writeUInt32LE(encoded.length,12);header.writeUInt32LE(0x4e4f534a,16);
    const output=Buffer.concat([header,encoded,rest]);
    const report=await validator.validateBytes(new Uint8Array(output));
    if(report.issues.numErrors)throw new Error(JSON.stringify(report.issues));
    await writeFile(new URL(`public/models/${name}.glb`,import.meta.url),output);
    manifest.push({file:`${name}.glb`,source,sourceSha256:createHash('sha256').update(input).digest('hex'),
        outputSha256:createHash('sha256').update(output).digest('hex'),license:'CC0',validatorErrors:0});
}
await writeFile(new URL('public/models/provenance.json',import.meta.url),JSON.stringify(manifest,null,2));
console.log('MODEL_IMPORT_VALIDATED',manifest.map(x=>x.file).join(', '));
