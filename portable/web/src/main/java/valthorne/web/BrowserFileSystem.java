package valthorne.web;

import java.io.*;
import org.teavm.runtime.fs.*;
import org.teavm.interop.*;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Uint8Array;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** The Java filesystem API backed by an origin-private IndexedDB store. */
public final class BrowserFileSystem implements VirtualFileSystem {
 private static VirtualFileSystem instance;
 public static VirtualFileSystem getInstance(){if(instance==null)instance=new BrowserFileSystem();return instance;}
 public static void setInstance(VirtualFileSystem value){instance=value;}
 public static Path move(Path source,Path target,CopyOption... options)throws IOException{
  boolean replace=false;for(var option:options){Objects.requireNonNull(option);if(option==StandardCopyOption.REPLACE_EXISTING)replace=true;else if(option!=StandardCopyOption.ATOMIC_MOVE)throw new UnsupportedOperationException("Unsupported move option");}
  if(source.getFileSystem()!=target.getFileSystem())throw new ProviderMismatchException();
  String from=normalize(source.toAbsolutePath().toString()),to=normalize(target.toAbsolutePath().toString());
  if(!exists(from))throw new NoSuchFileException(from);if(from.equals(to))return target;
  if(exists(to)&&!replace)throw new FileAlreadyExistsException(to);
  String parent=to.substring(0,to.lastIndexOf('/'));if(parent.isEmpty())parent="/";
  try{if(!mutate("move",parent,from,to.substring(to.lastIndexOf('/')+1),replace?1:0))throw new FileSystemException(from,to,"Move rejected");}catch(UncheckedIOException error){throw error.getCause();}
  return target;
 }
 public String getUserDir(){return "/";}public boolean isWindows(){return false;}
 public String[] getRoots(){return new String[]{""};}
 public String canonicalize(String path){return normalize(path);}
 public VirtualFile getFile(String path){return new FileNode(normalize(path));}
 @JSBody(params="path",script="return valthorneFiles.normalize(path);") private static native String normalize(String path);
 @JSBody(params={"path","property"},script="var n=valthorneFiles.get(path);return n?+n[property]:0;") private static native double property(String path,String property);
 @JSBody(params="path",script="return !!valthorneFiles.get(path);") private static native boolean exists(String path);
 @JSBody(params="path",script="return valthorneFiles.list(path);") private static native String[] list(String path);
 @Async private static native boolean mutate(String operation,String path,String arg,String name,double value);
 private static void mutate(String operation,String path,String arg,String name,double value,AsyncCallback<Boolean> callback){
  mutateNative(operation,path,arg,name,value,(result,error)->{if(error!=null)callback.error(new UncheckedIOException(new IOException(error)));else callback.complete(result);});
 }
 @JSFunctor private interface Changed extends JSObject{void accept(boolean result,String error);}
 @JSBody(params={"operation","path","arg","name","value","callback"},script="var fileSystemBackend=valthorneFiles,operationPromise;try{switch(operation){case 'create':operationPromise=fileSystemBackend.create(path,arg,!!value);break;case 'remove':operationPromise=fileSystemBackend.remove(path);break;case 'move':operationPromise=fileSystemBackend.move(path,arg,name,!!value);break;default:operationPromise=fileSystemBackend.metadata(path,arg,arg==='readonly'?!!value:value);}}catch(e){callback(false,String(e));return;}operationPromise.then(v=>callback(v,null),e=>callback(false,String(e)));") private static native void mutateNative(String operation,String path,String arg,String name,double value,Changed callback);
 private static final class FileNode implements VirtualFile {
  private final String path;FileNode(String path){this.path=path;}
  public String getName(){return path.substring(path.lastIndexOf('/')+1);}
  public boolean isDirectory(){return property(path,"dir")!=0;}public boolean isFile(){return BrowserFileSystem.exists(path)&&!isDirectory();}
  public String[] listFiles(){return list(path);}
  public VirtualFileAccessor createAccessor(boolean readable,boolean writable,boolean append){JSObject handle=open(path,readable,writable,append);return handle==null?null:new Accessor(handle,readable,writable);}
  public boolean createFile(String name)throws IOException{try{return mutate("create",path,name,null,0);}catch(UncheckedIOException error){throw error.getCause();}}
  public boolean createDirectory(String name){return mutate("create",path,name,null,1);}
  public boolean delete(){return mutate("remove",path,null,null,0);}
  public boolean adopt(VirtualFile source,String name){return source instanceof FileNode file&&mutate("move",path,file.path,name,0);}
  public boolean canRead(){return BrowserFileSystem.exists(path);}public boolean canWrite(){return BrowserFileSystem.exists(path)&&property(path,"readonly")==0;}
  public long lastModified(){return (long)property(path,"mtime");}
  public boolean setLastModified(long time){return mutate("metadata",path,"mtime",null,time);}
  public boolean setReadOnly(boolean value){return mutate("metadata",path,"readonly",null,value?1:0);}
  public int length(){return (int)property(path,"length");}
 }
 @JSBody(params={"path","readable","writable","append"},script="return valthorneFiles.open(path,readable,writable,append);") private static native JSObject open(String path,boolean readable,boolean writable,boolean append);
 private static final class Accessor implements VirtualFileAccessor {
  private final JSObject handle;private final boolean readable,writable;private boolean closed;
  Accessor(JSObject handle,boolean readable,boolean writable){this.handle=handle;this.readable=readable;this.writable=writable;}
  private void check()throws IOException{if(closed)throw new IOException("File is closed");}
  public int read(byte[] data,int offset,int length)throws IOException{check();if(!readable)throw new IOException("File is not readable");Objects.checkFromIndexSize(offset,length,data.length);Uint8Array bytes=readNative(handle,length);for(int i=0;i<bytes.getLength();i++)data[offset+i]=(byte)bytes.get(i);return bytes.getLength();}
  public void write(byte[] data,int offset,int length)throws IOException{check();if(!writable)throw new IOException("File is not writable");Objects.checkFromIndexSize(offset,length,data.length);Uint8Array bytes=Uint8Array.create(length);for(int i=0;i<length;i++)bytes.set(i,(short)(data[offset+i]&255));error(writeNative(handle,bytes));}
  public int tell()throws IOException{check();return position(handle);}public int size()throws IOException{check();return sizeNative(handle);}
  public void seek(int value)throws IOException{check();error(seekNative(handle,value));}public void skip(int value)throws IOException{seek(Math.addExact(tell(),value));}
  public void resize(int size)throws IOException{check();error(resizeNative(handle,size));}
  public void flush()throws IOException{check();finish(handle,false);}
  public void close()throws IOException{if(closed)return;finish(handle,true);closed=true;}
 }
 private static void error(String error)throws IOException{if(error!=null)throw new IOException(error);}
 @JSBody(params={"handle","count"},script="return valthorneFiles.read(handle,count);") private static native Uint8Array readNative(JSObject handle,int count);
 @JSBody(params={"handle","bytes"},script="try{valthorneFiles.write(handle,bytes);return null;}catch(e){return String(e);}") private static native String writeNative(JSObject handle,Uint8Array bytes);
 @JSBody(params={"handle","size"},script="try{valthorneFiles.resize(handle,size);return null;}catch(e){return String(e);}") private static native String resizeNative(JSObject handle,int size);
 @JSBody(params={"handle","position"},script="try{valthorneFiles.seek(handle,position);return null;}catch(e){return String(e);}") private static native String seekNative(JSObject handle,int position);
 @JSBody(params="handle",script="return handle.position;") private static native int position(JSObject handle);
 @JSBody(params="handle",script="return handle.node.length;") private static native int sizeNative(JSObject handle);
 @Async private static native void finish(JSObject handle,boolean close)throws IOException;
 private static void finish(JSObject handle,boolean close,AsyncCallback<Void> callback){finishNative(handle,close,error->{if(error!=null)callback.error(new IOException(error));else callback.complete(null);});}
 @JSFunctor private interface Finished extends JSObject{void accept(String error);}
 @JSBody(params={"handle","close","callback"},script="valthorneFiles[close?'close':'flush'](handle).then(()=>callback(null),e=>callback(String(e)));") private static native void finishNative(JSObject handle,boolean close,Finished callback);
}
