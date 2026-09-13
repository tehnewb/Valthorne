package valthorne.portable;

import valthorne.Application;
import java.util.Objects;

/** Platform-neutral application lifecycle and bounded fixed-step updates. No native dependencies. */
public final class FrameLoop implements AutoCloseable {
    private final Application app;
    private final float step;
    private final int maxSteps;
    private double accumulator;
    private boolean started, closed, paused;

    public FrameLoop(Application app, float step, int maxSteps) {
        this.app = Objects.requireNonNull(app);
        if (!Float.isFinite(step) || step <= 0 || maxSteps < 1)
            throw new IllegalArgumentException("Positive finite step and step limit required");
        this.step = step;
        this.maxSteps = maxSteps;
    }

    public void start() {
        if (started || closed) throw new IllegalStateException("Loop already started or closed");
        try {app.init();}
        catch (RuntimeException | Error failure) {
            closed = true;
            try {app.dispose();} catch (RuntimeException | Error cleanup) {failure.addSuppressed(cleanup);}
            throw failure;
        }
        started = true;
    }

    /** Seconds since the previous frame; excess time is dropped instead of causing an update spiral. */
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

    /** Clears partial accumulated time when pausing or resuming. Rendering continues while paused. */
    public void setPaused(boolean paused) {
        if (this.paused != paused) {this.paused = paused; accumulator = 0;}
    }
    public boolean isPaused() {return paused;}

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (started) app.dispose();
    }
}
