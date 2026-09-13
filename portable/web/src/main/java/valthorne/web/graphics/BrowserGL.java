package valthorne.web.graphics;
import java.nio.*;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;
import org.teavm.jso.typedarrays.*;
/** Internal WebGL 2 bridge used by generated engine backend sources. */
public final class BrowserGL {
    public static final int GL_COMPUTE_SHADER=0x91B9,GL_TEXTURE_2D_ARRAY=0x8C1A;
    public static void glDeleteTextures(int[] ids){for(int id:ids)glDeleteTextures(id);}
    public static void glUniform3fv(int location,float[] values){glUniform3f(location,values[0],values[1],values[2]);}
    @JSBody(params={"location","x","y"},script="valthorneHost.graphics.context().uniform2i(valthorneHost.graphics.get(location),x,y);") public static native void glUniform2i(int location,int x,int y);
    public static final int GL_DST_COLOR=0x0306,GL_LINE_LOOP=0x0002,GL_POINTS=0x0000,GL_TRIANGLE_FAN=0x0006;
    @JSBody(params="width",script="valthorneHost.graphics.context().lineWidth(width);") public static native void glLineWidth(float width);
    @JSBody(script="valthorneHost.graphics.context();return 1;") public static native int contextToken();
    @JSBody(params="value",script="valthorneHost.graphics.context().cullFace(value);") public static native void glCullFace(int value);
    @JSBody(params="value",script="valthorneHost.graphics.context().frontFace(value);") public static native void glFrontFace(int value);
    @JSBody(params="value",script="valthorneHost.graphics.context().depthFunc(value);") public static native void glDepthFunc(int value);
    @JSBody(params="value",script="valthorneHost.graphics.context().drawBuffers([value]);") public static native void glDrawBuffer(int value);
    @JSBody(params="value",script="valthorneHost.graphics.context().readBuffer(value);") public static native void glReadBuffer(int value);
    public static float glGetFloat(int value){return (float)glGetDouble(value);}
    public static final int GL_TEXTURE3=0x84C3,GL_TEXTURE4=0x84C4,GL_TEXTURE_BUFFER=0x8C2A,GL_TEXTURE_BINDING_BUFFER=0x8C2C,GL_MAX_TEXTURE_BUFFER_SIZE=0x8C2B,GL_RGBA32F=0x8814,GL_R32I=0x8235;
    public static final int GL_BACK=0x0405,GL_CCW=0x0901,GL_COMPARE_REF_TO_TEXTURE=0x884E,GL_DEPTH_CLEAR_VALUE=0x0B73,GL_DEPTH_COMPONENT=0x1902,GL_DYNAMIC_DRAW=0x88E8,GL_LEQUAL=0x0203,GL_NONE=0;
    public static final int GL_POLYGON_OFFSET_FACTOR=0x8038,GL_POLYGON_OFFSET_FILL=0x8037,GL_POLYGON_OFFSET_UNITS=0x2A00,GL_TEXTURE_COMPARE_MODE=0x884C,GL_TEXTURE_COMPARE_FUNC=0x884D;
    @JSBody(params="name",script="return valthorneHost.graphics.context().getParameter(name);") public static native String glGetString(int name);
    @JSBody(params="name",script="return +valthorneHost.graphics.context().getParameter(name);") public static native double glGetDouble(int name);
    @JSBody(params="value",script="valthorneHost.graphics.context().clearDepth(value);") public static native void glClearDepth(double value);
    @JSBody(params={"factor","units"},script="valthorneHost.graphics.context().polygonOffset(factor,units);") public static native void glPolygonOffset(float factor,float units);
    @JSBody(script="return valthorneHost.graphics.add(valthorneHost.graphics.context().createSampler(),'Sampler');") public static native int glGenSamplers();
    @JSBody(params="id",script="valthorneHost.graphics.remove(id,'Sampler');") public static native void glDeleteSamplers(int id);
    @JSBody(params={"id","name","value"},script="valthorneHost.graphics.context().samplerParameteri(valthorneHost.graphics.get(id),name,value);") public static native void glSamplerParameteri(int id,int name,int value);
    @JSBody(params={"target","format","buffer"},script="valthorneHost.graphics.textureBuffer(target,format,buffer);") public static native void glTexBuffer(int target,int format,int buffer);
    @JSBody(params={"target","values","usage"},script="valthorneHost.graphics.bufferData(target,values,usage);") private static native void bufferIntegers(int target,Int32Array values,int usage);
    @JSBody(params="count",script="return new Int32Array(count);") private static native Int32Array signedInts(int count);
    public static void glBufferData(int target,IntBuffer buffer,int usage){Int32Array values=signedInts(buffer.remaining());for(int i=0;i<buffer.remaining();i++)values.set(i,buffer.get(buffer.position()+i));bufferIntegers(target,values,usage);}
    public static final int GL_VERTEX_ARRAY_BINDING=0x85B5,GL_ARRAY_BUFFER_BINDING=0x8894,GL_UNSIGNED_INT=0x1405,GL_TEXTURE_BINDING_2D=0x8069,GL_DEPTH_WRITEMASK=0x0B72;
    public static final int GL_BLEND_SRC_ALPHA=0x80CB,GL_BLEND_DST_ALPHA=0x80CA,GL_BLEND_EQUATION_RGB=0x8009,GL_BLEND_EQUATION_ALPHA=0x883D,GL_BLEND_SRC=0x80C9,GL_BLEND_DST=0x80C8;
    public static final int GL_RG32UI=0x823C,GL_RG_INTEGER=0x8228,GL_PIXEL_UNPACK_BUFFER=0x88EC,GL_PIXEL_UNPACK_BUFFER_BINDING=0x88EF,GL_UNPACK_ALIGNMENT=0x0CF5,GL_UNPACK_ROW_LENGTH=0x0CF2,GL_UNPACK_SKIP_ROWS=0x0CF3,GL_UNPACK_SKIP_PIXELS=0x0CF4;
    public static void glGetIntegerv(int name,IntBuffer result){int[] values=integers(name);for(int i=0;i<values.length;i++)result.put(result.position()+i,values[i]);}
    @JSBody(params={"index","size","type","stride","offset"},script="valthorneHost.graphics.context().vertexAttribIPointer(index,size,type,stride,offset);") private static native void integerPointer(int index,int size,int type,int stride,double offset);
    public static void glVertexAttribIPointer(int index,int size,int type,int stride,long offset){integerPointer(index,size,type,stride,offset);}
    @JSBody(params={"location","transpose","values"},script="valthorneHost.graphics.context().uniformMatrix4fv(valthorneHost.graphics.get(location),transpose,values);") private static native void matrix(int location,boolean transpose,Float32Array values);
    public static void glUniformMatrix4fv(int location,boolean transpose,FloatBuffer buffer){matrix(location,transpose,stage(buffer));}
    // TeaVM exposes the buffer's existing ArrayBuffer; WebGL 2 accepts a range
    // directly, eliminating a byte-by-byte copy and a second staging allocation.
    @JSBody(params={"target","offset","values","start","count"},script="if(count)valthorneHost.graphics.context().bufferSubData(target,offset,values,start,count);") private static native void bufferBytes(int target,double offset,@JSBuffer(JSBufferType.UINT8) ByteBuffer values,int start,int count);
    public static void glBufferSubData(int target,long offset,ByteBuffer buffer){bufferBytes(target,offset,buffer,buffer.position(),buffer.remaining());}
    @JSBody(params={"target","level","internal","width","height","border","format","type","pixels"},script="valthorneHost.graphics.upload(target,level,internal,width,height,border,format,type,pixels);") private static native void uploadFloats(int target,int level,int internal,int width,int height,int border,int format,int type,Float32Array pixels);
    @JSBody(params={"target","level","internal","width","height","border","format","type","pixels"},script="valthorneHost.graphics.upload(target,level,internal,width,height,border,format,type,pixels);") private static native void uploadIntegers(int target,int level,int internal,int width,int height,int border,int format,int type,Uint32Array pixels);
    public static void glTexImage2D(int target,int level,int internal,int width,int height,int border,int format,int type,FloatBuffer buffer){uploadFloats(target,level,internal,width,height,border,format,type,buffer==null?null:stage(buffer));}
    @JSBody(params="size",script="return new Uint32Array(size);") private static native Uint32Array unsignedInts(int size);
    @JSBody(params={"name","value"},script="valthorneHost.graphics.context().pixelStorei(name,value);") public static native void glPixelStorei(int name,int value);
    public static void glTexImage2D(int target,int level,int internal,int width,int height,int border,int format,int type,IntBuffer buffer){Uint32Array values=null;if(buffer!=null){values=unsignedInts(buffer.remaining());for(int i=0;i<buffer.remaining();i++)values.set(i,buffer.get(buffer.position()+i));}uploadIntegers(target,level,internal,width,height,border,format,type,values);}
    @JSBody(params={"r","g","b","a"},script="valthorneHost.graphics.context().clearColor(r,g,b,a);") public static native void glClearColor(float r,float g,float b,float a);
    @JSBody(params="mask",script="valthorneHost.graphics.context().clear(mask);") public static native void glClear(int mask);
    @JSBody(params={"r","g","b","a"},script="valthorneHost.graphics.context().colorMask(r,g,b,a);") public static native void glColorMask(boolean r,boolean g,boolean b,boolean a);
    @JSBody(params={"rgbSource","rgbDestination","alphaSource","alphaDestination"},script="valthorneHost.graphics.context().blendFuncSeparate(rgbSource,rgbDestination,alphaSource,alphaDestination);") public static native void glBlendFuncSeparate(int rgbSource,int rgbDestination,int alphaSource,int alphaDestination);
    @JSBody(params="value",script="valthorneHost.graphics.context().blendEquation(value);") public static native void glBlendEquation(int value);
    @JSBody(params={"unit","sampler"},script="valthorneHost.graphics.context().bindSampler(unit,valthorneHost.graphics.get(sampler));") public static native void glBindSampler(int unit,int sampler);
    @JSBody(params="value",script="return valthorneHost.graphics.context().getParameter(value);") private static native float[] floats(int value);
    public static void glGetFloatv(int value,float[] target){float[] values=floats(value);System.arraycopy(values,0,target,0,values.length);}
    @JSBody(params="value",script="return valthorneHost.graphics.context().getParameter(value).map(v=>v?1:0);") private static native int[] booleans(int value);
    public static void glGetBooleanv(int value,ByteBuffer target){int[] values=booleans(value);for(int i=0;i<values.length;i++)target.put(target.position()+i,(byte)values[i]);}
    public static void glTexSubImage2D(int target,int level,int x,int y,int w,int h,int format,int type,FloatBuffer data){subImage(target,level,x,y,w,h,format,type,stage(data));}
    @JSBody(params={"target","level","x","y","w","h","format","type","data"},script="valthorneHost.graphics.context().texSubImage2D(target,level,x,y,w,h,format,type,data);") private static native void subImage(int target,int level,int x,int y,int w,int h,int format,int type,Float32Array data);
public static final int GL_ACTIVE_TEXTURE=0x84E0;
public static final int GL_COLOR_CLEAR_VALUE=0xC22;
public static final int GL_COLOR_WRITEMASK=0xC23;
public static final int GL_DRAW_FRAMEBUFFER=0x8CA9;
public static final int GL_DRAW_FRAMEBUFFER_BINDING=0x8CA6;
public static final int GL_FRAMEBUFFER_SRGB=0x8DB9;
public static final int GL_LINEAR=0x2601;
public static final int GL_MAX_TEXTURE_SIZE=0xD33;
public static final int GL_ONE=1;
public static final int GL_R32F=0x822E;
public static final int GL_READ_FRAMEBUFFER=0x8CA8;
public static final int GL_READ_FRAMEBUFFER_BINDING=0x8CAA;
public static final int GL_RED=0x1903;
public static final int GL_REPEAT=0x2901;
public static final int GL_RGBA16F=0x881A;
public static final int GL_SAMPLER_BINDING=0x8919;
public static final int GL_TEXTURE1=0x84C1;
public static final int GL_TEXTURE2=0x84C2;
public static final int GL_TRIANGLE_STRIP=0x5;
public static final int GL_ZERO=0;
    public static final int GL_FUNC_ADD=0x8006;
    @JSBody(params="value",script="valthorneHost.graphics.context().depthMask(value);") public static native void glDepthMask(boolean value);
    @JSBody(params={"rgb","alpha"},script="valthorneHost.graphics.context().blendEquationSeparate(rgb,alpha);") public static native void glBlendEquationSeparate(int rgb,int alpha);
private BrowserGL(){}
public static final int GL_ARRAY_BUFFER=0x8892;
public static final int GL_BLEND=0xBE2;
public static final int GL_CLAMP_TO_EDGE=0x812F;
public static final int GL_COLOR_ATTACHMENT0=0x8CE0;
public static final int GL_COLOR_BUFFER_BIT=0x4000;
public static final int GL_COMPILE_STATUS=0x8B81;
public static final int GL_CULL_FACE=0xB44;
public static final int GL_CURRENT_PROGRAM=0x8B8D;
public static final int GL_DEPTH_ATTACHMENT=0x8D00;
public static final int GL_DEPTH_BUFFER_BIT=0x100;
public static final int GL_DEPTH_COMPONENT24=0x81A6;
public static final int GL_DEPTH_TEST=0xB71;
public static final int GL_FALSE=0;
public static final int GL_FLOAT=0x1406;
public static final int GL_FRAGMENT_SHADER=0x8B30;
public static final int GL_FRAMEBUFFER=0x8D40;
public static final int GL_FRAMEBUFFER_BINDING=0x8CA6;
public static final int GL_FRAMEBUFFER_COMPLETE=0x8CD5;
public static final int GL_INVALID_INDEX=0xFFFFFFFF;
public static final int GL_LINK_STATUS=0x8B82;
public static final int GL_MAX_TEXTURE_IMAGE_UNITS=0x8872;
public static final int GL_NEAREST=0x2600;
public static final int GL_ONE_MINUS_SRC_ALPHA=0x303;
public static final int GL_RENDERBUFFER=0x8D41;
public static final int GL_RGBA=0x1908;
public static final int GL_RGBA8=0x8058;
public static final int GL_SCISSOR_BOX=0xC10;
public static final int GL_SCISSOR_TEST=0xC11;
public static final int GL_SRC_ALPHA=0x302;
public static final int GL_STATIC_DRAW=0x88E4;
public static final int GL_STENCIL_TEST=0xB90;
public static final int GL_STREAM_DRAW=0x88E0;
public static final int GL_TEXTURE0=0x84C0;
public static final int GL_TEXTURE_2D=0xDE1;
public static final int GL_TEXTURE_MAG_FILTER=0x2800;
public static final int GL_TEXTURE_MIN_FILTER=0x2801;
public static final int GL_TEXTURE_WRAP_S=0x2802;
public static final int GL_TEXTURE_WRAP_T=0x2803;
public static final int GL_TRIANGLES=0x4;
public static final int GL_UNIFORM_BUFFER=0x8A11;
public static final int GL_UNSIGNED_BYTE=0x1401;
public static final int GL_VALIDATE_STATUS=0x8B83;
public static final int GL_VERTEX_SHADER=0x8B31;
public static final int GL_VIEWPORT=0xBA2;
@JSBody(params={},script="return valthorneHost.graphics.create('Texture');") public static native int glGenTextures();
@JSBody(params={"id"},script="valthorneHost.graphics.remove(id,'Texture');") public static native void glDeleteTextures(int id);
@JSBody(params={},script="return valthorneHost.graphics.create('Buffer');") public static native int glGenBuffers();
@JSBody(params={"id"},script="valthorneHost.graphics.remove(id,'Buffer');") public static native void glDeleteBuffers(int id);
@JSBody(params={},script="return valthorneHost.graphics.create('VertexArray');") public static native int glGenVertexArrays();
@JSBody(params={"id"},script="valthorneHost.graphics.remove(id,'VertexArray');") public static native void glDeleteVertexArrays(int id);
@JSBody(params={},script="return valthorneHost.graphics.create('Framebuffer');") public static native int glGenFramebuffers();
@JSBody(params={"id"},script="valthorneHost.graphics.remove(id,'Framebuffer');") public static native void glDeleteFramebuffers(int id);
@JSBody(params={},script="return valthorneHost.graphics.create('Renderbuffer');") public static native int glGenRenderbuffers();
@JSBody(params={"id"},script="valthorneHost.graphics.remove(id,'Renderbuffer');") public static native void glDeleteRenderbuffers(int id);
@JSBody(params={"target","id"},script="valthorneHost.graphics.bindTexture(target,id);") public static native void glBindTexture(int target,int id);
@JSBody(params={"target","id"},script="valthorneHost.graphics.bindBuffer(target,id);") public static native void glBindBuffer(int target,int id);
@JSBody(params={"target","id"},script="valthorneHost.graphics.context().bindFramebuffer(target,valthorneHost.graphics.get(id));") public static native void glBindFramebuffer(int target,int id);
@JSBody(params={"target","id"},script="valthorneHost.graphics.context().bindRenderbuffer(target,valthorneHost.graphics.get(id));") public static native void glBindRenderbuffer(int target,int id);
@JSBody(params={"id"},script="valthorneHost.graphics.context().bindVertexArray(valthorneHost.graphics.get(id));") public static native void glBindVertexArray(int id);
@JSBody(params={"value"},script="valthorneHost.graphics.context().activeTexture(value);") public static native void glActiveTexture(int value);
@JSBody(params={"value"},script="valthorneHost.graphics.context().enable(value);") public static native void glEnable(int value);
@JSBody(params={"value"},script="valthorneHost.graphics.context().disable(value);") public static native void glDisable(int value);
@JSBody(params={"source","dest"},script="valthorneHost.graphics.blend(source,dest);") public static native void glBlendFunc(int source,int dest);
@JSBody(params={"x","y","width","height"},script="valthorneHost.graphics.context().viewport(x,y,width,height);") public static native void glViewport(int x,int y,int width,int height);
@JSBody(params={"x","y","width","height"},script="valthorneHost.graphics.context().scissor(x,y,width,height);") public static native void glScissor(int x,int y,int width,int height);
@JSBody(params={"target"},script="valthorneHost.graphics.context().generateMipmap(target);") public static native void glGenerateMipmap(int target);
@JSBody(params={"index"},script="valthorneHost.graphics.context().enableVertexAttribArray(index);") public static native void glEnableVertexAttribArray(int index);
@JSBody(params={"index","divisor"},script="valthorneHost.graphics.context().vertexAttribDivisor(index,divisor);") public static native void glVertexAttribDivisor(int index,int divisor);
@JSBody(params={"mode","first","count","instances"},script="valthorneHost.graphics.context().drawArraysInstanced(mode,first,count,instances);") public static native void glDrawArraysInstanced(int mode,int first,int count,int instances);
@JSBody(params={"mode","first","count"},script="valthorneHost.graphics.context().drawArrays(mode,first,count);") public static native void glDrawArrays(int mode,int first,int count);
@JSBody(params={"target","format","width","height"},script="valthorneHost.graphics.context().renderbufferStorage(target,format,width,height);") public static native void glRenderbufferStorage(int target,int format,int width,int height);
@JSBody(params={"value"},script="return valthorneHost.graphics.context().isEnabled(value);") public static native boolean glIsEnabled(int value);
@JSBody(params={"value"},script="return valthorneHost.graphics.parameter(value);") public static native int glGetInteger(int value);
@JSBody(params={"value"},script="var raw=valthorneHost.graphics.context().getParameter(value);return ArrayBuffer.isView(raw)||Array.isArray(raw)?Array.from(raw):[+valthorneHost.graphics.parameter(value)];") private static native int[] integers(int value);
public static void glGetIntegerv(int value,int[] values){int[] result=integers(value);System.arraycopy(result,0,values,0,result.length);}
@JSBody(params={"target"},script="return valthorneHost.graphics.context().checkFramebufferStatus(target);") public static native int glCheckFramebufferStatus(int target);
@JSBody(params={"target","attachment","textureTarget","id","level"},script="valthorneHost.graphics.context().framebufferTexture2D(target,attachment,textureTarget,valthorneHost.graphics.get(id),level);") public static native void glFramebufferTexture2D(int target,int attachment,int textureTarget,int id,int level);
@JSBody(params={"target","attachment","renderbufferTarget","id"},script="valthorneHost.graphics.context().framebufferRenderbuffer(target,attachment,renderbufferTarget,valthorneHost.graphics.get(id));") public static native void glFramebufferRenderbuffer(int target,int attachment,int renderbufferTarget,int id);
@JSBody(params={"target","name","value"},script="valthorneHost.graphics.filter(target,name,value);") public static native void glTexParameteri(int target,int name,int value);
@JSBody(params={"index","size","type","normalized","stride","offset"},script="valthorneHost.graphics.context().vertexAttribPointer(index,size,type,normalized,stride,offset);") public static native void vertexPointer(int index,int size,int type,boolean normalized,int stride,double offset);
public static void glVertexAttribPointer(int index,int size,int type,boolean normalized,int stride,long offset){vertexPointer(index,size,type,normalized,stride,offset);}
@JSBody(params={"target","size","usage"},script="valthorneHost.graphics.context().bufferData(target,size,usage);") public static native void bufferSize(int target,double size,int usage);
public static void glBufferData(int target,long size,int usage){bufferSize(target,size,usage);}
@JSBody(params={"target","data","usage"},script="valthorneHost.graphics.bufferData(target,data,usage);") public static native void bufferData(int target,Float32Array data,int usage);
@JSBody(params={"target","offset","data"},script="valthorneHost.graphics.context().bufferSubData(target,offset,data);") public static native void bufferSubData(int target,double offset,Float32Array data);
@JSBody(params={"array","size"},script="return array.subarray(0,size);") private static native Float32Array slice(Float32Array array,int size);
private static Float32Array staging=Float32Array.create(0);
private static Float32Array stage(FloatBuffer buffer){int size=buffer.remaining();if(staging.getLength()<size)staging=Float32Array.create(size);int at=buffer.position();for(int i=0;i<size;i++)staging.set(i,buffer.get(at+i));return slice(staging,size);}
public static void glBufferData(int target,FloatBuffer buffer,int usage){bufferData(target,stage(buffer),usage);}
public static void glBufferSubData(int target,long offset,FloatBuffer buffer){bufferSubData(target,offset,stage(buffer));}
@JSBody(params={"target","level","internal","width","height","border","format","type","pixels"},script="valthorneHost.graphics.upload(target,level,internal,width,height,border,format,type,pixels);") public static native void upload(int target,int level,int internal,int width,int height,int border,int format,int type,Uint8Array pixels);
public static void glTexImage2D(int target,int level,int internal,int width,int height,int border,int format,int type,long address){if(address!=0)throw new UnsupportedOperationException("Native pixel addresses are unavailable in a browser");upload(target,level,internal,width,height,border,format,type,null);}
public static void glTexImage2D(int target,int level,int internal,int width,int height,int border,int format,int type,ByteBuffer buffer){Uint8Array pixels=null;if(buffer!=null){pixels=Uint8Array.create(buffer.remaining());for(int i=0;i<buffer.remaining();i++)pixels.set(i,(short)(buffer.get(buffer.position()+i)&255));}upload(target,level,internal,width,height,border,format,type,pixels);}
@JSBody(params={"type"},script="return valthorneHost.graphics.add(valthorneHost.graphics.context().createShader(type),'Shader');") public static native int glCreateShader(int type);
@JSBody(params={},script="return valthorneHost.graphics.create('Program');") public static native int glCreateProgram();
@JSBody(params={"id","source"},script="valthorneHost.graphics.shaderSource(id,source);") public static native void glShaderSource(int id,String source);
@JSBody(params={"id"},script="valthorneHost.graphics.context().compileShader(valthorneHost.graphics.get(id));") public static native void glCompileShader(int id);
@JSBody(params={"id"},script="valthorneHost.graphics.context().linkProgram(valthorneHost.graphics.get(id));") public static native void glLinkProgram(int id);
@JSBody(params={"id"},script="valthorneHost.graphics.context().validateProgram(valthorneHost.graphics.get(id));") public static native void glValidateProgram(int id);
@JSBody(params={"id"},script="valthorneHost.graphics.context().useProgram(valthorneHost.graphics.get(id));") public static native void glUseProgram(int id);
@JSBody(params={"id"},script="valthorneHost.graphics.remove(id,'Program');") public static native void glDeleteProgram(int id);
@JSBody(params={"id","name"},script="return +valthorneHost.graphics.context().getProgramParameter(valthorneHost.graphics.get(id),name);") public static native int glGetProgrami(int id,int name);
@JSBody(params={"id"},script="return valthorneHost.graphics.context().getProgramInfoLog(valthorneHost.graphics.get(id))||'';") public static native String glGetProgramInfoLog(int id);
@JSBody(params={"id"},script="valthorneHost.graphics.remove(id,'Shader');") public static native void glDeleteShader(int id);
@JSBody(params={"id","name"},script="return +valthorneHost.graphics.context().getShaderParameter(valthorneHost.graphics.get(id),name);") public static native int glGetShaderi(int id,int name);
@JSBody(params={"id"},script="return valthorneHost.graphics.context().getShaderInfoLog(valthorneHost.graphics.get(id))||'';") public static native String glGetShaderInfoLog(int id);
@JSBody(params={"program","shader"},script="valthorneHost.graphics.context().attachShader(valthorneHost.graphics.get(program),valthorneHost.graphics.get(shader));") public static native void glAttachShader(int program,int shader);
@JSBody(params={"program","shader"},script="valthorneHost.graphics.context().detachShader(valthorneHost.graphics.get(program),valthorneHost.graphics.get(shader));") public static native void glDetachShader(int program,int shader);
@JSBody(params={"program","index","name"},script="valthorneHost.graphics.context().bindAttribLocation(valthorneHost.graphics.get(program),index,name);") public static native void glBindAttribLocation(int program,int index,String name);
@JSBody(params={"program","name"},script="return valthorneHost.graphics.context().getAttribLocation(valthorneHost.graphics.get(program),name);") public static native int glGetAttribLocation(int program,String name);
@JSBody(params={"program","name"},script="return valthorneHost.graphics.uniform(program,name);") public static native int glGetUniformLocation(int program,String name);
@JSBody(params={"program","name"},script="return valthorneHost.graphics.context().getUniformBlockIndex(valthorneHost.graphics.get(program),name)|0;") public static native int glGetUniformBlockIndex(int program,String name);
@JSBody(params={"program","index","binding"},script="valthorneHost.graphics.context().uniformBlockBinding(valthorneHost.graphics.get(program),index,binding);") public static native void glUniformBlockBinding(int program,int index,int binding);
@JSBody(params={"location","x"},script="valthorneHost.graphics.context().uniform1i(valthorneHost.graphics.get(location),x);") public static native void glUniform1i(int location,int x);
@JSBody(params={"location","x"},script="valthorneHost.graphics.context().uniform1f(valthorneHost.graphics.get(location),x);") public static native void glUniform1f(int location,float x);
@JSBody(params={"location","x","y"},script="valthorneHost.graphics.context().uniform2f(valthorneHost.graphics.get(location),x,y);") public static native void glUniform2f(int location,float x,float y);
@JSBody(params={"location","x","y","z"},script="valthorneHost.graphics.context().uniform3f(valthorneHost.graphics.get(location),x,y,z);") public static native void glUniform3f(int location,float x,float y,float z);
@JSBody(params={"location","x","y","z","w"},script="valthorneHost.graphics.context().uniform4f(valthorneHost.graphics.get(location),x,y,z,w);") public static native void glUniform4f(int location,float x,float y,float z,float w);
@JSBody(params={"location","transpose","values"},script="valthorneHost.graphics.context().uniformMatrix4fv(valthorneHost.graphics.get(location),transpose,values);") public static native void glUniformMatrix4fv(int location,boolean transpose,float[] values);
}
