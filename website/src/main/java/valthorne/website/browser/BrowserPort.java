package valthorne.website.browser;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.dom.html.HTMLCanvasElement;

/**
 * Primitive bindings to the portable browser graphics adapter.
 *
 * <p>This class deliberately contains no page, route, font-family, animation,
 * or input policy. Java chooses those behaviors and passes concrete values to
 * the browser's graphics resources. JavaScript bodies only bridge one host
 * operation or property.</p>
 */
public final class BrowserPort {
    private BrowserPort() { }

    /** Loads the graphics backends only when the Java application requests them. */
    @JSBody(script = "return valthornePort.initialize();")
    public static native JSPromise<JSObject> initialize();

    /** Callback invoked once the portable application connects its frame loop. */
    @JSFunctor
    public interface Action extends JSObject { void run(); }

    /** Delivers a browser graphics failure to the Java application's handler. */
    @JSFunctor
    public interface Failure extends JSObject { void accept(String message); }

    /** Maps an engine face and its registered family to a browser font string. */
    @JSFunctor
    public interface FontResolver extends JSObject {
        String resolve(String face, String registeredFamily, double size);
    }

    @JSBody(params = "callback", script = "valthorneHost.onConnect = callback;")
    public static native void onConnect(Action callback);

    @JSBody(params = "callback", script = "valthorneHost.onFailure = callback;")
    public static native void onFailure(Failure callback);

    @JSBody(params = "resolver", script = "valthorneHost.fontResolver = resolver;")
    public static native void setFontResolver(FontResolver resolver);

    /** Advances the connected Java application; Java owns frame scheduling. */
    @JSBody(params = "delta", script = "valthorneHost.frame(delta);")
    public static native void callFrame(double delta);

    @JSBody(script = "valthorneHost.shutdownApplication();")
    public static native void shutdownApplication();

    @JSBody(script = "valthorneHost.close();")
    public static native void close();

    /** Applies the scale selected by Java to the browser drawing surface. */
    @JSBody(params = "ratio", script = "valthorneHost.resize(ratio);")
    public static native void resize(double ratio);

    /** Applies a generic transform to subsequent vector primitives. */
    public static void setEffect(double opacity, double offset) {
        setOpacity(opacity);
        setOffset(offset);
    }

    @JSBody(params = "opacity", script = "valthorneHost.opacity = opacity;")
    private static native void setOpacity(double opacity);

    @JSBody(params = "offset", script = "valthorneHost.offset = offset;")
    private static native void setOffset(double offset);

    @JSBody(params = "handle", script = "return valthorneHost.face(handle);")
    public static native String getFace(int handle);

    @JSBody(params = {"handle", "text", "size"}, script = "return valthorneHost.measure(handle, text, size);")
    public static native double measure(int handle, String text, double size);

    @JSBody(params = {"handle", "image", "x", "y", "width", "height", "contain", "radius"},
        script = "valthorneHost.drawImage(handle, image, x, y, width, height, contain, radius);")
    public static native void drawImage(int handle, JSObject image, double x, double y,
        double width, double height, boolean contain, double radius);

    @JSBody(params = "handle", script = "return valthorneHost.surface(handle);")
    public static native HTMLCanvasElement surface(int handle);

    @JSBody(script = "return valthorneHost.graphics.canvas;")
    public static native HTMLCanvasElement canvas();
}
