import {browserTestOptions} from './browser-test-options.mjs';
import {waitForFixtureCompletion} from './browser-fixture-wait.mjs';
import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch(browserTestOptions);
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.fonts.fonts.size>0&&document.querySelector('aside').hidden);
 await page.evaluate(()=>{const h=valthorneHost,close=h.close;h.close=function(){globalThis.fontLeaks=this.fonts.fonts.size+this.graphics.objects.size;return close.call(this);};});
 const data=await page.evaluate(()=>new Promise(resolve=>{const gl=valthorneHost.graphics.gl,draw=gl.drawArraysInstanced;gl.drawArraysInstanced=function(...args){draw.apply(gl,args);const p=new Uint8Array(640*480*4);gl.readPixels(0,0,640,480,gl.RGBA,gl.UNSIGNED_BYTE,p);let white=0;for(let i=0;i<p.length;i+=4)if(p[i]>180&&p[i+1]>180&&p[i+2]>180)white++;if(white>100){gl.drawArraysInstanced=draw;resolve({white,error:gl.getError()});}};}));
 assert(data.white>1000,JSON.stringify(data));assert.equal(data.error,0);
 assert(await page.evaluate(()=>[...valthorneHost.fonts.fonts].every(f=>f.ctx.canvas.width===1&&f.pixels===null&&f.glyphs===null)),'Font retained transferred atlas copies');
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-font.png',output))});
 await waitForFixtureCompletion(page,{fixture:'common-font',messages,errors});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_FONT_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.fontLeaks),0);
 console.log('COMMON_FONT_BROWSER_VALIDATED '+JSON.stringify(data));
}finally{await browser.close();}
