// Real WebGPU compute. glslang and Tint compile existing GLSL into WGSL lazily.
let compilers;
async function loadCompilers(){
 if(!compilers)compilers=(async()=>{
  const {default:initialize}=await import('./vendor/glslang/glslang.js');
  const glslang=await initialize();
  await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='vendor/twgsl/twgsl.js';script.onload=resolve;script.onerror=()=>reject(new Error('Tint could not load'));document.head.append(script);});
  return {glslang,tint:await globalThis.twgsl('vendor/twgsl/twgsl.wasm')};
 })().catch(error=>{compilers=null;throw error;});
 return compilers;
}

export class BrowserCompute {
 static async create(host){
  if(!navigator.gpu)return new BrowserCompute(host,null);
  const adapter=await navigator.gpu.requestAdapter();
  if(!adapter)return new BrowserCompute(host,null);
  const device=await adapter.requestDevice();return new BrowserCompute(host,device);
 }
 constructor(host,device){this.host=host;this.device=device;this.programs=new Map();this.buffers=new Map();this.images=new Map();this.bindings=new Map();this.next=1;this.current=0;this.closed=false;this.dispatches=0;if(device)device.lost.then(info=>{this.lost=info.message;});}
 supported(){return !!this.device&&!this.closed&&!this.lost;}
 async compile(source){
  if(!this.supported())throw new Error('WebGPU compute is unavailable on this browser/device');
  const {glslang,tint}=await loadCompilers();
  const prepared=this.prepare(source),spirv=glslang.compileGLSL(prepared.source,'compute',false),wgsl=tint.convertSpirV2WGSL(spirv);
  const module=this.device.createShaderModule({code:wgsl}),info=await module.getCompilationInfo();
  const errors=info.messages.filter(m=>m.type==='error');if(errors.length)throw new Error(errors.map(m=>m.message).join('\n'));
  const entryPoint=wgsl.match(/@compute\b[\s\S]*?\bfn\s+(\w+)\s*\(/)?.[1];
  if(!entryPoint)throw new Error('Shader has no compute entry point: '+wgsl);
  const pipeline=await this.device.createComputePipelineAsync({layout:'auto',compute:{module,entryPoint}});
  // Automatic layouts omit declarations removed by shader optimization.
  const activeBindings=new Set([...wgsl.matchAll(/@binding\(\s*(\d+)\s*\)/g)].map(match=>Number(match[1])));
  prepared.images=prepared.images.filter(entry=>activeBindings.has(entry.gpuBinding));
  prepared.buffers=prepared.buffers.filter(entry=>activeBindings.has(entry.gpuBinding));
  prepared.activeUniforms=activeBindings.has(31);
  const id=this.next++,size=Math.max(16,prepared.uniformBytes),uniform=this.device.createBuffer({size,usage:GPUBufferUsage.UNIFORM|GPUBufferUsage.COPY_DST});
  this.programs.set(id,{pipeline,uniform,data:new ArrayBuffer(size),...prepared,wgsl});return id;
 }
 prepare(source){
  if(typeof source!=='string')throw new Error('Compute source is required');
  source=source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g,'').replace(/#version[^\n]*/,'#version 450');
  const images=[],buffers=[],uniforms=new Map();let uniformBytes=0;
  source=source.replace(/layout\s*\(([^)]*)\)\s*((?:(?:readonly|writeonly|coherent|restrict|volatile)\s+)*)buffer\s+(\w+)\s*\{/g,(all,layout,qualifier,name)=>{
   const binding=Number(layout.match(/binding\s*=\s*(\d+)/)?.[1]);if(!Number.isInteger(binding))throw new Error('Storage buffers require an explicit binding');
   buffers.push({binding,gpuBinding:binding+16,readonly:qualifier.includes('readonly')});
   return `layout(std430,set=0,binding=${binding+16}) ${qualifier}buffer ${name} {`;
  });
  source=source.replace(/layout\s*\(([^)]*)\)\s*(?:uniform\s+)?((?:(?:readonly|writeonly|coherent|restrict|volatile)\s+)*)(?:uniform\s+)?([iu]?image2D)\s+(\w+)\s*;/g,(all,layout,qualifier,type,name)=>{
   const binding=Number(layout.match(/binding\s*=\s*(\d+)/)?.[1]),format=layout.split(',').map(s=>s.trim()).find(s=>!s.includes('='));
   if(!Number.isInteger(binding))throw new Error('Storage images require an explicit binding');
   const bufferImage=!qualifier.includes('readonly')&&!qualifier.includes('writeonly');
   images.push({binding,gpuBinding:binding,name,format,readonly:qualifier.includes('readonly'),bufferImage});
   if(bufferImage){
    const pixelType={rgba8:'uint',rgba32f:'vec4',rgba32ui:'uvec4',rgba32i:'ivec4'}[format];
    if(!pixelType)throw new Error('Unsupported read/write image format: '+format);
    const valueType=format==='rgba8'?'vec4':pixelType;
    const element=`vt_pixels_${name}[p.y*vt_size_${name}.x+p.x]`;
    return `layout(std430,set=0,binding=${binding}) buffer ValthorneImage_${name}{${pixelType} vt_pixels_${name}[];};
uniform ivec2 vt_size_${name};
${valueType} vt_load_${name}(ivec2 p){return ${format==='rgba8'?`unpackUnorm4x8(${element})`:element};}
void vt_store_${name}(ivec2 p,${valueType} value){${element}=${format==='rgba8'?'packUnorm4x8(value)':'value'};}`;
   }
   return `layout(${format},set=0,binding=${binding}) uniform ${qualifier}${type} ${name};`;
  });
  // Tint's SPIR-V reader predates read/write storage textures. A storage buffer
  // preserves within-dispatch reads and writes without splitting image history.
  for(const image of images.filter(entry=>entry.bufferImage)){
   source=source.replace(new RegExp(`imageLoad\\s*\\(\\s*${image.name}\\s*,`,'g'),`vt_load_${image.name}(`)
    .replace(new RegExp(`imageStore\\s*\\(\\s*${image.name}\\s*,`,'g'),`vt_store_${image.name}(`)
    .replace(new RegExp(`imageSize\\s*\\(\\s*${image.name}\\s*\\)`,'g'),`vt_size_${image.name}`);
  }
  if(/\b(?:memoryBarrier\w*|groupMemoryBarrier)\s*\(/.test(source))throw new Error('Standalone GLSL memory barriers are unsupported by the pinned compiler; workgroup barrier() is supported');
  const declarations=[];
  source=source.replace(/\buniform\s+(float|int|uint|bool|[biu]?vec[234]|mat[234])\s+([^;]+);/g,(all,type,names)=>{
   for(const name of names.split(',').map(s=>s.trim())){
    if(!/^\w+$/.test(name))throw new Error('Compute uniform arrays/initializers require an explicit uniform block');
    const components=type.startsWith('mat')?Number(type.at(-1))**2:/vec/.test(type)?Number(type.at(-1)):1;
    const align=type.startsWith('mat')||components>=3?16:components===2?8:4;
    uniformBytes=Math.ceil(uniformBytes/align)*align;uniforms.set(name,{type,components,offset:uniformBytes});declarations.push(`${type} ${name};`);uniformBytes+=type.startsWith('mat')?Number(type.at(-1))*16:components*4;
   }return '';
  });
  if(declarations.length)source=source.replace(/(#version[^\n]*\n)/,`$1layout(std140,set=0,binding=31) uniform ValthorneUniforms {${declarations.join('')}};\n`);
  return {source,images,buffers,uniforms,uniformBytes:Math.ceil(uniformBytes/16)*16};
 }
 program(id=this.current){const p=this.programs.get(id);if(!p)throw new Error('Compute program is closed or unbound');return p;}
 bind(id){if(id)this.program(id);this.current=id;}
 uniform(id,name,values){const p=this.program(id),entry=p.uniforms.get(name);if(!entry)return;const view=new DataView(p.data);if(values.length!==entry.components)throw new Error('Incorrect compute uniform size');for(let i=0;i<values.length;i++){const offset=entry.offset+(entry.type.startsWith('mat')?Math.floor(i/Number(entry.type.at(-1)))*16+i%Number(entry.type.at(-1))*4:i*4);view[entry.type==='int'||entry.type==='bool'||entry.type.startsWith('ivec')||entry.type.startsWith('bvec')?'setInt32':entry.type==='uint'||entry.type.startsWith('uvec')?'setUint32':'setFloat32'](offset,values[i],true);}}
 createBuffer(size){if(!this.supported())throw new Error('WebGPU compute is unavailable');if(!Number.isSafeInteger(size)||size<4||size>this.device.limits.maxStorageBufferBindingSize)throw new Error('Invalid storage buffer size');const id=this.next++,buffer=this.device.createBuffer({size:Math.ceil(size/4)*4,usage:GPUBufferUsage.STORAGE|GPUBufferUsage.COPY_SRC|GPUBufferUsage.COPY_DST});this.buffers.set(id,{buffer,size});return id;}
 updateBuffer(id,offset,bytes){const entry=this.buffers.get(id),size=bytes?.byteLength;if(!entry||!Number.isSafeInteger(offset)||offset<0||offset%4||!Number.isSafeInteger(size)||size%4||offset+size>entry.size)throw new Error('Invalid storage buffer upload');this.device.queue.writeBuffer(entry.buffer,offset,bytes);}
 bindBuffer(id,binding){if(id&&!this.buffers.has(id))throw new Error('Invalid storage buffer');if(id)this.bindings.set(binding,id);else this.bindings.delete(binding);}
 bindImage(id,binding,access,format){if(id)this.images.set(binding,{id,access,format});else this.images.delete(binding);}
 async dispatch(x,y,z){
  const p=this.program();for(const n of [x,y,z])if(!Number.isInteger(n)||n<0||n>this.device.limits.maxComputeWorkgroupsPerDimension)throw new Error('Invalid compute dispatch size');
  if(!x||!y||!z)return;
  const resources=[],temporary=[];let encoder;
  const boundImages=new Set();
  for(const entry of p.images){const image=this.images.get(entry.binding);if(image&&boundImages.has(image.id))throw new Error('Aliasing an image across compute bindings is unsupported');if(image)boundImages.add(image.id);}
  this.device.pushErrorScope('validation');
  try{
   for(const entry of p.buffers){const buffer=this.buffers.get(this.bindings.get(entry.binding));if(!buffer)throw new Error(`No storage buffer at binding ${entry.binding}`);resources.push({binding:entry.gpuBinding,resource:{buffer:buffer.buffer}});}
   for(const entry of p.images){const image=this.images.get(entry.binding);if(!image)throw new Error(`No image at binding ${entry.binding}`);const transfer=this.uploadImage(image,entry);temporary.push(transfer);if(transfer.buffer)this.uniform(this.current,'vt_size_'+entry.name,[transfer.width,transfer.height]);resources.push({binding:entry.gpuBinding,resource:transfer.buffer?{buffer:transfer.buffer}:transfer.texture.createView()});}
   if(p.activeUniforms){this.device.queue.writeBuffer(p.uniform,0,p.data);resources.push({binding:31,resource:{buffer:p.uniform}});}
   const group=this.device.createBindGroup({layout:p.pipeline.getBindGroupLayout(0),entries:resources});encoder=this.device.createCommandEncoder();const pass=encoder.beginComputePass();pass.setPipeline(p.pipeline);pass.setBindGroup(0,group);pass.dispatchWorkgroups(x,y,z);pass.end();
   for(const image of temporary)if(!image.readonly){image.readback=this.device.createBuffer({size:image.pitch*image.height,usage:GPUBufferUsage.COPY_DST|GPUBufferUsage.MAP_READ});if(image.buffer)encoder.copyBufferToBuffer(image.buffer,0,image.readback,0,image.pitch*image.height);else encoder.copyTextureToBuffer({texture:image.texture},{buffer:image.readback,bytesPerRow:image.pitch},[image.width,image.height]);}
   this.device.queue.submit([encoder.finish()]);
   for(const image of temporary)if(image.readback){await image.readback.mapAsync(GPUMapMode.READ);this.downloadImage(image);image.readback.unmap();}
   this.dispatches++;
  }finally{for(const image of temporary){image.readback?.destroy();image.texture?.destroy();image.buffer?.destroy();}const error=await this.device.popErrorScope();if(error)throw new Error('Compute dispatch validation: '+error.message);}
 }
 uploadImage(image,entry){
  const graphics=this.host.graphics,gl=graphics.context(),object=graphics.objects.get(image.id),meta=graphics.images.get(image.id);
  const width=meta?.width||object?.width,height=meta?.height||object?.height;
  if(!width||!height)throw new Error('Compute image dimensions are unavailable');
  const formats={rgba8:['rgba8unorm',4,gl.UNSIGNED_BYTE],rgba32f:['rgba32float',16,gl.FLOAT],rgba32ui:['rgba32uint',16,gl.UNSIGNED_INT],rgba32i:['rgba32sint',16,gl.INT]},format=formats[entry.format];
  if(!format)throw new Error('Unsupported WebGPU image format: '+entry.format);
  if(format[2]===gl.FLOAT)gl.getExtension('EXT_color_buffer_float');
  if(entry.bufferImage){
   const size=width*height*format[1];if(size>this.device.limits.maxStorageBufferBindingSize)throw new Error('Compute image exceeds storage buffer limit');
   const bytes=image.access===0x88B9?null:graphics.readTexturePixels(image.id,format[2]);
   const buffer=this.device.createBuffer({size,usage:GPUBufferUsage.STORAGE|GPUBufferUsage.COPY_SRC|GPUBufferUsage.COPY_DST});
   if(bytes)this.device.queue.writeBuffer(buffer,0,bytes);
   return {buffer,id:image.id,width,height,format,readonly:false,pitch:width*format[1]};
  }
  const texture=this.device.createTexture({size:[width,height],format:format[0],usage:GPUTextureUsage.STORAGE_BINDING|GPUTextureUsage.COPY_SRC|GPUTextureUsage.COPY_DST});
  const transfer={texture,id:image.id,width,height,format,readonly:entry.readonly,pitch:Math.ceil(width*format[1]/256)*256};
  if(image.access!==0x88B9){const bytes=graphics.readTexturePixels(image.id,format[2]);this.device.queue.writeTexture({texture},bytes,{bytesPerRow:width*format[1]},[width,height]);}
  return transfer;
 }
 downloadImage(image){const bytes=new Uint8Array(image.readback.getMappedRange()),row=image.width*image.format[1],packed=new Uint8Array(row*image.height);for(let y=0;y<image.height;y++)packed.set(bytes.subarray(y*image.pitch,y*image.pitch+row),y*row);this.host.graphics.writeTexturePixels(image.id,image.width,image.height,image.format[2],packed);}
 async readBuffer(id){const entry=this.buffers.get(id);if(!entry)throw new Error('Storage buffer is closed');const readback=this.device.createBuffer({size:entry.buffer.size,usage:GPUBufferUsage.COPY_DST|GPUBufferUsage.MAP_READ});try{const encoder=this.device.createCommandEncoder();encoder.copyBufferToBuffer(entry.buffer,0,readback,0,entry.buffer.size);this.device.queue.submit([encoder.finish()]);await readback.mapAsync(GPUMapMode.READ);return new Uint8Array(readback.getMappedRange()).slice(0,entry.size);}finally{readback.destroy();}}
 deleteBuffer(id){this.buffers.get(id)?.buffer.destroy();this.buffers.delete(id);for(const [binding,value]of this.bindings)if(value===id)this.bindings.delete(binding);}
 deleteProgram(id){this.programs.get(id)?.uniform.destroy();this.programs.delete(id);if(this.current===id)this.current=0;}
 close(){if(this.closed)return;this.closed=true;for(const id of this.programs.keys())this.deleteProgram(id);for(const id of this.buffers.keys())this.deleteBuffer(id);this.images.clear();this.bindings.clear();this.device?.destroy();}
}
