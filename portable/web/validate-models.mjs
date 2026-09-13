import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {validateBytes} from 'gltf-validator';
const models=JSON.parse(await readFile('public/models/provenance.json','utf8'));
for(const model of models){
    if(!/^[a-z]+\.glb$/.test(model.file))throw new Error('Invalid model filename');
    const bytes=await readFile(`public/models/${model.file}`);
    if(createHash('sha256').update(bytes).digest('hex')!==model.outputSha256)throw new Error(`Model provenance mismatch: ${model.file}`);
    const result=await validateBytes(bytes,{uri:model.file});
    if(result.issues.numErrors)throw new Error(JSON.stringify(result.issues));
    console.log(`Validated ${model.file}`);
}
