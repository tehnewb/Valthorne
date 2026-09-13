package valthorne.graphics.shader;
import java.nio.ByteBuffer;
import org.teavm.jso.*;
import org.teavm.interop.*;
import org.teavm.jso.typedarrays.Uint8Array;
/** Existing GLSL compute API compiled to WebGPU; Java callers suspend during GPU transfers. */
public class ComputeShader {
 private int program;
 public ComputeShader(String source){program=compile(java.util.Objects.requireNonNull(source));}
 @JSBody(script="return !!valthorneHost.compute?.supported();") public static native boolean isComputeSupported();
 /** Dispatch waits for cross-backend image transfers, so these barriers are already satisfied. */
 public static void memoryBarrierAll(){}public static void memoryBarrierImage(){}
 @JSBody(params={"id","unit","access","format"},script="valthorneHost.compute.bindImage(id,unit,access,format);") public static native void bindImage2D(int id,int unit,int access,int format);
 public static int createSSBO(long size,int usage){if(size<4||size>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid buffer size");return create((int)size);}
 @JSBody(params="size",script="return valthorneHost.compute.createBuffer(size);") private static native int create(int size);
 @JSBody(params={"id","binding"},script="valthorneHost.compute.bindBuffer(id,binding);") public static native void bindSSBO(int id,int binding);
 public static void updateSSBO(int id,long offset,ByteBuffer data){java.util.Objects.requireNonNull(data);if(offset<0||offset>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid buffer offset");Uint8Array bytes=Uint8Array.create(data.remaining());for(int i=0;i<data.remaining();i++)bytes.set(i,(short)(data.get(data.position()+i)&255));update(id,(int)offset,bytes);}
 @JSBody(params={"id","offset","bytes"},script="valthorneHost.compute.updateBuffer(id,offset,bytes);") private static native void update(int id,int offset,Uint8Array bytes);
 @JSBody(params="id",script="valthorneHost.compute.deleteBuffer(id);") public static native void deleteSSBO(int id);
 public void bind(){bindNative(program);}public void unbind(){bindNative(0);}
 @JSBody(params="id",script="valthorneHost.compute.bind(id);") private static native void bindNative(int id);
 public void dispatch(int x,int y,int z){dispatchNative(x,y,z);}
 public void dispose(){if(program!=0){remove(program);program=0;}}
 @JSBody(params="id",script="valthorneHost.compute.deleteProgram(id);") private static native void remove(int id);
 public void setUniform1i(String name,int value){uniform(program,name,new double[]{value});}
 public void setUniform1f(String name,float value){uniform(program,name,new double[]{value});}
 public void setUniform2f(String name,float x,float y){uniform(program,name,new double[]{x,y});}
 public void setUniform3f(String name,float x,float y,float z){uniform(program,name,new double[]{x,y,z});}
 public void setUniform4f(String name,float x,float y,float z,float w){uniform(program,name,new double[]{x,y,z,w});}
 public void setUniform2i(String name,int x,int y){uniform(program,name,new double[]{x,y});}
 @JSBody(params={"id","name","values"},script="valthorneHost.compute.uniform(id,name,values);") private static native void uniform(int id,String name,double[] values);
 @Async private static native int compile(String source);
 private static void compile(String source,AsyncCallback<Integer> callback){compileNative(source,(id,error)->{if(error!=null)callback.error(new IllegalStateException(error));else callback.complete(id);});}
 @JSFunctor private interface Compiled extends JSObject{void accept(int id,String error);}
 @JSBody(params={"source","callback"},script="valthorneHost.compute.compile(source).then(id=>callback(id,null),e=>callback(0,String(e)));") private static native void compileNative(String source,Compiled callback);
 @Async private static native void dispatchNative(int x,int y,int z);
 private static void dispatchNative(int x,int y,int z,AsyncCallback<Void> callback){dispatchAsync(x,y,z,error->{if(error!=null)callback.error(new IllegalStateException(error));else callback.complete(null);});}
 @JSFunctor private interface Dispatched extends JSObject{void accept(String error);}
 @JSBody(params={"x","y","z","callback"},script="valthorneHost.compute.dispatch(x,y,z).then(()=>callback(null),e=>callback(String(e)));") private static native void dispatchAsync(int x,int y,int z,Dispatched callback);
}
