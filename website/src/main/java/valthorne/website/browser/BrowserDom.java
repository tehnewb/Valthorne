package valthorne.website.browser;

import org.teavm.jso.*;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.dom.events.*;
import org.teavm.jso.dom.html.*;

/** Thin bindings for browser primitives missing from TeaVM's standard DOM API. */
public final class BrowserDom {
    private BrowserDom() { }
    public interface MediaQuery extends EventTarget { @JSProperty boolean isMatches(); }
    public interface Pointer extends MouseEvent { @JSProperty int getPointerId(); }
    public interface PageEvent extends Event { @JSProperty boolean isPersisted(); }
    public interface Font extends JSObject { JSPromise<Font> load(); }
    public interface Url extends JSObject {
        @JSProperty String getHref();
        @JSProperty String getPathname();
        @JSProperty SearchParams getSearchParams();
    }
    public interface SearchParams extends JSObject {
        String get(String name); void set(String name, String value); void delete(String name);
    }
    @JSFunctor public interface BooleanValue extends JSObject { boolean get(); }
    @JSFunctor public interface NumberValue extends JSObject { double get(); }
    @JSFunctor public interface StringValue extends JSObject { String get(); }
    @JSFunctor public interface ObjectValue extends JSObject { JSObject get(); }
    @JSFunctor public interface Measure extends JSObject { double measure(int context, String text, double size); }

    @JSBody(script="return document.hidden;") public static native boolean hidden();
    @JSBody(script="return performance.now();") public static native double now();
    @JSBody(params="query",script="return matchMedia(query);") public static native MediaQuery media(String query);
    @JSBody(params={"input","base"},script="return new URL(input,base);") public static native Url url(String input,String base);
    @JSBody(params="element",script="element.focus({preventScroll:true});") public static native void focus(HTMLElement element);
    @JSBody(params={"top","behavior"},script="scrollTo({top:top,behavior:behavior});") public static native void scroll(double top,String behavior);
    @JSBody(params={"element","pointer"},script="element.setPointerCapture(pointer);") public static native void capture(HTMLElement element,int pointer);
    @JSBody(params={"target","name","listener"},script="target.addEventListener(name,listener,{passive:true});")
    public static native void passive(EventTarget target,String name,EventListener<?> listener);
    @JSBody(params={"family","source","weight"},script="return new FontFace(family,source,{weight:weight,style:'normal'});")
    public static native Font font(String family,String source,String weight);
    @JSBody(params="font",script="document.fonts.add(font);") public static native void addFont(Font font);
    @JSBody(params="image",script="return image.decode();") public static native JSPromise<JSObject> decode(HTMLImageElement image);
    @JSBody(params="text",script="return navigator.clipboard.writeText(text);") public static native JSPromise<JSObject> copy(String text);
    @JSBody(params="message",script="console.error(message);") public static native void error(String message);
    @JSBody(script="return {};") public static native JSObject object();
    @JSBody(params={"target","key","value"},script="target[key]=value;") public static native void string(JSObject target,String key,String value);
    @JSBody(params={"name","value"},script="globalThis[name]=value;") public static native void expose(String name,JSObject value);
    @JSBody(params={"name","value"},script="globalThis[name]=value;") public static native void flag(String name,boolean value);
    @JSBody(params={"name","value"},script="globalThis[name]=value;") public static native void exposeString(String name,String value);
    @JSBody(params={"target","key","value"},script="Object.defineProperty(target,key,{get:value});")
    public static native void booleanGetter(JSObject target,String key,BooleanValue value);
    @JSBody(params={"target","key","value"},script="Object.defineProperty(target,key,{get:value});")
    public static native void numberGetter(JSObject target,String key,NumberValue value);
    @JSBody(params={"target","key","value"},script="Object.defineProperty(target,key,{get:value});")
    public static native void stringGetter(JSObject target,String key,StringValue value);
    @JSBody(params={"target","key","value"},script="Object.defineProperty(target,key,{get:value});")
    public static native void objectGetter(JSObject target,String key,ObjectValue value);
    @JSBody(params={"target","value"},script="target.measure=value;") public static native void measureFunction(JSObject target,Measure value);
}
