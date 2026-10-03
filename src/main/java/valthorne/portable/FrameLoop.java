package valthorne.portable;

import valthorne.Application;

import java.util.Objects;

/**
 * Platform-neutral application lifecycle and bounded fixed-step updates. No native dependencies.
 */
public final class FrameLoop implements AutoCloseable {
    private final Application app; // Owned application lifecycle, disposed after successful initialization.
    private final float step; // Fixed update interval in seconds.
    private final int maxSteps; // Maximum updates per rendered frame.
    private double accumulator; // Unconsumed simulation seconds.
    private boolean started; // Whether initialization completed.
    private boolean closed; // Whether disposal or failed initialization terminated the loop.
    private boolean paused; // Whether simulation updates are suspended.

    /**
     * Takes responsibility for initializing and disposing an application.
     *
     * @param app application lifecycle to own
     * @param step positive finite update interval in seconds
     * @param maxSteps positive update budget per frame
     * @throws IllegalArgumentException if timing configuration is invalid
     * @throws NullPointerException if app is null
     */
    public FrameLoop(Application app, float step, int maxSteps) {
        this.app = Objects.requireNonNull(app);
        if (!Float.isFinite(step) || step <= 0 || maxSteps < 1)
            throw new IllegalArgumentException("Positive finite step and step limit required");
        this.step = step;
        this.maxSteps = maxSteps;
    }

    /**
     * Initializes the application once. Failed initialization disposes partial
     * resources, preserving cleanup failures as suppressed exceptions.
     *
     * @throws IllegalStateException if already started or closed
     */
    public void start() {
        if (started || closed) throw new IllegalStateException("Loop already started or closed");
        try {
            app.init();
        } catch (RuntimeException | Error failure) {
            closed = true;
            try {
                app.dispose();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        started = true;
    }

    /**
     * Updates with bounded accumulated time and renders once, including while paused.
     *
     * @param elapsed finite nonnegative seconds since the previous frame
     * @throws IllegalStateException if the loop is not running
     * @throws IllegalArgumentException if elapsed is negative or nonfinite
     */
    public void frame(double elapsed) {
        if (!started || closed) throw new IllegalStateException("Loop is not running");
        if (!Double.isFinite(elapsed) || elapsed < 0) throw new IllegalArgumentException("Invalid frame delta");
        if (!paused) {
            accumulator += Math.min(elapsed, (double) step * maxSteps);
            int steps = 0;
            while (accumulator >= step && steps++ < maxSteps) {
                app.update(step);
                accumulator -= step;
            }
        }
        app.render();
    }

    /**
     * Clears partial accumulated time when pausing or resuming. Rendering continues while paused.
     *
     * @param paused whether simulation updates should be suspended
     */
    public void setPaused(boolean paused) {
        if (this.paused != paused) {
            this.paused = paused;
            accumulator = 0;
        }
    }

    /**
     * Reports whether simulation updates are suspended.
     *
     * @return true while paused; rendering still runs
     */
    public boolean isPaused() {
        return paused;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (started) app.dispose();
    }
}
