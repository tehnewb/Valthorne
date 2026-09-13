// The engine's independent-pixel tracing kernels run as WebGL2 MRT passes.
// BVH construction, light transport, temporal history and denoising stay shared.
export class BrowserPathTracing {
 constructor(graphics){this.graphics=graphics;this.images=new Map();this.buffers=new Map();this.boundBuffer=0;this.programs=new Set();}
 supported(){const gl=this.graphics.context();return !!gl.getExtension('EXT_color_buffer_float')&&!!gl.getExtension('OES_texture_float_linear')&&gl.getParameter(gl.MAX_DRAW_BUFFERS)>=4&&gl.getParameter(gl.MAX_TEXTURE_IMAGE_UNITS)>=12;}
 compile(source){
  if(!this.supported())throw new Error('Path tracing requires WebGL2 float render targets, float filtering and four color attachments');
  const gl=this.graphics.context(),images=[],buffers=[];let helpers='',outputs=0;
  // The denoiser writes both destinations for every in-bounds fragment. Trace
  // history has conditional writes and must preserve the previous contents.
  const fullOutputs=source.includes('uniform int stepWidth, realtime;');
  source=source.replace(/#version[^\n]*/,'#version 300 es\nprecision highp float;precision highp int;precision highp sampler2D;precision highp sampler2DArray;')
   .replace(/layout\s*\(local_size[^;]*;/,'');
  source=source.replace(/layout\s*\(([^)]*)\)\s*uniform\s+(?:(readonly|writeonly)\s+)?image2D\s+(\w+)\s*;/g,(all,layout,qualifier,name)=>{
   const binding=Number(layout.match(/binding\s*=\s*(\d+)/)[1]),write=qualifier!=='readonly',location=write?outputs++:-1;
   const preserve=write&&!fullOutputs;
   images.push({binding,name,write,location,preserve});
   if(!write||preserve)helpers+=`vec4 load_${name}(ivec2 p){return texelFetch(pt_${name},p,0);}\n`;
   if(write)helpers+=`void store_${name}(ivec2 p,vec4 value){pt_out${location}=value;}\n`;
   return `${!write||preserve?`uniform sampler2D pt_${name};`:''}${write?`layout(location=${location}) out vec4 pt_out${location};`:''}`;
  });
  source=source.replace(/layout\s*\(([^)]*)\)\s*readonly\s+buffer\s+\w+\s*\{\s*(vec[24])\s+(\w+)\[\];\s*\};/g,(all,layout,type,name)=>{
   buffers.push({binding:Number(layout.match(/binding\s*=\s*(\d+)/)[1]),name,components:type==='vec2'?2:4});
   helpers+=`${type} buffer_${name}(int index){ivec2 size=textureSize(pt_${name},0);return texelFetch(pt_${name},ivec2(index%size.x,index/size.x),0)${type==='vec2'?'.xy':''};}\n`;
   return `uniform sampler2D pt_${name};`;
  });
  for(const image of images)source=source.replaceAll(`imageLoad(${image.name},`,`load_${image.name}(`).replaceAll(`imageLoad(${image.name}, `,`load_${image.name}(`).replaceAll(`imageStore(${image.name},`,`store_${image.name}(`).replaceAll(`imageStore(${image.name}, `,`store_${image.name}(`).replaceAll(`imageSize(${image.name})`,`textureSize(pt_${image.name},0)`);
  // Denoise sources include spaces after commas; the function replacement keeps them legal.
  for(const buffer of buffers)source=source.replace(new RegExp(`\\b${buffer.name}\\[([^\\]]+)\\]`,'g'),`buffer_${buffer.name}($1)`);
  source=source.replaceAll('gl_GlobalInvocationID.xy','gl_FragCoord.xy');
  const firstFunction=source.indexOf('const float PI');const fallback=source.indexOf('vec3 octDecode');const insertion=firstFunction>=0?firstFunction:fallback;
  if(insertion<0)throw new Error('Unknown path tracing kernel');source=source.slice(0,insertion)+helpers+source.slice(insertion);
  source=source.replace(/void main\(\)\s*\{/,`void main(){${images.filter(i=>i.preserve).map(i=>`pt_out${i.location}=load_${i.name}(ivec2(gl_FragCoord.xy));`).join('')}`);
  if(/imageLoad|imageStore|imageSize|\bimage2D\b|gl_GlobalInvocationID|\bshared\b|\bbarrier\s*\(/.test(source))throw new Error('Unsupported path kernel operation');
  const shaders=[],program=gl.createProgram();
  try{
   for(const [type,text]of [[gl.VERTEX_SHADER,'#version 300 es\nvoid main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0,1);}'],[gl.FRAGMENT_SHADER,source]]){
    const shader=gl.createShader(type);shaders.push(shader);gl.shaderSource(shader,text);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));gl.attachShader(program,shader);
   }
   gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
   const id=this.graphics.add(program,'Program');this.graphics.objects.get(id).pathPass={images,buffers,framebuffer:gl.createFramebuffer(),copyFramebuffer:gl.createFramebuffer(),vao:gl.createVertexArray(),copies:new Map()};this.programs.add(id);return id;
  }catch(error){gl.deleteProgram(program);throw error;}finally{for(const shader of shaders)gl.deleteShader(shader);}
 }
 upload(data){
  const gl=this.graphics.context(),buffer=this.graphics.objects.get(this.boundBuffer);if(!buffer)throw new Error('No path scene buffer bound');
  buffer.pathData=new Float32Array(data);if(buffer.pathTexture){gl.deleteTexture(buffer.pathTexture);buffer.pathTexture=null;}
 }
 bufferTexture(id,components){
  const gl=this.graphics.context(),buffer=this.graphics.objects.get(id);if(!buffer||!buffer.pathTexture&&!buffer.pathData)throw new Error('Path scene buffer is empty');
  if(!buffer.pathTexture){
   const texels=Math.max(1,Math.ceil(buffer.pathData.length/components)),width=Math.min(gl.getParameter(gl.MAX_TEXTURE_SIZE),texels),height=Math.ceil(texels/width);
   if(height>gl.getParameter(gl.MAX_TEXTURE_SIZE))throw new Error('Path scene exceeds texture storage limit');
   const pixels=new Float32Array(width*height*components);pixels.set(buffer.pathData);buffer.pathTexture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,buffer.pathTexture);
   gl.texImage2D(gl.TEXTURE_2D,0,components===2?gl.RG32F:gl.RGBA32F,width,height,0,components===2?gl.RG:gl.RGBA,gl.FLOAT,pixels);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);buffer.pathData=null;
  }return buffer.pathTexture;
 }
 dispatch(){
  const gl=this.graphics.context(),program=gl.getParameter(gl.CURRENT_PROGRAM),id=this.graphics.reverse.get(program),pass=this.graphics.objects.get(id)?.pathPass;if(!pass)throw new Error('No path tracing pass bound');
  const first=pass.images.find(i=>i.write),target=this.images.get(first.binding),meta=this.graphics.images.get(target);if(!meta)throw new Error('Missing path output texture');
  const width=meta.width,height=meta.height,attachments=[];let unit=1;
  gl.disable(gl.BLEND);gl.disable(gl.DEPTH_TEST);gl.disable(gl.CULL_FACE);gl.disable(gl.SCISSOR_TEST);gl.disable(gl.STENCIL_TEST);gl.disable(gl.RASTERIZER_DISCARD);gl.colorMask(true,true,true,true);
  const written=new Map(pass.images.filter(i=>i.preserve).map(i=>[this.images.get(i.binding),i.binding]));
  for(const image of pass.images.filter(i=>i.preserve)){
   const textureId=this.images.get(image.binding);let texture=textureId?this.graphics.get(textureId):null;
   // Inactive realtime history still needs a complete sampler in progressive mode.
   if(!texture)texture=this.graphics.get(target);
   if(image.write){
    let copy=pass.copies.get(image.binding);
    if(!copy||copy.width!==width||copy.height!==height){if(copy)gl.deleteTexture(copy.texture);const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);gl.texStorage2D(gl.TEXTURE_2D,1,gl.RGBA32F,width,height);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);copy={texture,width,height};pass.copies.set(image.binding,copy);}
    gl.bindFramebuffer(gl.READ_FRAMEBUFFER,pass.copyFramebuffer);gl.framebufferTexture2D(gl.READ_FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);gl.readBuffer(gl.COLOR_ATTACHMENT0);
    gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,pass.framebuffer);gl.framebufferTexture2D(gl.DRAW_FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,copy.texture,0);gl.drawBuffers([gl.COLOR_ATTACHMENT0]);
    gl.blitFramebuffer(0,0,width,height,0,0,width,height,gl.COLOR_BUFFER_BIT,gl.NEAREST);texture=copy.texture;
   }
  }
  for(const image of pass.images){
   if(image.write&&!image.preserve)continue;
   const textureId=this.images.get(image.binding);
   const texture=image.write?pass.copies.get(image.binding).texture:written.has(textureId)?pass.copies.get(written.get(textureId)).texture:textureId?this.graphics.get(textureId):pass.copies.get(first.binding).texture;
   gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,texture);gl.bindSampler(unit,null);gl.uniform1i(gl.getUniformLocation(program,'pt_'+image.name),unit++);
  }
  for(const buffer of pass.buffers){gl.activeTexture(gl.TEXTURE0+unit);gl.bindTexture(gl.TEXTURE_2D,this.bufferTexture(this.buffers.get(buffer.binding),buffer.components));gl.bindSampler(unit,null);gl.uniform1i(gl.getUniformLocation(program,'pt_'+buffer.name),unit++);}
  gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,pass.framebuffer);
  for(const image of pass.images.filter(i=>i.write)){const attachment=gl.COLOR_ATTACHMENT0+image.location;attachments.push(attachment);gl.framebufferTexture2D(gl.DRAW_FRAMEBUFFER,attachment,gl.TEXTURE_2D,this.graphics.get(this.images.get(image.binding)),0);}
  gl.drawBuffers(attachments);if(gl.checkFramebufferStatus(gl.DRAW_FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw new Error('Incomplete path framebuffer');
  gl.viewport(0,0,width,height);gl.bindVertexArray(pass.vao);gl.drawArrays(gl.TRIANGLES,0,3);
 }
 atlas(ids){
  const gl=this.graphics.context();if(ids.length>gl.getParameter(gl.MAX_ARRAY_TEXTURE_LAYERS))throw new Error('Too many path tracing textures');
  const texture=gl.createTexture(),framebuffer=gl.createFramebuffer(),vao=gl.createVertexArray(),program=gl.createProgram(),sampler=gl.createSampler(),shaders=[];
  const oldDraw=gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING),oldViewport=gl.getParameter(gl.VIEWPORT),oldProgram=gl.getParameter(gl.CURRENT_PROGRAM),oldVao=gl.getParameter(gl.VERTEX_ARRAY_BINDING),oldActive=gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0);
  const oldTexture=gl.getParameter(gl.TEXTURE_BINDING_2D),oldArray=gl.getParameter(gl.TEXTURE_BINDING_2D_ARRAY),oldSampler=gl.getParameter(gl.SAMPLER_BINDING),oldMask=gl.getParameter(gl.COLOR_WRITEMASK),caps=[gl.BLEND,gl.DEPTH_TEST,gl.CULL_FACE,gl.SCISSOR_TEST,gl.STENCIL_TEST,gl.RASTERIZER_DISCARD].map(c=>[c,gl.isEnabled(c)]);
  try{
   gl.bindTexture(gl.TEXTURE_2D_ARRAY,texture);gl.texStorage3D(gl.TEXTURE_2D_ARRAY,1,gl.RGBA8,512,512,Math.max(1,ids.length));gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_WRAP_S,gl.REPEAT);gl.texParameteri(gl.TEXTURE_2D_ARRAY,gl.TEXTURE_WRAP_T,gl.REPEAT);
   if(ids.length){
    for(const [type,source]of [[gl.VERTEX_SHADER,'#version 300 es\nout vec2 uv;void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));uv=p;gl_Position=vec4(p*2.-1.,0,1);}'],[gl.FRAGMENT_SHADER,'#version 300 es\nprecision highp float;uniform sampler2D source;in vec2 uv;out vec4 color;void main(){color=texture(source,uv);}']]){const shader=gl.createShader(type);shaders.push(shader);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));gl.attachShader(program,shader);}
    gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
    gl.useProgram(program);gl.uniform1i(gl.getUniformLocation(program,'source'),0);gl.bindVertexArray(vao);gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,framebuffer);gl.viewport(0,0,512,512);gl.colorMask(true,true,true,true);for(const[c]of caps)gl.disable(c);
    gl.samplerParameteri(sampler,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.samplerParameteri(sampler,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.samplerParameteri(sampler,gl.TEXTURE_WRAP_S,gl.REPEAT);gl.samplerParameteri(sampler,gl.TEXTURE_WRAP_T,gl.REPEAT);gl.bindSampler(0,sampler);
    for(let i=0;i<ids.length;i++){gl.bindTexture(gl.TEXTURE_2D,this.graphics.get(ids[i]));gl.framebufferTextureLayer(gl.DRAW_FRAMEBUFFER,gl.COLOR_ATTACHMENT0,texture,0,i);if(gl.checkFramebufferStatus(gl.DRAW_FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw new Error('Incomplete path atlas');gl.drawArrays(gl.TRIANGLES,0,3);}
   }
   return this.graphics.add(texture,'Texture');
  }catch(error){gl.deleteTexture(texture);throw error;}
  finally{for(const shader of shaders)gl.deleteShader(shader);gl.deleteProgram(program);gl.deleteSampler(sampler);gl.deleteFramebuffer(framebuffer);gl.deleteVertexArray(vao);gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,oldDraw);gl.viewport(...oldViewport);gl.useProgram(oldProgram);gl.bindVertexArray(oldVao);gl.bindTexture(gl.TEXTURE_2D,oldTexture);gl.bindTexture(gl.TEXTURE_2D_ARRAY,oldArray);gl.bindSampler(0,oldSampler);gl.activeTexture(oldActive);gl.colorMask(...oldMask);for(const[c,v]of caps)gl[v?'enable':'disable'](c);}
 }
 disposeProgram(id){const gl=this.graphics.context(),pass=this.graphics.objects.get(id)?.pathPass;if(!pass)return;for(const copy of pass.copies.values())gl.deleteTexture(copy.texture);gl.deleteFramebuffer(pass.framebuffer);gl.deleteFramebuffer(pass.copyFramebuffer);gl.deleteVertexArray(pass.vao);this.programs.delete(id);}
 begin(){
  const gl=this.graphics.context(),state={draw:gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING),read:gl.getParameter(gl.READ_FRAMEBUFFER_BINDING),viewport:gl.getParameter(gl.VIEWPORT),program:gl.getParameter(gl.CURRENT_PROGRAM),vao:gl.getParameter(gl.VERTEX_ARRAY_BINDING),active:gl.getParameter(gl.ACTIVE_TEXTURE),mask:gl.getParameter(gl.COLOR_WRITEMASK),textures:[],caps:[gl.DEPTH_TEST,gl.BLEND,gl.CULL_FACE,gl.SCISSOR_TEST,gl.STENCIL_TEST,gl.RASTERIZER_DISCARD].map(c=>[c,gl.isEnabled(c)])};
  for(let i=0;i<12;i++){gl.activeTexture(gl.TEXTURE0+i);state.textures.push([gl.getParameter(gl.TEXTURE_BINDING_2D),gl.getParameter(gl.TEXTURE_BINDING_2D_ARRAY),gl.getParameter(gl.SAMPLER_BINDING)]);gl.bindSampler(i,null);}gl.activeTexture(gl.TEXTURE0);this.state=state;return state;
 }
 present(){const gl=this.graphics.context(),s=this.state;gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,s.draw);gl.viewport(...s.viewport);gl.colorMask(...s.mask);const scissor=s.caps.find(([c])=>c===gl.SCISSOR_TEST)[1];if(scissor)gl.enable(gl.SCISSOR_TEST);else gl.disable(gl.SCISSOR_TEST);}
 end(s){const gl=this.graphics.context();gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,s.draw);gl.bindFramebuffer(gl.READ_FRAMEBUFFER,s.read);gl.viewport(...s.viewport);gl.colorMask(...s.mask);for(const[c,v]of s.caps)gl[v?'enable':'disable'](c);for(let i=0;i<s.textures.length;i++){gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,s.textures[i][0]);gl.bindTexture(gl.TEXTURE_2D_ARRAY,s.textures[i][1]);gl.bindSampler(i,s.textures[i][2]);}gl.activeTexture(s.active);gl.useProgram(s.program);gl.bindVertexArray(s.vao);this.state=null;}
}
