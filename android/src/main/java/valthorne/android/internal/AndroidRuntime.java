package valthorne.android.internal;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.Surface;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.lwjgl.glfw.*;
import valthorne.android.ValthorneActivity;
import valthorne.Audio;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxResources;
import valthorne.ui.UINode;
import valthorne.ui.nodes.TextField;
import valthorne.ui.nodes.nano.NanoTextField;
import valthorne.ui.nodes.nano.NanoCodeEditor;

/**
 * Coordinates one native Android activity, surface and application render thread.
 * UI callbacks only copy input and publish surface state. Shared engine callbacks,
 * scene changes and graphics resource recovery execute on the render thread.
 * Backgrounding suspends the application clock and frame loop. A replacement
 * surface restores the existing resource graph without reinitializing scenes.
 * The bounded input queue owns one copied record per UI event; frames allocate
 * no platform event objects when no input arrives.
 */
public final class AndroidRuntime {
    /**
     * Surface and pause coordination between the Android UI and render threads.
     */
    private static final Object lifecycle = new Object();
    /**
     * Pending copied platform events; render callbacks drain these in order.
     */
    private static final ArrayBlockingQueue<AndroidInputEvent> events = new ArrayBlockingQueue<>(1024);
    /**
     * Current Android activity, installed before entering JGL.
     */
    private static ValthorneActivity activity;
    /**
     * Current valid presentation surface, or null while unavailable.
     */
    private static volatile Surface surface;
    /**
     * Physical backbuffer width published by Android layout callbacks.
     */
    private static volatile int pixelsWide;
    /**
     * Physical backbuffer height published by Android layout callbacks.
     */
    private static volatile int pixelsHigh;
    /**
     * Whether the application has requested termination.
     */
    private static volatile boolean closing;
    /**
     * Whether Android has paused the activity.
     */
    private static volatile boolean paused;
    /**
     * Whether a lost surface needs native device and resource recreation.
     */
    private static boolean recover;
    /**
     * Whether the render thread has reached a safe surface-loss boundary.
     */
    private static boolean surfaceAcknowledged;
    /**
     * Baseline logical height taken from the application's window configuration.
     */
    private static volatile int logicalHeight = 900;
    /**
     * Start of the current pause interval in nanoseconds.
     */
    private static long pauseStart;
    /**
     * Paused time removed from the application clock.
     */
    private static long pausedNanos;
    /**
     * Native renderer owned only by the render thread.
     */
    private static volatile AndroidNativeDevice device;
    /**
     * Most recent pointer position in engine logical coordinates.
     */
    private static double cursorX;
    /**
     * Most recent vertical pointer position in engine logical coordinates.
     */
    private static double cursorY;
    /**
     * Window focus callback owned by the engine input managers.
     */
    private static GLFWWindowFocusCallbackI windowFocus;
    /**
     * Close callback owned by the engine window lifecycle.
     */
    private static GLFWWindowCloseCallbackI windowClose;
    /**
     * Window position callback, retained for API compatibility.
     */
    private static GLFWWindowPosCallbackI windowPos;
    /**
     * Logical window resize callback.
     */
    private static GLFWWindowSizeCallbackI windowSize;
    /**
     * Physical framebuffer resize callback.
     */
    private static GLFWFramebufferSizeCallbackI framebufferSize;
    /**
     * Committed character callback.
     */
    private static GLFWCharCallbackI character;
    /**
     * Key press and release callback.
     */
    private static GLFWKeyCallbackI key;
    /**
     * Pointer movement callback.
     */
    private static GLFWCursorPosCallbackI cursorPos;
    /**
     * Primary touch and mouse button callback.
     */
    private static GLFWMouseButtonCallbackI mouseButton;
    /**
     * Mouse or trackpad scroll callback.
     */
    private static GLFWScrollCallbackI scroll;
    /**
     * Drop callback retained for platform API compatibility.
     */
    private static GLFWDropCallbackI drop;

    /**
     * Prevents constructing process-wide platform coordination.
     */
    private AndroidRuntime() {
    }

    /**
     * Binds a new activity before its render thread starts.
     * @param owner current Valthorne activity
     */
    public static void bind(ValthorneActivity owner) {
        synchronized (lifecycle) {
            if (activity != null && !closing) throw new IllegalStateException("Only one Valthorne activity can run per process");
            activity = owner;
            closing = false;
            paused = false;
            recover = false;
            surfaceAcknowledged = false;
            pausedNanos = pauseStart = 0;
            cursorX = cursorY = 0;
            windowFocus = null;
            windowClose = null;
            windowPos = null;
            windowSize = null;
            framebufferSize = null;
            character = null;
            key = null;
            cursorPos = null;
            mouseButton = null;
            scroll = null;
            drop = null;
            events.clear();
        }
    }

    /**
     * Returns the Android owner for platform clipboard, keyboard and links.
     * @return bound activity
     */
    public static ValthorneActivity activity() { return activity; }

    /**
     * Publishes a usable Android surface and its physical dimensions.
     * @param next live surface
     * @param width physical width
     * @param height physical height
     */
    public static void surface(Surface next, int width, int height) {
        synchronized (lifecycle) {
            surface = next;
            pixelsWide = width;
            pixelsHigh = height;
            lifecycle.notifyAll();
        }
        emit(6, 0, 0, 0, 0, 0, 0, null);
    }

    /**
     * Stops drawing before Android releases a destroyed presentation surface.
     */
    public static void loseSurface() {
        synchronized (lifecycle) {
            surface = null;
            recover = device != null;
            surfaceAcknowledged = device == null;
            lifecycle.notifyAll();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (!surfaceAcknowledged && device != null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException("Render thread did not release the Android surface in time");
                try { TimeUnit.NANOSECONDS.timedWait(lifecycle, remaining); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }
        }
    }

    /**
     * Suspends or resumes application time without changing scene state.
     * @param value whether the activity is paused
     */
    public static void pause(boolean value) {
        synchronized (lifecycle) {
            if (paused == value) return;
            if (value) pauseStart = System.nanoTime();
            else pausedNanos += System.nanoTime() - pauseStart;
            paused = value;
            lifecycle.notifyAll();
        }
        emit(5, value ? 0 : 1, 0, 0, 0, 0, 0, null);
        if (Audio.isReady()) Audio.run(() -> AndroidOpenAL.setPaused(value));
    }

    /**
     * Requires an Android launcher before entering shared engine initialization.
     */
    public static void requireHost() { if (activity == null) throw new IllegalStateException("Start Android applications through ValthorneActivity"); }

    /**
     * Drains copied events and restores graphics after a surface replacement.
     */
    public static void poll() {
        boolean restore;
        for (;;) {
            AndroidNativeDevice pending;
            synchronized (lifecycle) {
                if (closing || (surface != null && !paused)) break;
                pending = surfaceAcknowledged ? null : device;
                if (pending == null) {
                    surfaceAcknowledged = true;
                    lifecycle.notifyAll();
                    try {
                        lifecycle.wait();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        closing = true;
                        throw new IllegalStateException(failure);
                    }
                    continue;
                }
            }
            pending.quiesce();
            synchronized (lifecycle) {
                surfaceAcknowledged = true;
                lifecycle.notifyAll();
            }
        }
        synchronized (lifecycle) {
            restore = recover && !closing;
            recover = false;
        }
        if (restore) BgfxResources.recoverDevice();
        AndroidInputEvent event;
        while ((event = events.poll()) != null) dispatch(event);
    }

    /**
     * Copies one UI event; queue saturation reports an unresponsive application.
     */
    public static void emit(int kind, int a, int b, int c, int d, double x, double y, String text) {
        if (!events.offer(new AndroidInputEvent(kind, a, b, c, d, x, y, text)))
            throw new IllegalStateException("Android input queue overflow: application render thread is not processing events");
    }

    /**
     * Delivers one platform event through the ordinary engine callbacks.
     * @param event copied event
     */
    private static void dispatch(AndroidInputEvent event) {
        switch (event.kind()) {
            case 0 -> { if (key != null) key.invoke(1, event.a(), event.b(), event.c(), event.d()); }
            case 1 -> { if (character != null) character.invoke(1, event.a()); }
            case 2 -> { cursorX = event.x(); cursorY = event.y(); if (cursorPos != null) cursorPos.invoke(1, cursorX, cursorY); }
            case 3 -> { if (mouseButton != null) mouseButton.invoke(1, event.a(), event.b(), event.c()); }
            case 4 -> { if (scroll != null) scroll.invoke(1, event.x(), event.y()); }
            case 5 -> { if (windowFocus != null) windowFocus.invoke(1, event.a() != 0); }
            case 6 -> { if (windowSize != null) windowSize.invoke(1, width(), height()); if (framebufferSize != null) framebufferSize.invoke(1, pixelsWide, pixelsHigh); }
            case 8 -> { if (windowClose != null) windowClose.invoke(1); }
            case 11 -> { if (character != null) { for (int i = 0; i < event.text().length();) { int code = event.text().codePointAt(i); character.invoke(1, code); i += Character.charCount(code); } } }
            default -> throw new IllegalArgumentException("Unknown Android input event " + event.kind());
        }
    }

    /**
     * Creates the native renderer after the Android surface becomes usable.
     * @param backend explicit native graphics API
     */
    public static void createDevice(GraphicsBackend backend) {
        if (device != null) throw new IllegalStateException("Android graphics device already exists");
        synchronized (lifecycle) {
            if (surface == null || !surface.isValid()) throw new IllegalStateException("Android presentation surface is unavailable");
            device = new AndroidNativeDevice(surface, backend == GraphicsBackend.VULKAN);
        }
    }

    /**
     * Releases native graphics before replacing its surface.
     */
    public static void destroyDevice() {
        AndroidNativeDevice current = device;
        if (current == null) return;
        try {
            current.close();
        } finally {
            synchronized (lifecycle) {
                device = null;
                surfaceAcknowledged = true;
                lifecycle.notifyAll();
            }
        }
    }

    /**
     * Removes paused intervals from the monotonic application clock.
     * @return elapsed seconds
     */
    public static double time() { synchronized (lifecycle) { return ((paused ? pauseStart : System.nanoTime()) - pausedNanos) * 1e-9; } }

    /**
     * Selects the application's logical coordinate scale independently of density.
     */
    public static void createWindow(int width, int height, String title, boolean fullscreen) { logicalHeight = height; title(title); }
    /**
     * Returns the logical width preserving the current physical aspect ratio.
     */
    public static int width() { return Math.max(1, Math.round((float) pixelsWide * logicalHeight / Math.max(1, pixelsHigh))); }
    /**
     * Returns the configured logical viewport height.
     */
    public static int height() { return logicalHeight; }
    /**
     * Returns the current physical surface width.
     */
    public static int pixelWidth() { return pixelsWide; }
    /**
     * Returns the current physical surface height.
     */
    public static int pixelHeight() { return pixelsHigh; }
    /**
     * Returns whether shutdown has been requested.
     */
    public static boolean shouldClose() { return closing; }
    /**
     * Requests termination without running application callbacks on the UI thread.
     */
    public static void requestClose(boolean value) { closing = value; synchronized (lifecycle) { lifecycle.notifyAll(); } }
    /**
     * Finishes the activity after the shared engine releases its resources.
     */
    public static void close() {
        ValthorneActivity owner = activity;
        closing = true;
        owner.runOnUiThread(() -> {
            if (!owner.isDestroyed() && !owner.isChangingConfigurations()) owner.finish();
        });
        synchronized (lifecycle) {
            lifecycle.notifyAll();
        }
    }
    /**
     * Releases the logical window after native renderer shutdown.
     */
    public static void destroyWindow() { }
    /**
     * Retains platform-independent startup hints; Android owns window creation.
     */
    public static void hint(int hint, int value) { }
    /**
     * Reads no-client-API, visibility and activity-focus attributes.
     */
    public static int attribute(int attribute) { return attribute == 0x00022001 ? 0 : attribute == 0x00020004 ? 1 : attribute == 0x00020001 ? (paused ? 0 : 1) : 0; }
    /**
     * Android activity flags own desktop-only window attributes.
     */
    public static void attribute(int attribute, int value) { }
    /**
     * Updates the logical scale; Android controls the physical surface extent.
     */
    public static void size(int width, int height) { logicalHeight = height; }
    /**
     * Updates the Android task title on its UI thread.
     */
    public static void title(String title) { activity.runOnUiThread(() -> activity.setTitle(title)); }
    /**
     * The Android activity already owns visibility when JGL starts.
     */
    public static void show() { }
    /**
     * Moves the activity to the background without destroying application state.
     */
    public static void hide() { activity.runOnUiThread(() -> activity.moveTaskToBack(true)); }
    /**
     * Focuses the native surface on the Android UI thread.
     */
    public static void focus() { activity.runOnUiThread(() -> activity.surfaceView().requestFocus()); }
    /**
     * Android controls the activity's screen position.
     */
    public static void position(int x, int y) { }
    /**
     * Returns the logical display width.
     */
    public static int screenWidth() { return width(); }
    /**
     * Returns the logical display height.
     */
    public static int screenHeight() { return height(); }
    /**
     * Applies fullscreen through the activity's system-bar controller.
     */
    public static void fullscreen(boolean value) { activity.runOnUiThread(() -> activity.fullscreen(value)); }
    /**
     * Android owns the activity extent; the surface already fills that extent.
     */
    public static void maximize() { }
    /**
     * Sets activity content opacity on the UI thread.
     */
    public static void opacity(float value) { activity.runOnUiThread(() -> activity.surfaceView().setAlpha(value)); }
    /**
     * Android screen layout determines surface limits.
     */
    public static void limits(int minWidth, int minHeight, int maxWidth, int maxHeight) { }
    /**
     * Returns the last logical pointer x coordinate.
     */
    public static double cursorX() { return cursorX; }
    /**
     * Returns the last logical pointer y coordinate.
     */
    public static double cursorY() { return cursorY; }
    /**
     * Updates the engine's virtual pointer position.
     */
    public static void cursorPosition(double x, double y) { emit(2, 0, 0, 0, 0, x, y, null); }
    /**
     * Touch input does not capture or hide a physical mouse cursor.
     */
    public static void inputMode(int mode, int value) { }
    /**
     * Releases an Android standard cursor token, which owns no native memory.
     */
    public static void releaseCursor(int cursor) { }
    /**
     * Android selects the visible system pointer for attached input devices.
     */
    public static void cursor(int cursor) { }
    /**
     * Reports unsupported custom pointer images explicitly.
     */
    public static int createCursor(ByteBuffer pixels, int width, int height, int x, int y) { throw new UnsupportedOperationException("Android custom cursors are not implemented"); }
    /**
     * Runtime window icons cannot replace the Android launcher icon.
     */
    public static void icon(ByteBuffer pixels, int width, int height) { }
    /**
     * Returns the default caps-lock state when no hardware lock event is supplied.
     */
    public static boolean capsLock() { return false; }
    /**
     * Rejects desktop file-drop lookup on the Android platform.
     */
    public static String dropName(int token, int index) { throw new UnsupportedOperationException("Use Android document selection instead of desktop file drops"); }
    /**
     * Requests additional display pacing for nonstandard swap divisors.
     */
    public static void waitRefreshes(int refreshes) { if (refreshes > 1) { try { Thread.sleep((refreshes - 1) * 16L); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); } } }
    /**
     * Reports the absent browser selector; native Android uses a Surface.
     */
    public static String canvasSelector() { throw new UnsupportedOperationException("Native Android does not use a canvas selector"); }
    /**
     * Selects the software keyboard for the focused engine text control.
     */
    public static void textFocus(UINode node) {
        boolean enabled = node instanceof TextField || node instanceof NanoTextField || node instanceof NanoCodeEditor;
        boolean masked = node instanceof TextField field && field.isMasking() || node instanceof NanoTextField nanoField && nanoField.isMasking();
        activity.runOnUiThread(() -> activity.surfaceView().textFocus(enabled, masked));
    }
    /**
     * Opens a supported external URI using Android's normal activity resolver.
     */
    public static boolean openLink(String link) { activity.runOnUiThread(() -> activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)))); return true; }
    /**
     * Captures a completed native surface into the application's private files.
     */
    public static void capture(String path) {
        Bitmap bitmap = Bitmap.createBitmap(pixelsWide, pixelsHigh, Bitmap.Config.ARGB_8888);
        CountDownLatch done = new CountDownLatch(1);
        int[] result = {PixelCopy.ERROR_UNKNOWN};
        PixelCopy.request(surface, bitmap, value -> { result[0] = value; done.countDown(); }, new Handler(Looper.getMainLooper()));
        try {
            if (!done.await(5, TimeUnit.SECONDS) || result[0] != PixelCopy.SUCCESS) throw new IllegalStateException("Android surface capture failed");
            Path destination = activity.getFilesDir().toPath().resolve(path);
            try (OutputStream output = Files.newOutputStream(destination)) { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG encoding failed"); }
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        catch (IOException failure) { throw new IllegalStateException("Android capture could not be written", failure); }
        finally { bitmap.recycle(); }
    }

    /**
     * Registers the shared window focus callback, returning the previous callback.
     */
    public static GLFWWindowFocusCallbackI windowFocus(GLFWWindowFocusCallbackI callback) { GLFWWindowFocusCallbackI previous = windowFocus; windowFocus = callback; return previous; }
    /**
     * Registers the shared window close callback, returning the previous callback.
     */
    public static GLFWWindowCloseCallbackI windowClose(GLFWWindowCloseCallbackI callback) { GLFWWindowCloseCallbackI previous = windowClose; windowClose = callback; return previous; }
    /**
     * Registers the shared window position callback.
     */
    public static GLFWWindowPosCallbackI windowPos(GLFWWindowPosCallbackI callback) { GLFWWindowPosCallbackI previous = windowPos; windowPos = callback; return previous; }
    /**
     * Registers logical window resize delivery.
     */
    public static GLFWWindowSizeCallbackI windowSize(GLFWWindowSizeCallbackI callback) { GLFWWindowSizeCallbackI previous = windowSize; windowSize = callback; return previous; }
    /**
     * Registers physical framebuffer resize delivery.
     */
    public static GLFWFramebufferSizeCallbackI framebufferSize(GLFWFramebufferSizeCallbackI callback) { GLFWFramebufferSizeCallbackI previous = framebufferSize; framebufferSize = callback; return previous; }
    /**
     * Registers committed text delivery.
     */
    public static GLFWCharCallbackI character(GLFWCharCallbackI callback) { GLFWCharCallbackI previous = character; character = callback; return previous; }
    /**
     * Registers hardware and software editing key delivery.
     */
    public static GLFWKeyCallbackI key(GLFWKeyCallbackI callback) { GLFWKeyCallbackI previous = key; key = callback; return previous; }
    /**
     * Registers primary touch and mouse movement delivery.
     */
    public static GLFWCursorPosCallbackI cursorPos(GLFWCursorPosCallbackI callback) { GLFWCursorPosCallbackI previous = cursorPos; cursorPos = callback; return previous; }
    /**
     * Registers primary touch and mouse button delivery.
     */
    public static GLFWMouseButtonCallbackI mouseButton(GLFWMouseButtonCallbackI callback) { GLFWMouseButtonCallbackI previous = mouseButton; mouseButton = callback; return previous; }
    /**
     * Registers trackpad and mouse-wheel scroll delivery.
     */
    public static GLFWScrollCallbackI scroll(GLFWScrollCallbackI callback) { GLFWScrollCallbackI previous = scroll; scroll = callback; return previous; }
    /**
     * Retains a desktop drop callback without synthesizing Android file drops.
     */
    public static GLFWDropCallbackI drop(GLFWDropCallbackI callback) { GLFWDropCallbackI previous = drop; drop = callback; return previous; }
}
