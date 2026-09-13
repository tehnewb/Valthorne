import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {validateBytes} from 'gltf-validator';
import {deformingGlb} from './animation-fixture.mjs';
import {mkdir,writeFile} from 'node:fs/promises';
import {browserTestOptions} from './browser-test-options.mjs';
const browser=await chromium.launch(browserTestOptions);
try{
 const page=await browser.newPage({viewport:{width:800,height:600}}),errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 for(const name of ['skin','morph']){const bytes=deformingGlb(name==='skin'),validation=await validateBytes(bytes);assert.equal(validation.issues.numErrors,0,JSON.stringify(validation.issues));await page.route(`**/models/${name}.glb`,r=>r.fulfill({body:bytes}));}
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneReady,null,{timeout:45000});
 await page.waitForFunction(()=>valthorneHost.assets.pending.size===0&&valthorneHost.assets.models.size===3);
 const result=await page.evaluate(async()=>{
  const host=valthorneHost,assets=host.assets;host.paused=true;
  // Leave the Java runtime alone, but remove unrelated scene geometry from visibility.
  for(const object of host.objects)host.scene.remove(object.entity);
  for(const model of assets.models.values())for(const entity of model.entities)host.scene.remove(entity);
  host.firstPerson={x:0,y:1,z:5,yaw:0,pitch:0,fov:45};
  const baseline=assets.sourceBytes,results=[];
  for(const name of ['skin','morph']){
   const id=await new Promise((resolve,reject)=>assets.load(`models/${name}.glb`,resolve,reject)),model=assets.get(id);
   const manager=host.engine.getRenderableManager(),transforms=host.engine.getTransformManager();
   const mesh=model.entities.find(e=>manager.hasComponent(e));
   const instance=manager.getInstance(mesh);manager.setCulling(instance,false);instance.delete();
   function transform(){const i=transforms.getInstance(mesh);try{return Array.from(transforms.getTransform(i));}finally{i.delete();}}
   async function sample(time){
    assets.animate(id,0,time);await new Promise(requestAnimationFrame);host.render();
    const canvas=document.querySelector('#scene'),gl=canvas.getContext('webgl2'),pixels=new Uint8Array(canvas.width*canvas.height*4);gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
    if(name==='skin'&&time>.9)globalThis.deformingPreview=canvas.toDataURL('image/png');
    let top=0,bottom=0,topX=0,bottomX=0;
    for(let y=0;y<canvas.height;y++)for(let x=0;x<canvas.width;x++){const at=(y*canvas.width+x)*4;if(pixels[at]>100&&pixels[at+2]>100&&pixels[at+1]<80){if(y>canvas.height*.58){top++;topX+=x;}else if(y<canvas.height*.42){bottom++;bottomX+=x;}}}
    if(!top||!bottom)throw new Error('Deforming mesh is not visible: '+JSON.stringify({name,time,top,bottom}));
    return {top,bottom,topX:topX/top,bottomX:bottomX/bottom,transform:transform()};
   }
   const first=await sample(0),middle=await sample(.5),last=await sample(.999),rewind=await sample(0);
   results.push({name,first,middle,last,rewind});assets.release(id);
  }
  for(let i=0;i<10;i++){const id=await new Promise((resolve,reject)=>assets.load('models/skin.glb',resolve,reject));assets.animate(id,0,.5);assets.release(id);}
  return {results,sourceBytes:assets.sourceBytes,baseline,models:assets.models.size,pending:assets.pending.size};
 });
 for(const {name,first,middle,last,rewind} of result.results){
  assert.deepEqual(first.transform,last.transform,name+' mesh must remain stationary');
  assert(last.topX-first.topX>60,name+' upper vertices must deform');
  assert(Math.abs(last.bottomX-first.bottomX)<35,name+' lower vertices must stay anchored');
  assert(middle.topX>first.topX+20&&middle.topX<last.topX-20,name+' interpolation');
  assert(Math.abs(rewind.topX-first.topX)<2,name+' rewind');
 }
 assert.equal(result.sourceBytes,result.baseline);assert.equal(result.models,3);assert.equal(result.pending,0);assert.deepEqual(errors,[]);
 await mkdir('build/verification',{recursive:true});await writeFile('build/verification/skeletal-animation.json',JSON.stringify(result,null,2));await writeFile('build/verification/skeletal-animation.png',Buffer.from((await page.evaluate(()=>deformingPreview)).split(',')[1],'base64'));
 console.log('SKELETAL_AND_MORPH_ANIMATION_VALIDATED '+JSON.stringify(result));
}finally{await browser.close();}
