import {drawingCanvas} from './canvas.js';
import { BrowserPathTracing } from './pathtrace.js';
const getPixelRatio = (host) => {
    const ratio = host.pixelRatio || Number(window.devicePixelRatio) || 1;
    return Math.max(1, Math.min(ratio, 3));
};
// Internal WebGL backend for the unchanged engine batching and shader code.
// A transparent compositing surface keeps Filament's private GL state isolated.
export class BrowserGraphics {
    constructor(host){this.host=host;this.objects=new Map();this.reverse=new WeakMap();this.next=1;this.uniforms=new Map();this.images=new Map();this.closed=false;this.pathTracing=new BrowserPathTracing(this);}
    context(){
        if(this.closed)throw new Error('Graphics backend is closed');
        if(!this.gl){
            const canvas=this.canvas=document.createElement('canvas');canvas.id='valthorne-2d';
            canvas.style.cssText='position:absolute;inset:0;pointer-events:none;width:100%;height:100%;';
            document.querySelector('#scene').after(canvas);
            this.gl=canvas.getContext('webgl2',{alpha:true,premultipliedAlpha:true,antialias:false});
            if(!this.gl){canvas.remove();throw new Error('WebGL 2 is required for 2D rendering');}
            this.resize();
        }
        return this.gl;
    }
    resize(){if(!this.gl)return;const ratio=getPixelRatio(this.host),w=Math.max(1,Math.round(this.host.platform.window.width*ratio)),h=Math.max(1,Math.round(this.host.platform.window.height*ratio));if(this.canvas.width!==w||this.canvas.height!==h){this.canvas.width=w;this.canvas.height=h;this.gl.viewport(0,0,w,h);}}
    add(object,kind){if(!object)throw new Error('Could not allocate '+kind);const id=this.next++;this.objects.set(id,{object,kind});this.reverse.set(object,id);return id;}
    get(id){if(id===0||id===-1)return null;const item=this.objects.get(id);if(!item)throw new Error('Invalid or disposed graphics handle '+id);return item.object;}
    create(kind){return this.add(this.context()['create'+kind](),kind);}
    readTexturePixels(id,type){
        const gl=this.context(),meta=this.images.get(id);if(!meta)throw new Error('Unknown texture dimensions');
        const framebuffer=gl.createFramebuffer(),old=gl.getParameter(gl.READ_FRAMEBUFFER_BINDING),pack=gl.getParameter(gl.PIXEL_PACK_BUFFER_BINDING);
        const names=[gl.PACK_ALIGNMENT,gl.PACK_ROW_LENGTH,gl.PACK_SKIP_PIXELS,gl.PACK_SKIP_ROWS],values=names.map(n=>gl.getParameter(n));
        const Type=type===gl.FLOAT?Float32Array:type===gl.UNSIGNED_INT?Uint32Array:type===gl.INT?Int32Array:Uint8Array,pixels=new Type(meta.width*meta.height*4);
        try{gl.bindFramebuffer(gl.READ_FRAMEBUFFER,framebuffer);gl.framebufferTexture2D(gl.READ_FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,this.get(id),0);if(gl.checkFramebufferStatus(gl.READ_FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw new Error('Texture is not readable');gl.bindBuffer(gl.PIXEL_PACK_BUFFER,null);names.forEach((n,i)=>gl.pixelStorei(n,i===0?1:0));gl.readPixels(0,0,meta.width,meta.height,type===gl.INT||type===gl.UNSIGNED_INT?gl.RGBA_INTEGER:gl.RGBA,type,pixels);return pixels;}
        finally{gl.bindFramebuffer(gl.READ_FRAMEBUFFER,old);gl.bindBuffer(gl.PIXEL_PACK_BUFFER,pack);names.forEach((n,i)=>gl.pixelStorei(n,values[i]));gl.deleteFramebuffer(framebuffer);}
    }
    writeTexturePixels(id,width,height,type,bytes){
        const gl=this.context(),old=gl.getParameter(gl.TEXTURE_BINDING_2D),unpack=gl.getParameter(gl.PIXEL_UNPACK_BUFFER_BINDING);
        const names=[gl.UNPACK_ALIGNMENT,gl.UNPACK_ROW_LENGTH,gl.UNPACK_SKIP_PIXELS,gl.UNPACK_SKIP_ROWS,gl.UNPACK_FLIP_Y_WEBGL,gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL],values=names.map(n=>gl.getParameter(n));
        const Type=type===gl.FLOAT?Float32Array:type===gl.UNSIGNED_INT?Uint32Array:type===gl.INT?Int32Array:Uint8Array;
        try{gl.bindTexture(gl.TEXTURE_2D,this.get(id));gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,null);names.forEach((n,i)=>gl.pixelStorei(n,i===0?1:0));gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,width,height,type===gl.INT||type===gl.UNSIGNED_INT?gl.RGBA_INTEGER:gl.RGBA,type,new Type(bytes.buffer,bytes.byteOffset,bytes.byteLength/Type.BYTES_PER_ELEMENT));const meta=this.images.get(id);if(meta){meta.version++;if(type===gl.UNSIGNED_BYTE)meta.pixels=bytes.slice();}}
        finally{gl.bindTexture(gl.TEXTURE_2D,old);gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,unpack);names.forEach((n,i)=>gl.pixelStorei(n,values[i]));}
    }
    remove(id,kind){const item=this.objects.get(id);if(!item)return;if(item.kind!==kind)throw new Error('Graphics handle type mismatch');
        if(item.pathPass)this.pathTracing.disposeProgram(id);
        if(item.pathTexture)this.context().deleteTexture(item.pathTexture);
        if(kind==='Program'){for(const uniform of this.uniforms.get(id)?.values()||[])this.objects.delete(uniform);this.uniforms.delete(id);}
        if(kind==='Texture'){const image=this.images.get(id);if(image?.native)this.host.engine.destroyTexture(image.native);this.images.delete(id);}
        this.context()['delete'+kind](item.object);this.objects.delete(id);this.reverse.delete(item.object);
    }
    parameter(name){const gl=this.context();if(name===0x8C2A)return this.textureBufferBinding||0;if(name===0x8C2B){const size=gl.getParameter(gl.MAX_TEXTURE_SIZE);return size*size;}if(name===0x8C2C)name=gl.TEXTURE_BINDING_2D;const value=gl.getParameter(name);return typeof value==='object'?(value?this.reverse.get(value)||0:0):+value;}
    bindTexture(target,id){const gl=this.context();gl.bindTexture(target===0x8C2A?gl.TEXTURE_2D:target,this.get(id));}
    bindBuffer(target,id){if(target===0x8C2A){if(id)this.get(id);this.textureBufferBinding=id;return;}this.context().bindBuffer(target,this.get(id));}
    bufferData(target,data,usage){if(target===0x8C2A){const buffer=this.objects.get(this.textureBufferBinding);if(!buffer)throw new Error('No lighting data buffer bound');buffer.data=data.slice();return;}this.context().bufferData(target,data,usage);}
    textureBuffer(target,format,bufferId){
        if(target!==0x8C2A)throw new Error('Unsupported texture-buffer target');
        const gl=this.context(),data=this.objects.get(bufferId)?.data;if(!data)throw new Error('Lighting data buffer is empty');
        const integer=format===gl.R32I;if(!integer&&format!==gl.RGBA32F)throw new Error('Unsupported lighting texture format');
        const components=integer?1:4,texels=Math.max(1,Math.ceil(data.length/components)),width=Math.min(gl.getParameter(gl.MAX_TEXTURE_SIZE),texels),height=Math.ceil(texels/width);
        const texture=this.objects.get(this.reverse.get(gl.getParameter(gl.TEXTURE_BINDING_2D)));if(!texture)throw new Error('No lighting texture bound');
        const Type=integer?Int32Array:Float32Array,size=width*height*components;
        if(!(texture.staging instanceof Type)||texture.staging.length!==size)texture.staging=new Type(size);
        texture.staging.set(data);texture.staging.fill(0,data.length);
        this.upload(gl.TEXTURE_2D,0,format,width,height,0,integer?gl.RED_INTEGER:gl.RGBA,integer?gl.INT:gl.FLOAT,texture.staging);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    }
    uniform(program,name){let cache=this.uniforms.get(program);if(!cache)this.uniforms.set(program,cache=new Map());if(cache.has(name))return cache.get(name);const object=this.context().getUniformLocation(this.get(program),name);const id=object?this.add(object,'Uniform'):-1;cache.set(name,id);return id;}
    blend(source,destination){const gl=this.context();gl.blendFuncSeparate(source,destination,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);}
    shaderSource(id,source){
        // The engine's GLSL 330 stages use the WebGL 2-compatible feature subset.
        // Unsupported desktop-only GLSL features still produce compiler diagnostics.
        let bufferHelpers='';const buffers=new Map(),types=new Set();
        source=source.replace(/uniform\s+([iu]?sampler)Buffer\s+(\w+)\s*;/g,(_,type,name)=>{types.add(type);buffers.set(name,type);return `uniform ${type}2D ${name};`;});
        // Distinct names avoid an ANGLE/D3D11 compiler collision when opaque
        // float/integer sampler parameters lower to the same internal signature.
        for(const [name,type] of buffers)source=source.replace(new RegExp(`texelFetch\\(\\s*${name}\\s*,`,'g'),`valthorneBufferFetch_${type}(${name},`);
        for(const type of types){const result=type==='isampler'?'ivec4':type==='usampler'?'uvec4':'vec4';bufferHelpers+=`${result} valthorneBufferFetch_${type}(${type}2D data,int index){int width=textureSize(data,0).x;return texelFetch(data,ivec2(index%width,index/width),0);}\n`;}
        source=source.replace(/^\s*#version\s+(?:330|430)\s+core\s*/,'#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DShadow;\nprecision highp usampler2D;\nprecision highp isampler2D;\n'+bufferHelpers);
        this.context().shaderSource(this.get(id),source);
    }
    upload(target,level,internal,width,height,border,format,type,pixels){
        const gl=this.context();if(pixels===null&&internal===gl.RGBA16F&&!gl.getExtension('EXT_color_buffer_float'))throw new Error('HDR 2D lighting requires EXT_color_buffer_float');
        // Desktop permits a conversion type for null allocations; WebGL requires
        // a legal storage-format/type pair even when no pixels are supplied.
        if(pixels===null&&internal===gl.RGBA8&&type===gl.FLOAT)type=gl.UNSIGNED_BYTE;
        if(pixels===null&&internal===gl.DEPTH_COMPONENT24&&type===gl.FLOAT)type=gl.UNSIGNED_INT;
        gl.texImage2D(target,level,internal,width,height,border,format,type,pixels);
        if(level===0){const id=this.reverse.get(gl.getParameter(gl.TEXTURE_BINDING_2D));const old=this.images.get(id);if(old?.native)this.host.engine.destroyTexture(old.native);
            // Retain pixels only for cross-backend texture sharing. Render targets have no CPU image.
            this.images.set(id,{width,height,pixels:format===gl.RGBA&&type===gl.UNSIGNED_BYTE?pixels?.slice():undefined,min:gl.NEAREST,mag:gl.NEAREST,version:(old?.version||0)+1});}
    }
    filter(target,name,value){const gl=this.context();gl.texParameteri(target,name,value);const image=this.images.get(this.reverse.get(gl.getParameter(gl.TEXTURE_BINDING_2D)));if(image){if(name===gl.TEXTURE_MIN_FILTER)image.min=value;if(name===gl.TEXTURE_MAG_FILTER)image.mag=value;image.version++;}}
    filament(id){
        const image=this.images.get(id);if(!image?.pixels)throw new Error('Only uploaded image textures can be shared with Filament; render-target sharing is not implemented');
        const F=this.host.F,e=this.host.engine;
        // Shared engine materials perform their own sRGB-to-linear conversion.
        if(!image.native){
            // v1.75 DriverEnums.h: UPLOADABLE | SAMPLEABLE | GEN_MIPMAPPABLE.
            // The shipped web typings omit the last flag (0x0200).
            const levels=1+Math.floor(Math.log2(Math.max(image.width,image.height)));
            const texture=F.Texture.Builder().width(image.width).height(image.height).levels(levels).usage(0x0218).sampler(F.Texture$Sampler.SAMPLER_2D).format(F.Texture$InternalFormat.RGBA8).build(e);
            try{texture.setImage(e,0,F.PixelBuffer(image.pixels,F.PixelDataFormat.RGBA,F.PixelDataType.UBYTE));if(levels>1)texture.generateMipmaps(e);image.native=texture;}
            catch(error){e.destroyTexture(texture);throw new Error('Filament texture upload failed: '+String(error));}
        }
        return image;
    }
    clear(r,g,b,a,depth){const gl=this.context();gl.clearColor(r,g,b,a);const writes=depth?gl.getParameter(gl.DEPTH_WRITEMASK):false;if(depth)gl.depthMask(true);gl.clear(gl.COLOR_BUFFER_BIT|(depth?gl.DEPTH_BUFFER_BIT:0));if(depth)gl.depthMask(writes);}
    snapshot(){const gl=this.context(),names=['DEPTH_WRITEMASK','DEPTH_FUNC','CULL_FACE_MODE','FRONT_FACE','BLEND_SRC_RGB','BLEND_DST_RGB','BLEND_SRC_ALPHA','BLEND_DST_ALPHA','BLEND_EQUATION_RGB','BLEND_EQUATION_ALPHA','CURRENT_PROGRAM','VERTEX_ARRAY_BINDING','ARRAY_BUFFER_BINDING','ACTIVE_TEXTURE'];const s={};for(const name of names)s[name]=gl.getParameter(gl[name]);s.capabilities=[gl.DEPTH_TEST,gl.BLEND,gl.CULL_FACE].map(cap=>[cap,gl.isEnabled(cap)]);s.textures=[];s.samplers=[];for(let i=0;i<5;i++){gl.activeTexture(gl.TEXTURE0+i);s.textures.push(gl.getParameter(gl.TEXTURE_BINDING_2D));s.samplers.push(gl.getParameter(gl.SAMPLER_BINDING));}gl.activeTexture(s.ACTIVE_TEXTURE);return s;}
    restore(s){const gl=this.context();for(const [cap,enabled] of s.capabilities)enabled?gl.enable(cap):gl.disable(cap);gl.depthMask(s.DEPTH_WRITEMASK);gl.depthFunc(s.DEPTH_FUNC);gl.cullFace(s.CULL_FACE_MODE);gl.frontFace(s.FRONT_FACE);gl.blendFuncSeparate(s.BLEND_SRC_RGB,s.BLEND_DST_RGB,s.BLEND_SRC_ALPHA,s.BLEND_DST_ALPHA);gl.blendEquationSeparate(s.BLEND_EQUATION_RGB,s.BLEND_EQUATION_ALPHA);gl.useProgram(s.CURRENT_PROGRAM);gl.bindVertexArray(s.VERTEX_ARRAY_BINDING);gl.bindBuffer(gl.ARRAY_BUFFER,s.ARRAY_BUFFER_BINDING);for(let i=0;i<5;i++){gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,s.textures[i]);gl.bindSampler(i,s.samplers[i]);}gl.activeTexture(s.ACTIVE_TEXTURE);}
    clearWindow(r,g,b,a,depth){
        this.resize();if(this.gl){this.clear(r,g,b,a,depth);if(this.gl.getParameter(this.gl.FRAMEBUFFER_BINDING))return;}
        this.host.renderer.setClearOptions({clearColor:[r,g,b,a],clear:true});
        if(!this.gl&&this.host.sceneRenderers.size===0)this.host.render();
    }
    clearOverlay(){if(!this.gl)return;const gl=this.gl,previous=gl.getParameter(gl.FRAMEBUFFER_BINDING);gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT);gl.bindFramebuffer(gl.FRAMEBUFFER,previous);}
    composite(canvas){
        const gl=this.context();
        if(!this.vector){
            const shaders=[];let program,vao,texture;
            try{
                for(const [type,source] of [[gl.VERTEX_SHADER,'#version 300 es\nout vec2 uv;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0,1);}'],[gl.FRAGMENT_SHADER,'#version 300 es\nprecision mediump float;in vec2 uv;uniform sampler2D source;out vec4 color;void main(){color=texture(source,uv);}']]){const shader=gl.createShader(type);shaders.push(shader);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));}
                program=gl.createProgram();for(const shader of shaders)gl.attachShader(program,shader);gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));
                vao=gl.createVertexArray();texture=gl.createTexture();this.vector={program,vao,texture};
            }catch(error){if(program)gl.deleteProgram(program);if(vao)gl.deleteVertexArray(vao);if(texture)gl.deleteTexture(texture);throw error;}finally{for(const shader of shaders)gl.deleteShader(shader);}
        }
        const {program,vao,texture}=this.vector;
        gl.useProgram(program);gl.bindVertexArray(vao);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
        const premultiply=gl.getParameter(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL);gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,true);
        if(this.vector.width!==canvas.width||this.vector.height!==canvas.height){
            gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,canvas.width,canvas.height,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
            this.vector.width=canvas.width;this.vector.height=canvas.height;
        }
        gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,gl.RGBA,gl.UNSIGNED_BYTE,canvas);
        gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,premultiply);
        const depth=gl.isEnabled(gl.DEPTH_TEST),scissor=gl.isEnabled(gl.SCISSOR_TEST),cull=gl.isEnabled(gl.CULL_FACE);gl.disable(gl.DEPTH_TEST);gl.disable(gl.SCISSOR_TEST);gl.disable(gl.CULL_FACE);gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE_MINUS_SRC_ALPHA);gl.drawArrays(gl.TRIANGLES,0,3);if(depth)gl.enable(gl.DEPTH_TEST);if(scissor)gl.enable(gl.SCISSOR_TEST);if(cull)gl.enable(gl.CULL_FACE);
    }
    async decode(path,bytes,flip){
        let bitmap;
        try{
            let blob;if(path!==null){const response=await fetch(path);if(!response.ok)throw new Error('Image request failed: '+response.status+' '+path);blob=await response.blob();}else blob=new Blob([bytes]);
            bitmap=await createImageBitmap(blob,{premultiplyAlpha:'none',colorSpaceConversion:'none'});
            const canvas=drawingCanvas(bitmap.width,bitmap.height),ctx=canvas.getContext('2d',{willReadFrequently:true});
            if(flip){ctx.translate(0,bitmap.height);ctx.scale(1,-1);}ctx.drawImage(bitmap,0,0);
            return {width:bitmap.width,height:bitmap.height,pixels:new Uint8Array(ctx.getImageData(0,0,bitmap.width,bitmap.height).data.buffer)};
        }finally{bitmap?.close();}
    }
    close(){if(this.closed)return;if(this.vector){this.gl.deleteProgram(this.vector.program);this.gl.deleteVertexArray(this.vector.vao);this.gl.deleteTexture(this.vector.texture);this.vector=null;}for(const [id,item] of this.objects)if(item.kind!=='Uniform')this.remove(id,item.kind);this.objects.clear();this.images.clear();this.uniforms.clear();this.canvas?.remove();this.gl?.getExtension('WEBGL_lose_context')?.loseContext();this.closed=true;}
}
