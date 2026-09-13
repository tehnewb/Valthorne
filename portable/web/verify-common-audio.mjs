import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>{errors.push(String(e));console.error(String(e));});
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>(globalThis.valthorneHost?.audioBackend.sources.size===2&&[...valthorneHost.audioBackend.sources.values()].every(s=>s.state===0x1012&&s.nodes.length))||globalThis.valthorneError,null,{timeout:60000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.mouse.click(320,240);await page.waitForFunction(()=>valthorneHost.platform.audio.state==='running');
 const output=await page.evaluate(()=>new Promise(resolve=>{
  const backend=valthorneHost.audioBackend,context=backend.context(),analyser=context.createAnalyser();analyser.fftSize=1024;
  for(const source of backend.sources.values())source.gain.connect(analyser);
  const samples=new Float32Array(1024),deadline=performance.now()+1800;
  function sample(){analyser.getFloatTimeDomainData(samples);let peak=0;for(const value of samples)peak=Math.max(peak,Math.abs(value));if(peak<=.005&&performance.now()<deadline){setTimeout(sample,25);return;}for(const source of backend.sources.values())source.gain.disconnect(analyser);analyser.disconnect();resolve({peak,sources:backend.sources.size,buffers:backend.buffers.size});}sample(); }));
 assert(output.peak>.005,JSON.stringify(output));
 await page.evaluate(()=>{const a=valthorneHost.audioBackend,close=a.close;a.close=function(){globalThis.audioLeaks=this.sources.size+this.buffers.size;return close.call(this);};});
 await page.waitForFunction(()=>valthorneHost.closed||globalThis.valthorneError,null,{timeout:10000});assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[]);assert(messages.some(m=>m.includes('COMMON_AUDIO_VALIDATED')),messages.join('\n'));assert(messages.some(m=>m.includes('COMMON_AUDIO_RETURNED')),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.audioLeaks),0);
 console.log('COMMON_AUDIO_BROWSER_VALIDATED '+JSON.stringify(output));
}finally{await browser.close();}
