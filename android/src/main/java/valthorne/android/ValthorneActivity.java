package valthorne.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.Display;
import android.view.Display.Mode;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;
import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.SwapInterval;
import valthorne.android.internal.AndroidRuntime;
import valthorne.graphics.GraphicsBackend;

/**
 * Native Android entry point for an ordinary Valthorne Application. Android owns
 * the activity and SurfaceView; one dedicated render thread enters JGL and owns
 * all application, scene, asset-upload and graphics callbacks. Implement only
 * {@link #createApplication()} and optionally {@link #configuration()}.
 *
 * <p>Pause and surface replacement retain application state. The default config
 * selects native OpenGL ES and enables native OpenAL audio. Native desktop window
 * and file-dialog endpoints are not loaded by this launcher. Activity
 * recreation should be avoided for ordinary display changes by declaring
 * orientation, screenSize, keyboardHidden and density in configChanges.</p>
 */
public abstract class ValthorneActivity extends Activity {
    private AndroidSurface view; // Android-owned presentation and input endpoint.
    private Thread renderThread; // Single owner of the shared Valthorne application.
    private boolean resumed; // Whether Android currently allows this activity to run.

    /**
     * Creates the application's existing Java implementation on its render thread.
     * @return application receiving the normal init/update/render/dispose lifecycle
     */
    protected abstract Application createApplication();

    /**
     * Supplies a native Android configuration before entering JGL.
     * @return configuration with a logical viewport height and explicit mobile API
     */
    protected JGLConfiguration configuration() {
        return JGLConfiguration.defaults()
                .size(1600, 900)
                .graphicsBackend(GraphicsBackend.OPENGLES)
                .audioEnabled(true);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ValthorneActivity previous = AndroidRuntime.activity();
        if (previous != null && previous != this && !AndroidRuntime.shouldClose())
            throw new IllegalStateException("Only one Valthorne activity can run per process");
        Thread previousThread = previous == null ? null : previous.renderThread;
        if (previousThread != null && previousThread.isAlive()) {
            Thread handoff = new Thread(() -> awaitPreviousHost(previousThread), "Valthorne-host-handoff");
            handoff.setDaemon(true);
            handoff.start();
        } else initializeHost();
    }

    /**
     * Serializes activity recreation with the previous application's shutdown
     * without blocking Android's UI thread or rebinding a still-running JGL loop.
     * Application disposal may take longer than a fixed timeout; only this daemon
     * handoff waits, and destroyed replacement activities never initialize a host.
     * @param previous previous application owner thread
     */
    private void awaitPreviousHost(Thread previous) {
        try {
            previous.join();
            runOnUiThread(() -> { if (!isDestroyed()) initializeHost(); });
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            runOnUiThread(() -> { throw new IllegalStateException("Interrupted while replacing Valthorne activity", failure); });
        } catch (RuntimeException failure) {
            runOnUiThread(() -> { throw failure; });
        }
    }

    /**
     * Installs the presentation and input host after the previous JGL owner exits.
     */
    private void initializeHost() {
        System.setProperty("joml.nounsafe", "true");
        AndroidRuntime.bind(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new AndroidSurface(this);
        FrameLayout content = new FrameLayout(this);
        content.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        content.setOnApplyWindowInsetsListener((container, insets) -> {
            int keyboardHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
            if (container.getPaddingBottom() != keyboardHeight)
                container.setPadding(0, 0, 0, keyboardHeight);
            return insets;
        });
        setContentView(content);
        fullscreen(true);
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> AndroidRuntime.requestClose(true));
    }

    /**
     * Starts JGL once the first real Android presentation surface is available.
     */
    void startApplication() {
        if (renderThread != null || !resumed || isDestroyed()) return;
        renderThread = new Thread(() -> {
            try {
                Application application = createApplication();
                JGLConfiguration config = configuration();
                if (config.getSwapInterval() == SwapInterval.OFF) runOnUiThread(this::preferFastestDisplay);
                JGL.init(application, config);
            } catch (Throwable failure) {
                Log.e("Valthorne", "Native Android application failed", failure);
                runOnUiThread(() -> { throw new IllegalStateException("Native Valthorne application failed", failure); });
            }
        }, "Valthorne-render");
        renderThread.start();
    }

    /**
     * Requests the fastest supported display mode at the current resolution for
     * uncapped rendering. Android retains control of presentation and may apply
     * device, battery or thermal policy; this does not add an engine frame limit.
     */
    private void preferFastestDisplay() {
        Display display = getDisplay();
        if (display == null) return;
        Mode current = display.getMode();
        Mode fastest = current;
        for (Mode mode : display.getSupportedModes())
            if (mode.getPhysicalWidth() == current.getPhysicalWidth() && mode.getPhysicalHeight() == current.getPhysicalHeight() && mode.getRefreshRate() > fastest.getRefreshRate())
                fastest = mode;
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.preferredDisplayModeId = fastest.getModeId();
        getWindow().setAttributes(attributes);
    }

    /**
     * Returns the native view for input-method and surface integration.
     * @return activity-owned SurfaceView
     */
    public final AndroidSurface surfaceView() { return view; }

    /**
     * Shows or hides Android system bars without replacing the rendering surface.
     * @param enabled whether game content fills the activity window
     */
    public final void fullscreen(boolean enabled) {
        if (enabled) getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
        else getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (AndroidRuntime.activity() == this) AndroidRuntime.pause(true);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (AndroidRuntime.activity() != this) return;
        AndroidRuntime.pause(false);
        if (view != null && view.getHolder().getSurface().isValid()) startApplication();
    }

    @Override
    protected void onDestroy() {
        if (AndroidRuntime.activity() == this) AndroidRuntime.requestClose(true);
        super.onDestroy();
    }
}
