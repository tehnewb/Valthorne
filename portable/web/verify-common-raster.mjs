import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>{messages.push(m.text());if(m.text()==='RASTER_SHADOW_DISABLED')page.evaluate(()=>installRasterProbe()).catch(e=>errors.push(String(e)));});page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.graphics.gl&&document.querySelector('aside').hidden||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.evaluate(()=>{
  const h=valthorneHost,close=h.close;h.close=function(){globalThis.rasterLeaks=this.graphics.objects.size+this.graphics.images.size;return close.call(this);};
  const gl=h.graphics.gl,draw=gl.drawArrays;globalThis.rasterCaptures=[];
  globalThis.installRasterProbe=()=>{gl.drawArrays=function(...args){draw.apply(gl,args);if(args[2]!==6||gl.getParameter(gl.FRAMEBUFFER_BINDING)!==null)return;const pixels=new Uint8Array(640*480*4);gl.readPixels(0,0,640,480,gl.RGBA,gl.UNSIGNED_BYTE,pixels);rasterCaptures.push({pixels,error:gl.getError()});gl.drawArrays=draw;};};installRasterProbe();
 });
 await page.waitForFunction(()=>rasterCaptures.length>0,null,{timeout:5000});
 const stats=await page.evaluate(()=>{const {pixels,error}=rasterCaptures[0];let red=0,blue=0,green=0,neutral=0;for(let i=0;i<pixels.length;i+=4){const r=pixels[i],g=pixels[i+1],b=pixels[i+2];if(r>100&&r>g*2&&r>b*2)red++;if(b>150&&b>r*2&&b>g*2)blue++;if(g>150&&g>r*2&&g>b*2)green++;if(r>40&&Math.abs(r-b)<8&&Math.abs(r-g)<8)neutral++;}return{red,blue,green,neutral,error};});
 assert.equal(stats.error,0);assert(stats.red>100&&stats.blue>500&&stats.green>100&&stats.neutral>10000,JSON.stringify(stats));
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-raster.png',output))});
 await page.waitForFunction(()=>valthorneHost.closed,null,{timeout:10000});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_RASTER_VALIDATED checks=5'),messages.join('\n'));assert(messages.includes('COMMON_RASTER_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.rasterLeaks),0);
 const shadow=await page.evaluate(()=>{if(rasterCaptures.length!==2)return{captures:rasterCaptures.length};const a=rasterCaptures[0].pixels,b=rasterCaptures[1].pixels;let changed=0;for(let i=0;i<a.length;i+=4)if(b[i]>a[i]+10&&Math.abs(a[i]-a[i+1])<8&&Math.abs(a[i]-a[i+2])<8)changed++;return{changed,error:rasterCaptures[1].error};});
 assert.equal(shadow.error,0,JSON.stringify(shadow));assert(shadow.changed>20,'Shadow removal did not brighten receivers: '+JSON.stringify(shadow));
 for(const message of messages)if(message.startsWith('RASTER_BENCHMARK'))console.log(message);console.log('COMMON_RASTER_BROWSER_VALIDATED '+JSON.stringify({stats,shadow}));
}finally{await browser.close();}
