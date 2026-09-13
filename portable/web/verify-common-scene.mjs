import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
    const page=await browser.newPage({viewport:{width:960,height:640}}),errors=[],messages=[];
    page.on('pageerror',e=>errors.push(String(e)));page.on('console',m=>messages.push(m.text()));
    await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');
    await page.waitForFunction(()=>globalThis.valthorneReady||globalThis.valthorneError,null,{timeout:20000});
    assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
    await page.waitForFunction(()=>valthorneHost.frames>=30||valthorneHost.closed);
    assert(!await page.evaluate(()=>valthorneHost.closed),messages.join('\n'));
    const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});
    await page.screenshot({path:fileURLToPath(new URL('common-scene.png',output))});
    const read=()=>page.evaluate(()=>new Promise((resolve,reject)=>{
        const h=valthorneHost,r=[...h.sceneRenderers][0],render=h.renderer.render;
        const timeout=setTimeout(()=>{h.renderer.render=render;reject(new Error('No rendered Filament pixels'));},5000);
        h.renderer.render=function(...args){
            const result=render.apply(this,args);
            const gl=document.querySelector('#scene').getContext('webgl2'),pixels=new Uint8Array(gl.drawingBufferWidth*gl.drawingBufferHeight*4);
            gl.readPixels(0,0,gl.drawingBufferWidth,gl.drawingBufferHeight,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
            let sum=0,colored=0,vivid=0;for(let i=0;i<pixels.length;i+=4){sum+=pixels[i]+pixels[i+1]+pixels[i+2];if(Math.max(pixels[i],pixels[i+1],pixels[i+2])-Math.min(pixels[i],pixels[i+1],pixels[i+2])>25)colored++;}
            for(let y=160;y<300;y++)for(let x=260;x<420;x++){const i=(y*gl.drawingBufferWidth+x)*4;if(Math.max(pixels[i],pixels[i+1],pixels[i+2])-Math.min(pixels[i],pixels[i+1],pixels[i+2])>80)vivid++;}
            if(sum>0){h.renderer.render=render;clearTimeout(timeout);resolve({sum,colored,vivid,entries:r.entries.length,meshes:r.meshes.size,lights:r.lightCount(),textured:r.entries.some(e=>e.texture>0),frames:h.frames});}
            return result;
        };
    }));
    const lit=await read();assert(lit.colored>10000,JSON.stringify(lit));assert.equal(lit.meshes,2);assert.equal(lit.lights,3);
    await page.keyboard.down('b');await page.waitForFunction(start=>valthorneHost.frames>start+10,lit.frames);
    // Whole-frame luminance includes the unchanged sky and fill light; require a
    // substantial change across the image, not only a changed native light count.
    const unlit=await read();assert.equal(unlit.lights,2);assert(unlit.sum<lit.sum*.95,`Light did not affect image: ${JSON.stringify({lit,unlit})}`);
    await page.keyboard.up('b');await page.keyboard.down('t');await page.waitForFunction(()=>![...valthorneHost.sceneRenderers][0].entries.some(e=>e.texture>0));
    const plain=await read();assert.equal(plain.textured,false);assert(plain.vivid<lit.vivid*.5,JSON.stringify({lit,plain}));
    await page.keyboard.up('t');await page.keyboard.press('n');
    await page.keyboard.press('Escape');await page.waitForFunction(()=>valthorneHost.closed||globalThis.valthorneError);
    assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[]);
    assert(messages.some(m=>m.includes('COMMON_SCENE_VALIDATED')),messages.join('\n'));
    assert.equal(await page.evaluate(()=>valthorneHost.sceneRenderers.size+valthorneHost.physicsWorlds.size),0);
    console.log('COMMON_SCENE_BROWSER_VALIDATED '+JSON.stringify({lit,unlit,plain}));
}finally{await browser.close();}
