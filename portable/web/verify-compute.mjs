import {chromium} from '@playwright/test';import assert from 'node:assert/strict';
import {mkdir,writeFile}from'node:fs/promises';
import {browserTestOptions} from './browser-test-options.mjs';
const browser=await chromium.launch(browserTestOptions);
try{
 const page=await browser.newPage();page.on("console",m=>console.log(m.text()));await page.route('**/compute-test.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><meta charset="utf-8"><canvas id="scene"></canvas>'}));
 await page.goto((process.env.TEST_URL||'http://127.0.0.1:8095')+'/compute-test.html');
 const result=await page.evaluate(async()=>{
  const {BrowserCompute}=await import('./compute.js'),{BrowserGraphics}=await import('./graphics.js');
  const host={platform:{window:{width:64,height:64}}};host.graphics=new BrowserGraphics(host);const compute=await BrowserCompute.create(host);
  if(!compute.supported())throw new Error('WebGPU unavailable for required compute validation');
  const start=performance.now();
  const id=await compute.compile(`#version 430 core
layout(local_size_x=8) in;
layout(std430,binding=0) buffer Values { uint values[]; };
uniform uint addend;
shared uint localValues[8];
void main(){uint i=gl_GlobalInvocationID.x;localValues[gl_LocalInvocationID.x]=values[i]+addend;barrier();values[i]=localValues[7u-gl_LocalInvocationID.x];}`);
  const buffer=compute.createBuffer(64);compute.updateBuffer(buffer,0,new Uint32Array(Array.from({length:16},(_,i)=>i)));compute.bindBuffer(buffer,0);compute.bind(id);compute.uniform(id,'addend',[100]);await compute.dispatch(2,1,1);
  const data=await compute.readBuffer(buffer),values=Array.from(new Uint32Array(data.buffer));
  const gl=host.graphics.context(),texture=host.graphics.create('Texture');gl.bindTexture(gl.TEXTURE_2D,host.graphics.get(texture));host.graphics.upload(gl.TEXTURE_2D,0,gl.RGBA8,16,16,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
  const imageProgram=await compute.compile(`#version 430 core
layout(local_size_x=8,local_size_y=8) in;
layout(rgba8,binding=0) uniform writeonly image2D outputImage;
uniform vec4 color;
void main(){imageStore(outputImage,ivec2(gl_GlobalInvocationID.xy),color);}`);
  compute.bind(imageProgram);compute.uniform(imageProgram,'color',[1,.25,.5,1]);compute.bindImage(texture,0,0x88B9,gl.RGBA8);await compute.dispatch(2,2,1);
  const pixels=host.graphics.readTexturePixels(texture,gl.UNSIGNED_BYTE),pixel=Array.from(pixels.subarray(0,4));
  let overflowRejected=false;try{compute.updateBuffer(buffer,60,new Uint32Array(2));}catch{overflowRejected=true;}
  // Exercise read/modify/write across both APIs, including a float render target.
  gl.getExtension('EXT_color_buffer_float');
  const floatTexture=host.graphics.create('Texture');gl.bindTexture(gl.TEXTURE_2D,host.graphics.get(floatTexture));host.graphics.upload(gl.TEXTURE_2D,0,gl.RGBA32F,1,1,0,gl.RGBA,gl.FLOAT,new Float32Array([1,2,3,4]));
  const modify=await compute.compile(`#version 430 core
layout(local_size_x=1) in;
layout(rgba32f,binding=0) uniform image2D values;
void main(){imageStore(values,ivec2(0),imageLoad(values,ivec2(0))+1.0);imageStore(values,ivec2(0),imageLoad(values,ivec2(0))*2.0);}`);
  compute.bind(modify);compute.bindImage(floatTexture,0,0x88BA,gl.RGBA32F);await compute.dispatch(1,1,1);
  const floatPixel=Array.from(host.graphics.readTexturePixels(floatTexture,gl.FLOAT));
  compute.deleteProgram(modify);host.graphics.remove(floatTexture,'Texture');
  const integerPixels=[];
  for(const signed of [false,true]){
   const type=signed?gl.INT:gl.UNSIGNED_INT,internal=signed?gl.RGBA32I:gl.RGBA32UI,Type=signed?Int32Array:Uint32Array,tex=host.graphics.create('Texture');
   gl.bindTexture(gl.TEXTURE_2D,host.graphics.get(tex));host.graphics.upload(gl.TEXTURE_2D,0,internal,1,1,0,gl.RGBA_INTEGER,type,new Type([1,2,3,4]));
   const program=await compute.compile(`#version 430 core
layout(local_size_x=1) in;
layout(${signed?'rgba32i':'rgba32ui'},binding=0) uniform ${signed?'i':'u'}image2D values;
void main(){imageStore(values,ivec2(0),imageLoad(values,ivec2(0))*${signed?'2':'2u'});}`);
   compute.bind(program);compute.bindImage(tex,0,0x88BA,internal);await compute.dispatch(1,1,1);integerPixels.push(Array.from(host.graphics.readTexturePixels(tex,type)));compute.deleteProgram(program);host.graphics.remove(tex,'Texture');
  }
  compute.deleteProgram(id);compute.deleteProgram(imageProgram);compute.deleteBuffer(buffer);host.graphics.remove(texture,'Texture');const live={programs:compute.programs.size,buffers:compute.buffers.size};compute.close();host.graphics.close();
  return {values,pixel,floatPixel,integerPixels,overflowRejected,live,elapsedMs:performance.now()-start};
 });
 assert.deepEqual(result.values,[107,106,105,104,103,102,101,100,115,114,113,112,111,110,109,108]);assert(result.pixel[0]===255&&Math.abs(result.pixel[1]-64)<=1&&Math.abs(result.pixel[2]-128)<=1&&result.pixel[3]===255);assert.deepEqual(result.floatPixel,[4,6,8,10]);assert.deepEqual(result.integerPixels,[[2,4,6,8],[2,4,6,8]]);assert(result.overflowRejected);assert.deepEqual(result.live,{programs:0,buffers:0});
 await mkdir('build/verification',{recursive:true});await writeFile('build/verification/webgpu-compute.json',JSON.stringify(result,null,2));console.log('WEBGPU_COMPUTE_VALIDATED '+JSON.stringify(result));
}finally{await browser.close();}
