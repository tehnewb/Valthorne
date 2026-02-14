package valthorne.tick;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A lightweight time-based tick utility.
 *
 * <p>
 * {@code Tick} executes a callback repeatedly at a fixed delay interval.
 * It is manually driven by calling {@link #update(float)} every frame.
 * </p>
 *
 * <h2>Core Behavior</h2>
 * <ul>
 *     <li>Accumulates time using {@code delta}.</li>
 *     <li>When accumulated time reaches {@link #delay}, the callback is invoked.</li>
 *     <li>Supports optional stop conditions.</li>
 *     <li>Tracks how many times it has fired via {@link #iterations}.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * Tick tick = new Tick()
 *     .delay(1.0f) // fire every 1 second
 *     .callback(t -> System.out.println("Tick: " + t.getIterations()))
 *     .stopCondition(t -> t.getIterations() >= 5);
 *
 * tick.start();
 *
 * // inside game update method
 * tick.update(delta);
 * }</pre>
 *
 * <p>
 * This will print once per second and automatically stop after 5 iterations.
 * </p>
 *
 * @author Albert Beaupre
 * @since February 14th, 2026
 */
public class Tick {

    private float delay;                       // Time interval in seconds between callback executions.
    private float elapsed;                     // Accumulated time since last callback execution.
    private long iterations;                   // Number of times the callback has been executed.
    private boolean stopped = true;            // Whether this tick is currently stopped.
    private Consumer<Tick> callback;           // Callback executed each time the delay threshold is reached.
    private Predicate<Tick> stopCondition;     // Optional condition evaluated before execution to auto-stop.

    /**
     * Updates the tick timer.
     *
     * <p>
     * This method must be called every frame with the elapsed time since
     * the previous frame.
     * </p>
     *
     * <p>Execution order:</p>
     * <ol>
     *     <li>If stopped → return immediately.</li>
     *     <li>If stop condition exists and evaluates true → stop.</li>
     *     <li>Accumulate time.</li>
     *     <li>If accumulated time >= delay → fire callback.</li>
     * </ol>
     *
     * @param delta time in seconds since last update
     */
    public void update(float delta) {
        if (this.stopped)
            return;

        if (this.stopCondition != null && this.stopCondition.test(this)) {
            this.stop();
            return;
        }

        this.elapsed += delta;

        if (this.elapsed >= this.delay) {
            this.iterations++;
            this.elapsed = 0;
            if (this.callback != null) {
                this.callback.accept(this);
            }
        }
    }

    /**
     * Restarts the tick from a clean state.
     *
     * <p>
     * This resets elapsed time, iteration count,
     * and immediately starts the tick.
     * </p>
     */
    public void restart() {
        this.elapsed = 0f;
        this.iterations = 0;
        this.stopped = false;
    }

    /**
     * Starts the tick without resetting state.
     *
     * <p>
     * If the tick was previously stopped,
     * it resumes from its current elapsed time.
     * </p>
     */
    public void start() {
        this.stopped = false;
    }

    /**
     * Sets a stop condition.
     *
     * <p>
     * The predicate is evaluated before each callback execution.
     * If it returns true, the tick automatically stops.
     * </p>
     *
     * @param stopCondition condition to evaluate
     * @return this, for chaining
     */
    public Tick stopCondition(Predicate<Tick> stopCondition) {
        this.stopCondition = stopCondition;
        return this;
    }

    /**
     * Sets the callback executed each time the delay threshold is reached.
     *
     * @param callback callback consumer
     * @return this, for chaining
     */
    public Tick callback(Consumer<Tick> callback) {
        this.callback = callback;
        return this;
    }

    /**
     * Sets the delay interval between executions.
     *
     * @param delay time in seconds between ticks
     * @return this, for chaining
     */
    public Tick delay(float delay) {
        this.delay = delay;
        return this;
    }

    /**
     * Stops the tick immediately.
     *
     * <p>
     * No further updates will trigger the callback
     * until {@link #start()} or {@link #restart()} is called.
     * </p>
     */
    public void stop() {
        this.stopped = true;
    }

    /**
     * Returns whether this tick is currently stopped.
     *
     * @return true if stopped
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * Returns how many times the callback has been executed.
     *
     * @return iteration count
     */
    public long getIterations() {
        return iterations;
    }

    /**
     * Returns the current delay interval.
     *
     * @return delay in seconds
     */
    public float getDelay() {
        return delay;
    }
}