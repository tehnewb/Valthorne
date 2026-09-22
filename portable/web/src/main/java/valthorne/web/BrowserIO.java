package valthorne.web;
import org.teavm.interop.*;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Uint8Array;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
/** Shared asynchronous browser I/O behind synchronous engine entry points. */
public final class BrowserIO {
 private BrowserIO(){}
 public static byte[] readLocal(String path){
  Objects.requireNonNull(path);if(path.contains("://")||path.startsWith("blob:")||path.startsWith("data:"))return null;
  try{var file=Path.of(path);return Files.isRegularFile(file)?Files.readAllBytes(file):null;}catch(IOException error){throw new UncheckedIOException(error);}
 }
 public static String resolveSibling(String source,String relative){
  source=Objects.requireNonNull(source).replace('\\','/');relative=Objects.requireNonNull(relative).replace('\\','/');
  if(relative.contains("://"))return relative;
  String path=relative.startsWith("/")?relative:source.substring(0,source.lastIndexOf('/')+1)+relative;
  String prefix="";int scheme=path.indexOf("://");if(scheme>=0){int start=path.indexOf('/',scheme+3);if(start<0)return path;prefix=path.substring(0,start);path=path.substring(start);}
  boolean absolute=path.startsWith("/");var pieces=new ArrayDeque<String>();for(String piece:path.split("/")){if(piece.isEmpty()||piece.equals("."))continue;if(piece.equals("..")&&!pieces.isEmpty()&&!pieces.peekLast().equals(".."))pieces.removeLast();else if(!piece.equals("..")||!absolute)pieces.addLast(piece);}
  return prefix+(absolute?"/":"")+String.join("/",pieces);
 }
 @Async public static native byte[] read(String path);
 private static void read(String path,AsyncCallback<byte[]> callback){byte[] local=readLocal(path);if(local!=null){callback.complete(local);return;}fetch(path,(bytes,error)->{if(error!=null){callback.error(new IllegalArgumentException(error));return;}byte[] result=new byte[bytes.getLength()];for(int i=0;i<result.length;i++)result[i]=(byte)bytes.get(i);callback.complete(result);});}
 @JSFunctor private interface Loaded extends JSObject{void accept(Uint8Array bytes,String error);}
 @JSBody(params={"path","callback"},script="fetch(path,{signal:AbortSignal.timeout(30000)}).then(r=>{if(!r.ok)throw new Error('HTTP '+r.status+': '+path);return r.arrayBuffer();}).then(bytes=>callback(new Uint8Array(bytes),null),error=>callback(null,String(error)));") private static native void fetch(String path,Loaded callback);
}
