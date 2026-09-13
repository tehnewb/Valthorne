import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>{messages.push(m.text());if(['RADIANCE_OCCLUDER_DISABLED','RADIANCE_INTENSITY_ZERO'].includes(m.text()))page.evaluate(()=>installRadianceProbe()).catch(e=>errors.push(String(e)));});page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.graphics.gl&&document.querySelector('aside').hidden||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.evaluate(()=>{
  const h=valthorneHost,close=h.close;h.close=function(){globalThis.radianceLeaks=this.graphics.objects.size+this.graphics.images.size;return close.call(this);};
  const gl=h.graphics.gl,draw=gl.drawArraysInstanced;globalThis.radianceCaptures=[];
  globalThis.installRadianceProbe=()=>{gl.drawArraysInstanced=function(...args){draw.apply(gl,args);if(gl.getParameter(gl.FRAMEBUFFER_BINDING)!==null||args[3]!==1)return;const pixels=new Uint8Array(640*480*4);gl.readPixels(0,0,640,480,gl.RGBA,gl.UNSIGNED_BYTE,pixels);radianceCaptures.push({pixels,error:gl.getError()});gl.drawArraysInstanced=draw;};};installRadianceProbe();
 });
 await page.waitForFunction(()=>radianceCaptures.length>0,null,{timeout:5000});
 const initial=await page.evaluate(()=>{const {pixels:p,error}=radianceCaptures[0];let red=0,blue=0,spill=0;for(let i=0;i<p.length;i+=4){if(p[i]>100&&p[i]>p[i+2]*2)red++;if(p[i+2]>100&&p[i+2]>p[i]*2)blue++;if(Math.max(p[i],p[i+2])>2&&Math.max(p[i],p[i+2])<80)spill++;}return{red,blue,spill,error};});
 assert.equal(initial.error,0);assert(initial.red>1000&&initial.blue>1000&&initial.spill>1000,JSON.stringify(initial));
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-radiance.png',output))});
 await page.waitForFunction(()=>valthorneHost.closed,null,{timeout:15000});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_RADIANCE_VALIDATED checks=5'),messages.join('\n'));assert(messages.includes('COMMON_RADIANCE_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>radianceLeaks),0);
 const changes=await page.evaluate(()=>{if(radianceCaptures.length!==3)return{captures:radianceCaptures.length};let occlusion=0,remaining=0;const a=radianceCaptures[0].pixels,b=radianceCaptures[1].pixels,c=radianceCaptures[2].pixels;for(let i=0;i<a.length;i+=4){if(b[i]>a[i]+2||b[i+2]>a[i+2]+2)occlusion++;remaining+=c[i]+c[i+1]+c[i+2];}return{occlusion,remaining,errors:radianceCaptures.map(v=>v.error)};});
 assert.deepEqual(changes.errors,[0,0,0],JSON.stringify(changes));assert(changes.occlusion>100,JSON.stringify(changes));assert.equal(changes.remaining,0,JSON.stringify(changes));
 for(const message of messages)if(message.startsWith('RADIANCE_BENCHMARK'))console.log(message);console.log('COMMON_RADIANCE_BROWSER_VALIDATED '+JSON.stringify({initial,changes}));
}finally{await browser.close();}
