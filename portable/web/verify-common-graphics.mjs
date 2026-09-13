import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');
 await page.waitForFunction(()=>globalThis.valthorneHost?.graphics.gl||globalThis.valthorneError);
 await page.waitForFunction(()=>document.querySelector('aside').hidden||globalThis.valthorneError);
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.evaluate(()=>{const graphics=valthorneHost.graphics,close=graphics.close;graphics.close=function(){globalThis.graphicsLeaks=this.objects.size;return close.call(this);};});
 // Read the actual backbuffer synchronously after the final draw, before the
 // browser presents and invalidates its default framebuffer.
 const pixels=await page.evaluate(()=>new Promise((resolve,reject)=>{
  const timeout=setTimeout(()=>reject(new Error("Expected graphics pixels were not drawn")),5000);
  const graphics=valthorneHost.graphics,gl=graphics.gl,draw=gl.drawArraysInstanced;
  gl.drawArraysInstanced=function(...args){draw.apply(gl,args);
   if(gl.getParameter(gl.FRAMEBUFFER_BINDING)!==null)return;
   const sample=(x,y)=>{const p=new Uint8Array(4);gl.readPixels(x,y,1,1,gl.RGBA,gl.UNSIGNED_BYTE,p);return [...p];};
   const data={red:sample(40,140),blue:sample(40,40),green:sample(140,140),alpha:sample(380,50),clipInside:sample(250,70),clipOutside:sample(210,70),fbo:sample(40,260),custom:sample(240,340),error:gl.getError()};
   if(data.custom[0]>200&&data.fbo[0]>200){gl.drawArraysInstanced=draw;clearTimeout(timeout);resolve(data);}
  };
 }));
 const near=(actual,expected,label)=>{for(let i=0;i<3;i++)assert(Math.abs(actual[i]-expected[i])<=3,`${label}: ${actual}`);};
 near(pixels.red,[255,0,0],'top red');near(pixels.blue,[0,0,255],'bottom blue');near(pixels.green,[0,255,0],'top green');
 near(pixels.alpha,[0,128,0],'alpha blending');near(pixels.clipInside,[255,0,0],'clip interior');near(pixels.clipOutside,[0,0,0],'clip exterior');near(pixels.fbo,[255,0,0],'framebuffer blit');
 assert.equal(pixels.error,0,'WebGL reported an error');
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-graphics.png',output))});
 await page.waitForFunction(()=>valthorneHost.closed||globalThis.valthorneError,null,{timeout:10000});
 assert(messages.some(m=>m.includes('COMMON_GRAPHICS_VALIDATED')),messages.join('\n'));assert(messages.some(m=>m.includes('COMMON_GRAPHICS_RETURNED')),messages.join('\n'));assert.deepEqual(errors,[]);
 assert.equal(await page.evaluate(()=>valthorneHost.graphics.objects.size+valthorneHost.graphics.images.size),0);
 assert.equal(await page.evaluate(()=>globalThis.graphicsLeaks),0,'Application disposal left graphics handles alive before host cleanup');
 for(const message of messages)if(message.startsWith('GRAPHICS_BENCHMARK'))console.log(message);
 console.log('COMMON_GRAPHICS_BROWSER_VALIDATED '+JSON.stringify(pixels));
}finally{await browser.close();}
