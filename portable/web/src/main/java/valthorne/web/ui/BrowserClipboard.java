package valthorne.web.ui;
import org.teavm.jso.*;
import org.teavm.interop.*;
/** Clipboard access follows the browser's permission policy; denied access is reported to the caller. */
public final class BrowserClipboard {
 private BrowserClipboard(){}
 @Async public static native boolean write(String text);
 private static void write(String text,AsyncCallback<Boolean> callback){put(text,(value,error)->callback.complete(error==null));}
 @Async public static native String read();
 private static void read(AsyncCallback<String> callback){get((value,error)->callback.complete(error==null?value:null));}
 @JSFunctor private interface Done extends JSObject{void accept(String value,String error);}
 @JSBody(params={"text","done"},script="if(!navigator.clipboard){done(null,'Clipboard unavailable');return;}navigator.clipboard.writeText(text).then(()=>done('',null),e=>done(null,String(e)));") private static native void put(String text,Done done);
 @JSBody(params="done",script="if(!navigator.clipboard){done(null,'Clipboard unavailable');return;}navigator.clipboard.readText().then(value=>done(value,null),e=>done(null,String(e)));") private static native void get(Done done);
}
