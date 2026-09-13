import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');
 await page.waitForFunction(()=>globalThis.valthorneHost?.graphics.gl&&document.querySelector('aside').hidden||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.evaluate(()=>{
  const h=valthorneHost,close=h.close;h.close=function(){globalThis.legacyLeaks=this.graphics.objects.size+this.graphics.images.size;return close.call(this);};
  const gl=h.graphics.gl,draw=gl.drawArrays;let particleCount;
  gl.drawArrays=function(...args){draw.apply(gl,args);if(args[0]===gl.POINTS)particleCount=args[2];if(args[2]!==3||gl.getParameter(gl.FRAMEBUFFER_BINDING)!==null)return;
   const pixels=new Uint8Array(640*480*4);gl.readPixels(0,0,640,480,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
   const at=(x,y)=>Array.from(pixels.slice((y*640+x)*4,(y*640+x)*4+4));
   globalThis.legacyResult={particleCount,left:at(180,240),right:at(470,240),dark:at(10,10),error:gl.getError()};gl.drawArrays=draw;
  };
 });
 await page.waitForFunction(()=>globalThis.legacyResult,null,{timeout:5000});
 const result=await page.evaluate(()=>legacyResult);console.log('LEGACY2D_PIXELS '+JSON.stringify(result));
 assert.equal(result.error,0);assert.equal(result.particleCount,8);
 assert(result.left[0]>100&&result.left[1]<30,JSON.stringify(result));
 assert(result.right[2]>result.right[0]*2&&result.right[2]>100,JSON.stringify(result));
 assert(result.dark[0]<60&&result.dark[1]<60&&result.dark[2]<60,JSON.stringify(result));
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-legacy2d.png',output))});
 await page.waitForFunction(()=>valthorneHost.closed,null,{timeout:10000});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_LEGACY2D_VALIDATED checks=4'),messages.join('\n'));assert(messages.includes('COMMON_LEGACY2D_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>legacyLeaks),0);
 for(const message of messages)if(message.startsWith('LEGACY2D_BENCHMARK'))console.log(message);console.log('COMMON_LEGACY2D_BROWSER_VALIDATED');
}finally{await browser.close();}
