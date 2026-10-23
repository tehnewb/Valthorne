package valthorne.browser;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import valthorne.graphics.bgfx.BgfxResources;
import org.lwjgl.glfw.*;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;

/**
 * Owns the Java side of a canvas application session. Shared JGL lifecycle and
 * input managers remain in use; only their platform bindings differ. The render
 * loop suspends between animation frames, and DOM events resume that same
 * coroutine rather than executing application code concurrently.
 *
 * <p>The generated browser launcher initializes assets and the bgfx WebAssembly
 * module before entering the application's existing main method. Engine shutdown
 * detaches listeners and cancels outstanding frame requests.</p>
 */
public final class BrowserRuntime {
    /**
     * Copied events waiting for the application coroutine.
     */
    private static final ArrayDeque<BrowserInputEvent> input = new ArrayDeque<>();
    /**
     * Suspended frame wait, or null while Java is executing or awaiting asset I/O.
     */
    private static AsyncCallback<Void> pendingFrame;
    /**
     * Registered windowFocus callback, or null when detached.
     */
    private static GLFWWindowFocusCallbackI windowFocusCallback;
    /**
     * Registered windowClose callback, or null when detached.
     */
    private static GLFWWindowCloseCallbackI windowCloseCallback;
    /**
     * Registered drop callback, or null when detached.
     */
    private static GLFWDropCallbackI dropCallback;
    /**
     * Registered windowPos callback, or null when detached.
     */
    private static GLFWWindowPosCallbackI windowPosCallback;
    /**
     * Registered windowSize callback, or null when detached.
     */
    private static GLFWWindowSizeCallbackI windowSizeCallback;
    /**
     * Registered framebufferSize callback, or null when detached.
     */
    private static GLFWFramebufferSizeCallbackI framebufferSizeCallback;
    /**
     * Registered char callback, or null when detached.
     */
    private static GLFWCharCallbackI charCallback;
    /**
     * Registered key callback, or null when detached.
     */
    private static GLFWKeyCallbackI keyCallback;
    /**
     * Registered cursorPos callback, or null when detached.
     */
    private static GLFWCursorPosCallbackI cursorPosCallback;
    /**
     * Registered mouseButton callback, or null when detached.
     */
    private static GLFWMouseButtonCallbackI mouseButtonCallback;
    /**
     * Registered scroll callback, or null when detached.
     */
    private static GLFWScrollCallbackI scrollCallback;

    /**
     * Prevents constructing process-wide browser sessions.
     */
    private BrowserRuntime() {
    }

    /**
     * Checks browser bootstrap and installs the input route.
     */
    public static void requireHost() {
        /*
         * Retain browser state on the single application coroutine.
         */
        if (!loaded()) throw new IllegalStateException("Load the generated Valthorne browser launcher before starting JGL");
        BrowserFiles.home();
        install(BrowserRuntime::post);
    }

    /**
     * Checks whether native browser loading completed.
     * @return browser operation result
     */
    @JSBody(script = "return !!globalThis.valthorneHost;")
    private static native boolean loaded();

    /**
     * Installs the coroutine-safe input route.
     * @param receiver browser value
     */
    @JSBody(params = {"receiver"}, script = "globalThis.valthorneHost.installInput(receiver);")
    private static native void install(BrowserInput receiver);

    /**
     * Waits for a frame and dispatches queued DOM events.
     */
    public static void poll() {
        try {
            BrowserFiles.flush(false);
        } catch (IOException failure) {
            throw new UncheckedIOException("Browser save failed", failure);
        }
        /*
         * Retain browser state on the single application coroutine.
         */
        if (input.isEmpty() || contextLost()) awaitFrame();
        BrowserInputEvent event;
        while ((event = input.poll()) != null) dispatch(event);
    }

    /**
     * Suspends the application until a frame or trusted input arrives.
     */
    @Async
    private static native void awaitFrame();

    /**
     * Installs one pending coroutine continuation.
     * @param callback suspended application continuation
     */
    private static void awaitFrame(AsyncCallback<Void> callback) {
        /*
         * Never start a second frame while application code is waiting on I/O.
         */
        pendingFrame = callback;
        if (!input.isEmpty() && !contextLost()) wake();
        else requestFrame(BrowserRuntime::wake);
    }

    /**
     * Resumes the pending application wait at most once.
     */
    private static void wake() {
        /*
         * Clear the continuation before resumption to prevent callback reentry.
         */
        AsyncCallback<Void> callback = pendingFrame;
        pendingFrame = null;
        if (callback != null) callback.complete(null);
    }

    /**
     * Copies DOM input and wakes an idle application in the trusted event task.
     * @param kind event category
     * @param a first integer payload
     * @param b second integer payload
     * @param c third integer payload
     * @param d fourth integer payload
     * @param x horizontal payload
     * @param y vertical payload
     * @param text optional dropped-file path
     */
    private static void post(int kind, int a, int b, int c, int d, double x, double y, String text) {
        /*
         * Only a suspended frame wait can resume; active Java work keeps input queued.
         */
        BrowserInputEvent event = new BrowserInputEvent(kind, a, b, c, d, x, y, text);
        if (kind == 10) input.addFirst(event);
        else input.add(event);
        if (kind == 8 || !contextLost()) wake();
    }

    /**
     * Delivers one copied event through the unchanged engine input managers.
     * @param event next queued event
     */
    private static void dispatch(BrowserInputEvent event) {
        /*
         * Window token one identifies the single canvas presentation.
         */
        switch (event.kind()) {
            case 0 -> { if (keyCallback != null) keyCallback.invoke(1, event.a(), event.b(), event.c(), event.d()); }
            case 1 -> { if (charCallback != null) charCallback.invoke(1, event.a()); }
            case 2 -> { if (cursorPosCallback != null) cursorPosCallback.invoke(1, event.x(), event.y()); }
            case 3 -> { if (mouseButtonCallback != null) mouseButtonCallback.invoke(1, event.a(), event.b(), event.c()); }
            case 4 -> { if (scrollCallback != null) scrollCallback.invoke(1, event.x(), event.y()); }
            case 5 -> { if (windowFocusCallback != null) windowFocusCallback.invoke(1, event.a() != 0); }
            case 6 -> {
                if (windowSizeCallback != null) windowSizeCallback.invoke(1, event.a(), event.b());
                if (framebufferSizeCallback != null) framebufferSizeCallback.invoke(1, event.c(), event.d());
            }
            case 7 -> {
                try {
                    if (dropCallback != null) dropCallback.invoke(1, event.a(), event.b());
                } finally {
                    releaseDrop(event.b());
                }
            }
            case 8 -> { if (windowCloseCallback != null) windowCloseCallback.invoke(1); }
            case 9 -> { if (windowPosCallback != null) windowPosCallback.invoke(1, event.a(), event.b()); }
            case 10 -> BgfxResources.recoverDevice();
            default -> throw new IllegalArgumentException("Unknown browser input category: " + event.kind());
        }
    }

    /**
     * Requests one browser animation-frame continuation.
     * @param wake browser value
     */
    @JSBody(params = {"wake"}, script = "globalThis.valthorneHost.requestFrame(wake);")
    private static native void requestFrame(BrowserWake wake);

    /**
     * Checks whether presentation must wait for WebGL context restoration.
     * @return whether the browser currently owns a lost graphics context
     */
    @JSBody(script = "return globalThis.valthorneHost.contextLost&&!globalThis.valthorneHost.closing;")
    private static native boolean contextLost();

    /**
     * Clears Java callbacks and detaches DOM event ownership.
     */
    public static void close() {
        try {
            BrowserFiles.flush(true);
        } catch (IOException failure) {
            throw new UncheckedIOException("Browser save failed during shutdown", failure);
        } finally {
            /*
             * Retain browser state on the single application coroutine.
             */
            input.clear();
            pendingFrame = null;
            windowFocusCallback = null;
            windowCloseCallback = null;
            dropCallback = null;
            windowPosCallback = null;
            windowSizeCallback = null;
            framebufferSizeCallback = null;
            charCallback = null;
            keyCallback = null;
            cursorPosCallback = null;
            mouseButtonCallback = null;
            scrollCallback = null;
            closeHost();
        }
    }

    /**
     * Ends DOM ownership and cancels pending frame scheduling.
     */
    @JSBody(script = "globalThis.valthorneHost.close();")
    private static native void closeHost();

    /**
     * Replaces a retained windowFocus callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWWindowFocusCallbackI windowFocus(GLFWWindowFocusCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWWindowFocusCallbackI previous = windowFocusCallback;
        windowFocusCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained windowClose callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWWindowCloseCallbackI windowClose(GLFWWindowCloseCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWWindowCloseCallbackI previous = windowCloseCallback;
        windowCloseCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained drop callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWDropCallbackI drop(GLFWDropCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWDropCallbackI previous = dropCallback;
        dropCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained windowPos callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWWindowPosCallbackI windowPos(GLFWWindowPosCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWWindowPosCallbackI previous = windowPosCallback;
        windowPosCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained windowSize callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWWindowSizeCallbackI windowSize(GLFWWindowSizeCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWWindowSizeCallbackI previous = windowSizeCallback;
        windowSizeCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained framebufferSize callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWFramebufferSizeCallbackI framebufferSize(GLFWFramebufferSizeCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWFramebufferSizeCallbackI previous = framebufferSizeCallback;
        framebufferSizeCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained character callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWCharCallbackI character(GLFWCharCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWCharCallbackI previous = charCallback;
        charCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained key callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWKeyCallbackI key(GLFWKeyCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWKeyCallbackI previous = keyCallback;
        keyCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained cursorPos callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWCursorPosCallbackI cursorPos(GLFWCursorPosCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWCursorPosCallbackI previous = cursorPosCallback;
        cursorPosCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained mouseButton callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWMouseButtonCallbackI mouseButton(GLFWMouseButtonCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWMouseButtonCallbackI previous = mouseButtonCallback;
        mouseButtonCallback = callback;
        return previous;
    }

    /**
     * Replaces a retained scroll callback.
     * @param callback copied browser value
     * @return browser operation result
     */
    public static GLFWScrollCallbackI scroll(GLFWScrollCallbackI callback) {
        /*
         * Retain browser state on the single application coroutine.
         */
        GLFWScrollCallbackI previous = scrollCallback;
        scrollCallback = callback;
        return previous;
    }

    /**
     * Accesses canvas time state.
     * @return browser operation result
     */
    @JSBody(script = "return performance.now()/1000;")
    public static native double time();

    /**
     * Accesses canvas hint state.
     * @param hint browser value
     * @param value browser value
     */
    @JSBody(params = {"hint", "value"}, script = "globalThis.valthorneHost.hint(hint,value);")
    public static native void hint(int hint, int value);

    /**
     * Accesses canvas createWindow state.
     * @param width browser value
     * @param height browser value
     * @param title browser value
     * @param fullscreen browser value
     */
    @JSBody(params = {"width", "height", "title", "fullscreen"}, script = "globalThis.valthorneHost.createWindow(width,height,title,fullscreen);")
    public static native void createWindow(int width, int height, String title, boolean fullscreen);

    /**
     * Accesses canvas destroyWindow state.
     */
    @JSBody(script = "globalThis.valthorneHost.destroyWindow();")
    public static native void destroyWindow();

    /**
     * Accesses canvas width state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.width;")
    public static native int width();

    /**
     * Accesses canvas height state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.height;")
    public static native int height();

    /**
     * Accesses canvas pixelWidth state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.canvas.width;")
    public static native int pixelWidth();

    /**
     * Accesses canvas pixelHeight state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.canvas.height;")
    public static native int pixelHeight();

    /**
     * Accesses canvas attribute state.
     * @param attribute browser value
     * @return browser operation result
     */
    @JSBody(params = {"attribute"}, script = "return globalThis.valthorneHost.attribute(attribute);")
    public static native int attribute(int attribute);

    /**
     * Accesses canvas attribute state.
     * @param attribute browser value
     * @param value browser value
     */
    @JSBody(params = {"attribute", "value"}, script = "globalThis.valthorneHost.setAttribute(attribute,value);")
    public static native void attribute(int attribute, int value);

    /**
     * Accesses canvas size state.
     * @param width browser value
     * @param height browser value
     */
    @JSBody(params = {"width", "height"}, script = "globalThis.valthorneHost.size(width,height);")
    public static native void size(int width, int height);

    /**
     * Accesses canvas title state.
     * @param title browser value
     */
    @JSBody(params = {"title"}, script = "document.title=title;")
    public static native void title(String title);

    /**
     * Accesses canvas shouldClose state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.closing;")
    public static native boolean shouldClose();

    /**
     * Accesses canvas requestClose state.
     * @param close browser value
     */
    @JSBody(params = {"close"}, script = "globalThis.valthorneHost.closing=close;")
    public static native void requestClose(boolean close);

    /**
     * Accesses canvas show state.
     */
    @JSBody(script = "globalThis.valthorneHost.show();")
    public static native void show();

    /**
     * Accesses canvas hide state.
     */
    @JSBody(script = "globalThis.valthorneHost.hide();")
    public static native void hide();

    /**
     * Accesses canvas focus state.
     */
    @JSBody(script = "globalThis.valthorneHost.canvas.focus();")
    public static native void focus();

    /**
     * Accesses canvas position state.
     * @param x browser value
     * @param y browser value
     */
    @JSBody(params = {"x", "y"}, script = "globalThis.valthorneHost.position(x,y);")
    public static native void position(int x, int y);

    /**
     * Accesses canvas screenWidth state.
     * @return browser operation result
     */
    @JSBody(script = "return screen.availWidth;")
    public static native int screenWidth();

    /**
     * Accesses canvas screenHeight state.
     * @return browser operation result
     */
    @JSBody(script = "return screen.availHeight;")
    public static native int screenHeight();

    /**
     * Accesses canvas fullscreen state.
     * @param value browser value
     */
    @JSBody(params = {"value"}, script = "globalThis.valthorneHost.fullscreen(value);")
    public static native void fullscreen(boolean value);

    /**
     * Accesses canvas maximize state.
     */
    @JSBody(script = "globalThis.valthorneHost.size(innerWidth,innerHeight);")
    public static native void maximize();

    /**
     * Accesses canvas opacity state.
     * @param value browser value
     */
    @JSBody(params = {"value"}, script = "globalThis.valthorneHost.canvas.style.opacity=value;")
    public static native void opacity(float value);

    /**
     * Accesses canvas limits state.
     * @param minWidth browser value
     * @param minHeight browser value
     * @param maxWidth browser value
     * @param maxHeight browser value
     */
    @JSBody(params = {"minWidth", "minHeight", "maxWidth", "maxHeight"}, script = "globalThis.valthorneHost.limits(minWidth,minHeight,maxWidth,maxHeight);")
    public static native void limits(int minWidth, int minHeight, int maxWidth, int maxHeight);

    /**
     * Accesses canvas cursorX state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.cursorX;")
    public static native double cursorX();

    /**
     * Accesses canvas cursorY state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.cursorY;")
    public static native double cursorY();

    /**
     * Accesses canvas cursorPosition state.
     * @param x browser value
     * @param y browser value
     */
    @JSBody(params = {"x", "y"}, script = "globalThis.valthorneHost.cursorX=x;globalThis.valthorneHost.cursorY=y;")
    public static native void cursorPosition(double x, double y);

    /**
     * Accesses canvas inputMode state.
     * @param mode browser value
     * @param value browser value
     */
    @JSBody(params = {"mode", "value"}, script = "globalThis.valthorneHost.inputMode(mode,value);")
    public static native void inputMode(int mode, int value);

    /**
     * Accesses canvas releaseCursor state.
     * @param cursor browser value
     */
    @JSBody(params = {"cursor"}, script = "globalThis.valthorneHost.releaseCursor(cursor);")
    public static native void releaseCursor(int cursor);

    /**
     * Accesses canvas cursor state.
     * @param cursor browser value
     */
    @JSBody(params = {"cursor"}, script = "globalThis.valthorneHost.setCursor(cursor);")
    public static native void cursor(int cursor);

    /**
     * Accesses canvas createCursor state.
     * @param pixels browser value
     * @param width browser value
     * @param height browser value
     * @param x browser value
     * @param y browser value
     * @return browser operation result
     */
    @JSBody(params = {"pixels", "width", "height", "x", "y"}, script = "return globalThis.valthorneHost.createCursor(pixels,width,height,x,y);")
    public static native int createCursor(@JSBuffer(JSBufferType.UINT8) ByteBuffer pixels, int width, int height, int x, int y);

    /**
     * Accesses canvas icon state.
     * @param pixels browser value
     * @param width browser value
     * @param height browser value
     */
    @JSBody(params = {"pixels", "width", "height"}, script = "globalThis.valthorneHost.icon(pixels,width,height);")
    public static native void icon(@JSBuffer(JSBufferType.UINT8) ByteBuffer pixels, int width, int height);

    /**
     * Accesses canvas capsLock state.
     * @return browser operation result
     */
    @JSBody(script = "return globalThis.valthorneHost.capsLock;")
    public static native boolean capsLock();

    /**
     * Accesses canvas dropName state.
     * @param token copied drop batch identifier
     * @param index file index within the batch
     * @return browser operation result
     */
    @JSBody(params = {"token", "index"}, script = "return globalThis.valthorneHost.dropBatches.get(token)[index];")
    public static native String dropName(int token, int index);

    /**
     * Releases batch metadata after its synchronous input callback completes.
     * @param token delivered drop batch identifier
     */
    @JSBody(params = "token", script = "globalThis.valthorneHost.dropBatches.delete(token);")
    private static native void releaseDrop(int token);

    /**
     * Accesses canvas capture state.
     * @param path browser value
     */
    @JSBody(params = {"path"}, script = "globalThis.valthorneHost.capture(path);")
    public static native void capture(String path);

    /**
     * Resolves the uniquely attached canvas supplied by the browser launcher.
     * @return escaped selector for the current application canvas
     */
    @JSBody(script = "return globalThis.valthorneHost.canvasSelector;")
    public static native String canvasSelector();

    /**
     * Dispatches hyperlink activation without invoking desktop launchers. Relative
     * HTTP(S), mail and telephone destinations are supported; script, file and
     * other privileged schemes are rejected. Call within a trusted activation
     * event so browser pop-up policy can allow the new browsing context.
     *
     * @param link nonnull destination resolved against the hosting document
     * @return whether a supported request was dispatched, not whether it loaded
     */
    @JSBody(params = "link", script = "if (!link || !link.trim()) return false; var url; try { url=new URL(link,document.baseURI); } catch(error) { return false; } if (['http:','https:','mailto:','tel:'].indexOf(url.protocol)<0) return false; var anchor=document.createElement('a'); anchor.href=url.href; anchor.target='_blank'; anchor.rel='noopener noreferrer'; anchor.click(); return true;")
    public static native boolean openLink(String link);

}
