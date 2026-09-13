import {chromium}from'@playwright/test';import assert from'node:assert/strict';
import {browserTestOptions} from './browser-test-options.mjs';
const browser=await chromium.launch(browserTestOptions);
try{
 const page=await browser.newPage(),messages=[],errors=[];page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(e.stack||String(e)));
 await page.addInitScript(()=>{const draw=WebGL2RenderingContext.prototype.drawArrays;WebGL2RenderingContext.prototype.drawArrays=function(...args){draw.apply(this,args);if(this.canvas.id==='valthorne-2d'&&this.getParameter(this.DRAW_FRAMEBUFFER_BINDING)===null){const pixels=new Uint8Array(4);this.readPixels(10,10,1,1,this.RGBA,this.UNSIGNED_BYTE,pixels);globalThis.computePixel=Array.from(pixels);}};});
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.closed||globalThis.valthorneError,null,{timeout:30000});assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_COMPUTE_VALIDATED')&&messages.includes('COMMON_COMPUTE_RETURNED'),messages.join('\n'));
 const result=await page.evaluate(()=>({pixel:computePixel,buffers:valthorneHost.compute.buffers.size,programs:valthorneHost.compute.programs.size,objects:valthorneHost.graphics.objects.size}));assert(result.pixel.every((value,i)=>Math.abs(value-[128,64,32,255][i])<=1),JSON.stringify(result));assert.equal(result.buffers+result.programs+result.objects,0);console.log('COMMON_COMPUTE_BROWSER_VALIDATED '+JSON.stringify(result));
}finally{await browser.close();}
