package valthorne.website.browser;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.EventTarget;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLImageElement;

/**
 * Thin bindings for browser primitives missing from TeaVM's standard DOM API.
 */
public final class BrowserDom {
    private BrowserDom() {}

    /** Fragment navigation supplies Java-computed scroll targets. */
    @JSBody(script = "history.scrollRestoration = 'manual';")
    public static native void manualScrollRestoration();

    /** Indicates when the document should stop scheduling drawing work. */
    @JSBody(script = "return document.hidden;")
    public static native boolean hidden();

    /** Creates a browser media-query handle for Java preference listeners. */
    @JSBody(params = "query", script = "return matchMedia(query);")
    public static native MediaQuery media(String query);

    /** Resolves a URL using the browser's standard URL parser. */
    @JSBody(params = {"input", "base"}, script = "return new URL(input,base);")
    public static native Url url(String input, String base);

    /** Moves keyboard focus without disturbing Java's chosen scroll position. */
    @JSBody(params = "element", script = "element.focus({preventScroll:true});")
    public static native void focus(HTMLElement element);

    /** Scrolls to the document coordinate and behavior selected by Java. */
    @JSBody(params = {"top", "behavior"}, script = "scrollTo({top:top,behavior:behavior});")
    public static native void scroll(double top, String behavior);

    /** Registers a listener that never cancels native browser scrolling. */
    @JSBody(params = {"target", "name", "listener"}, script = "target.addEventListener(name,listener,{passive:true});")
    public static native void passive(EventTarget target, String name, EventListener<?> listener);

    /** Creates a normal-style font face using Java-selected source and weights. */
    @JSBody(params = {"family", "source", "weight"}, script = "return new FontFace(family,source,{weight:weight,style:'normal'});")
    public static native Font font(String family, String source, String weight);

    /** Registers a loaded face for browser drawing and text measurement. */
    @JSBody(params = "font", script = "document.fonts.add(font);")
    public static native void addFont(Font font);

    /** Awaits native image decoding before the first painted frame. */
    @JSBody(params = "image", script = "return image.decode();")
    public static native JSPromise<JSObject> decode(HTMLImageElement image);

    /** Records an application error in the browser console. */
    @JSBody(params = "message", script = "console.error(message);")
    public static native void error(String message);

    /** Creates an empty native object for read-only diagnostic properties. */
    @JSBody(script = "return {};")
    public static native JSObject object();

    /** Publishes a Java-built diagnostic object for browser verification. */
    @JSBody(params = {"name", "value"}, script = "globalThis[name]=value;")
    public static native void expose(String name, JSObject value);

    /** Publishes a lifecycle flag for browser verification. */
    @JSBody(params = {"name", "value"}, script = "globalThis[name]=value;")
    public static native void flag(String name, boolean value);

    /** Publishes a diagnostic message without exposing mutable Java state. */
    @JSBody(params = {"name", "value"}, script = "globalThis[name]=value;")
    public static native void exposeString(String name, String value);

    /** Exposes a read-only boolean computed by Java. */
    @JSBody(params = {"target", "key", "value"}, script = "Object.defineProperty(target,key,{get:value});")
    public static native void booleanGetter(JSObject target, String key, BooleanValue value);

    /** Exposes a read-only number computed by Java. */
    @JSBody(params = {"target", "key", "value"}, script = "Object.defineProperty(target,key,{get:value});")
    public static native void numberGetter(JSObject target, String key, NumberValue value);

    /** Exposes a read-only string computed by Java. */
    @JSBody(params = {"target", "key", "value"}, script = "Object.defineProperty(target,key,{get:value});")
    public static native void stringGetter(JSObject target, String key, StringValue value);

    /** Native media-query result with change-event support. */
    public interface MediaQuery extends EventTarget {
        @JSProperty
        boolean isMatches();
    }

    /** Browser lifecycle event indicating whether a page enters the back/forward cache. */
    public interface PageEvent extends Event {
        @JSProperty
        boolean isPersisted();
    }

    /** A font face whose loading promise can be awaited by the Java coroutine. */
    public interface Font extends JSObject {
        JSPromise<Font> load();
    }

    /** Native URL properties used for fragment navigation and text-view selection. */
    public interface Url extends JSObject {
        @JSProperty
        String getHash();

        @JSProperty
        String getHref();

        @JSProperty
        SearchParams getSearchParams();
    }

    /** Query-string operations needed to enter and leave the text view. */
    public interface SearchParams extends JSObject {
        String get(String name);

        void delete(String name);
    }

    /** Java callback underlying a read-only boolean diagnostic. */
    @JSFunctor
    public interface BooleanValue extends JSObject {
        boolean get();
    }

    /** Java callback underlying a read-only numeric diagnostic. */
    @JSFunctor
    public interface NumberValue extends JSObject {
        double get();
    }

    /** Java callback underlying a read-only string diagnostic. */
    @JSFunctor
    public interface StringValue extends JSObject {
        String get();
    }
}
