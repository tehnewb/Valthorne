import { chromium } from '@playwright/test';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { animatedGlb } from './animation-fixture.mjs';
import {validateBytes} from 'gltf-validator';

await mkdir('build',{recursive:true});
const browser=await chromium.launch({channel:process.env.BROWSER_CHANNEL||'chrome',headless:true});
try {
    const page=await browser.newPage({viewport:{width:1280,height:800}});
    page.setDefaultTimeout(15000);
    const errors=[];
    page.on('pageerror',error=>errors.push(String(error)));
    await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');
    await page.waitForFunction(()=>globalThis.valthorneReady||globalThis.valthorneError,null,{timeout:45000});
    assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined);
    await page.waitForFunction(()=>valthorneHost.assets.models.size===3&&valthorneHost.assets.pending.size===0);
    await page.waitForFunction(()=>globalThis.valthorneHost.javaSeconds>=2);
    const initial=await page.evaluate(()=>({count:valthorneHost.objects.length,y:valthorneHost.objects.at(-1).body.GetPosition().GetY()}));
    assert.equal(initial.count,26);
    assert(initial.y<5.1 && initial.y>0,'Jolt gravity and floor contact must change the stack height without falling through');
    await page.getByRole('button',{name:'Pause simulation'}).click();
    await page.waitForTimeout(100);
    const before=await page.evaluate(()=>valthorneHost.steps);
    await page.waitForTimeout(200);
    assert.equal(await page.evaluate(()=>valthorneHost.steps),before,'Paused Java loop must not step Jolt');
    await page.getByRole('button',{name:'Drop a body'}).click();
    await page.waitForFunction(()=>valthorneHost.javaBodies===26);
    const yaw=await page.evaluate(()=>valthorneHost.yaw);
    await page.mouse.move(500,400);await page.mouse.down();await page.mouse.move(600,430);await page.mouse.up();
    assert.notEqual(await page.evaluate(()=>valthorneHost.yaw),yaw);
    const distance=await page.evaluate(()=>valthorneHost.distance);
    await page.mouse.wheel(0,-200);await page.waitForTimeout(100);
    assert((await page.evaluate(()=>valthorneHost.distance))<distance);
    await page.locator('#color').fill('#ff5577');
    await page.getByRole('button',{name:'Resume simulation'}).click();
    await page.waitForFunction(previous=>valthorneHost.steps>previous,before);
    for(let i=0;i<12;i++) {
        await page.getByRole('button',{name:'Drop a body'}).click();
        await page.waitForTimeout(25);
        await page.getByRole('button',{name:'Rebuild stack'}).click();
        await page.waitForFunction(()=>valthorneHost.javaBodies===25&&valthorneHost.objects.length===26);
        await page.waitForFunction(()=>valthorneHost.assets.models.size===3&&valthorneHost.assets.pending.size===0);
    }
    const result=await page.evaluate(async()=>{
        const times=[]; let last=performance.now();
        for(let i=0;i<180;i++) {await new Promise(requestAnimationFrame); const now=performance.now(); times.push(now-last);last=now;}
        times.sort((a,b)=>a-b);
        const host=valthorneHost;
        return {frames:host.frames,steps:host.steps,bodies:host.objects.length,materials:host.materials.size,
            meanFrameIntervalMs:times.reduce((a,b)=>a+b,0)/times.length,p95FrameIntervalMs:times[Math.floor(times.length*.95)],
            joltHeapBytes:host.J.HEAP8.buffer.byteLength,javaSeconds:host.javaSeconds,viewport:[innerWidth,innerHeight],
            importedModels:host.assets.models.size,modelSourceBytes:host.assets.sourceBytes};
    });
    assert.equal(result.bodies,26);assert.equal(result.materials,3);
    assert.equal(result.importedModels,3);
    await page.route('**/models/invalid.glb',route=>route.fulfill({status:200,body:'invalid model'}));
    const malformed=await page.evaluate(()=>new Promise(resolve=>valthorneHost.assets.load('models/invalid.glb',()=>resolve('unexpected success'),resolve)));
    assert.match(malformed,/Truncated GLB/);
    const lifecycle=await page.evaluate(async()=>{
        const assets=valthorneHost.assets,baseline=assets.sourceBytes;
        const failures=[];
        const cancelled=assets.load('models/tree.glb',()=>{throw new Error('Cancelled request completed');},reason=>failures.push(reason));
        assets.cancel(cancelled);assets.cancel(cancelled);
        const missing=await new Promise(resolve=>assets.load('models/missing.glb',()=>resolve('unexpected success'),resolve));
        const id=await new Promise((resolve,reject)=>assets.load('models/tree.glb',resolve,reject));
        assets.cancel(id); // Completion transfers ownership; cancellation must not release it.
        assets.transform(id,6,0,2,.3,1);
        const clips=assets.animationCount(id);
        assets.release(id);assets.release(id);
        let staleRejected=false;
        try{assets.transform(id,0,0,0,0,1);}catch{staleRejected=true;}
        const requests=[];
        for(let i=0;i<5;i++)requests.push(assets.load('models/tree.glb',()=>{},()=>{}));
        let budgetRejected=false;
        try{assets.load('models/tree.glb',()=>{},()=>{});}catch{budgetRejected=true;}
        requests.forEach(request=>assets.cancel(request));
        return {failures,missing,clips,staleRejected,budgetRejected,count:assets.models.size,pending:assets.pending.size,bytes:assets.sourceBytes,baseline};
    });
    assert.deepEqual(lifecycle.failures,['cancelled']);
    assert.match(lifecycle.missing,/HTTP 404/);
    assert.equal(lifecycle.clips,0);assert(lifecycle.staleRejected);assert(lifecycle.budgetRejected);
    assert.equal(lifecycle.count,3);assert.equal(lifecycle.pending,0);assert.equal(lifecycle.bytes,lifecycle.baseline);
    result.assetLifecycle=lifecycle;
    const animationBytes=animatedGlb();assert.equal((await validateBytes(animationBytes)).issues.numErrors,0);
    await page.route('**/models/animated.glb',route=>route.fulfill({status:200,body:animationBytes}));
    const animation=await page.evaluate(async()=>{
        const assets=valthorneHost.assets,id=await new Promise((resolve,reject)=>assets.load('models/animated.glb',resolve,reject));
        const clips=assets.animationCount(id);assets.animate(id,0,.5);
        const model=assets.get(id),manager=valthorneHost.engine.getTransformManager();
        const positions=model.entities.map(entity=>{const instance=manager.getInstance(entity);try{return manager.getTransform(instance)[12];}finally{instance.delete();}});
        assets.release(id);return {clips,positions};
    });
    assert.equal(animation.clips,1);assert(animation.positions.some(x=>Math.abs(x-1)<.001));result.animation=animation;
    await page.keyboard.down('w');
    assert.equal(await page.evaluate(()=>valthorneHost.platform.keyDown('KeyW')),true);
    await page.keyboard.up('w');
    const platform=await page.evaluate(async()=>{
        const h=valthorneHost,p=h.platform;
        p.saveSetting('verification','persisted');const saved=p.loadSetting('verification');p.removeSetting('verification');
        p.keys.add('KeyW');window.dispatchEvent(new Event('blur'));const cleared=!p.keyDown('KeyW');
        p.beginOverlay();p.rectangle(10,10,20,20,0xff0000,.5);
        const pixel=Array.from(p.ctx.getImageData(15,15,1,1).data);p.beginOverlay();
        p.unlockAudio();await p.audio.resume();const played=p.tone(220,.03,.1,0);
        const body=h.createBox(9,4,8,[.5,.5,.5],0x98aabb,true,true);
        const distance=h.rayDistance(9,8,8,0,-1,0,20,body.id);
        h.changeBody(body.id,0,1,2,3);const velocity=h.bodyComponent(body.id,3);
        h.changeBody(body.id,2,9,5,8);const position=h.bodyComponent(body.id,1);
        h.releaseBody(body.id);h.releaseBody(body.id);
        let stale=false;try{h.bodyComponent(body.id,0);}catch{stale=true;}
        await new Promise(resolve=>setTimeout(resolve,80));
        return {saved,removed:p.loadSetting('verification'),cleared,pixel,played,voices:p.voices.size,distance,velocity,position,stale};
    });
    assert.equal(platform.saved,'persisted');assert.equal(platform.removed,null);assert(platform.cleared);
    assert.equal(platform.pixel[0],255);assert(Math.abs(platform.pixel[3]-128)<=1);
    assert(platform.played);assert.equal(platform.voices,0);
    assert(Math.abs(platform.distance-8)<.05);assert.equal(platform.velocity,2);assert.equal(platform.position,5);assert(platform.stale);
    result.platform=platform;
    assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined);
    await page.screenshot({path:'build/web-desktop.png'});
    await page.setViewportSize({width:390,height:844});await page.waitForTimeout(100);
    await page.screenshot({path:'build/web-mobile-layout.png'});
    const closing=await page.evaluate(()=>{
        const failures=[];
        valthorneHost.assets.load('models/tree.glb',()=>{throw new Error('Closed request completed');},reason=>failures.push(reason));
        valthorneHost.close();valthorneHost.close();
        return {failures,pending:valthorneHost.assets.pending.size,bytes:valthorneHost.assets.sourceBytes};
    });
    assert.deepEqual(closing,{failures:['cancelled'],pending:0,bytes:0});
    assert.equal(await page.evaluate(()=>valthorneHost.objects.length),0);
    assert.equal(await page.evaluate(()=>valthorneHost.assets.models.size),0);
    assert.deepEqual(errors,[]);
    await writeFile('build/web-validation.json',JSON.stringify(result,null,2));
    console.log('WEB_VALIDATED',JSON.stringify(result));
} finally {await browser.close();}
