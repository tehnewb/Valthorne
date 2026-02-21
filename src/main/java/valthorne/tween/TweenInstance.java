package valthorne.tween;

import valthorne.graphics.Color;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.Dimensional;

import java.util.Objects;

/**
 * A running tween instance.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Example 1: tween a float (e.g., opacity) over 0.25s using QUAD_OUT
 * TweenInstance alphaTween = TweenInstance.of(
 *         0f, 1f, 0.25f,
 *         Tweens.QUAD_OUT,
 *         a -> spriteColor.a(a)
 * );
 *
 * // Example 2: tween a Vector2f position over 0.15s with a small overshoot
 * TweenInstance posTween = TweenInstance.vec2(
 *         playerPos, new Vector2f(400, 250),
 *         0.15f,
 *         Tweens.backOut(1.6f),
 *         (x, y) -> {
 *             playerPos.setX(x);
 *             playerPos.setY(y);
 *         }
 * ).delay(0.05f).repeat(0).yoyo(false);
 *
 * // Example 3: tween a Dimensional UI element to a new rectangle and yoyo forever
 * TweenInstance uiTween = TweenInstance.dimensional(
 *         panel,
 *         32, 32, 320, 180,
 *         0.50f,
 *         Tweens.SINE_IN_OUT,
 *         TweenDimensionalTarget.XYWH
 * ).infinite().yoyo(true);
 *
 * // Game loop:
 * alphaTween.update(deltaSeconds);
 * posTween.update(deltaSeconds);
 * uiTween.update(deltaSeconds);
 * }</pre>
 *
 * <p>
 * {@code TweenInstance} is the runtime object that:
 * </p>
 * <ul>
 *     <li>Accumulates time (with optional delay).</li>
 *     <li>Normalizes time into {@code [0..1]} across the duration.</li>
 *     <li>Applies an easing curve ({@link Tween}).</li>
 *     <li>Writes the eased value through an {@link Applier} (e.g., setters for float/vec2/color/dimensional).</li>
 *     <li>Optionally repeats and/or yoyo-reverses between loops.</li>
 * </ul>
 *
 * <p>
 * Supported value families are created through static factory methods:
 * </p>
 * <ul>
 *     <li>{@link #of(float, float, float, Tween, TweenSetter)} for a single float</li>
 *     <li>{@link #vec2(float, float, float, float, float, Tween, Vec2Setter)} and {@link #vec2(Vector2f, Vector2f, float, Tween, Vec2Setter)} for 2D values</li>
 *     <li>{@link #color(float, float, float, float, float, float, float, float, float, Tween, ColorSetter)} and {@link #color(Color, Color, float, Tween, ColorSetter)} for colors</li>
 *     <li>{@link #dimensional(Dimensional, float, float, float, float, float, Tween, TweenDimensionalTarget)} for {@link Dimensional} targets</li>
 * </ul>
 *
 * <p>
 * Time behavior notes:
 * </p>
 * <ul>
 *     <li>{@link #update(float)} clamps negative {@code dt} to {@code 0}.</li>
 *     <li>Delay is consumed before the tween begins; leftover time is applied immediately after delay ends.</li>
 *     <li>Repeat counting uses {@code repeats}: {@code 0 = play once}, {@code 1 = play twice}, {@code -1 = infinite}.</li>
 *     <li>Yoyo reverses every other loop (loop 1, 3, 5...).</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public final class TweenInstance {

    /**
     * Total duration of a single loop in seconds.
     *
     * <p>
     * This value is clamped to a tiny positive minimum to avoid division by zero in normalization.
     * It represents the length of one forward playthrough of the tween before repeating/yoyo logic.
     * </p>
     */
    private final float duration; // Seconds.

    /**
     * Easing function used to curve the normalized time.
     *
     * <p>
     * This is applied after converting {@code time/duration} into {@code [0..1]}.
     * The easing may overshoot depending on the chosen tween (Back/Elastic/Bounce).
     * </p>
     */
    private final Tween tween;     // Easing.

    /**
     * Value writer invoked each update with the eased interpolation value.
     *
     * <p>
     * The applier receives an "eased alpha" (typically {@code [0..1]}), and is responsible for
     * mapping that alpha to concrete values (lerp, channels, dimensional fields).
     * </p>
     */
    private final Applier applier; // Writes values.

    /**
     * Remaining delay time in seconds before the tween starts.
     *
     * <p>
     * If delay is active, {@link #update(float)} subtracts from this first and will not advance
     * the tween's {@link #time} until delay reaches zero. Any leftover time from the frame that
     * completes the delay will be applied immediately.
     * </p>
     */
    private float delay;           // Seconds.

    /**
     * Repeat count configuration.
     *
     * <p>
     * Semantics:
     * </p>
     * <ul>
     *     <li>{@code 0}: play once (no extra loops)</li>
     *     <li>{@code 1}: play twice (one repeat)</li>
     *     <li>{@code -1}: repeat forever</li>
     * </ul>
     *
     * <p>
     * Completion occurs when {@link #playedLoops} becomes {@code > repeats} (for non-infinite mode),
     * after applying the final value at {@code t=1}.
     * </p>
     */
    private int repeats;           // 0 = once, -1 = infinite.

    /**
     * Enables yoyo mode (reverse every other loop).
     *
     * <p>
     * When enabled, loops alternate direction:
     * </p>
     * <ul>
     *     <li>Loop 0: forward</li>
     *     <li>Loop 1: reverse</li>
     *     <li>Loop 2: forward</li>
     *     <li>Loop 3: reverse</li>
     * </ul>
     *
     * <p>
     * Internally this is determined by {@code (playedLoops & 1) == 1}.
     * </p>
     */
    private boolean yoyo;          // Reverse every other loop.

    /**
     * Finished flag.
     *
     * <p>
     * When true, {@link #update(float)} becomes a no-op. This may be set by natural completion
     * (repeat limit reached) or by {@link #cancel()}.
     * </p>
     */
    private boolean finished;      // Done.

    /**
     * Time accumulator within the current loop in seconds.
     *
     * <p>
     * This counts up from {@code 0} toward {@link #duration}. When it reaches or exceeds
     * {@link #duration}, the loop is considered complete and repeat/yoyo rules are applied.
     * </p>
     */
    private float time;            // Seconds into current loop.

    /**
     * Number of completed loops so far.
     *
     * <p>
     * This increments each time the tween hits the end of a loop (after applying {@code t=1}).
     * It is used for:
     * </p>
     * <ul>
     *     <li>Repeat completion checks against {@link #repeats}</li>
     *     <li>Determining yoyo direction</li>
     * </ul>
     */
    private int playedLoops;       // Completed loops.

    /**
     * Optional completion callback.
     *
     * <p>
     * When set, this is invoked once when the tween finishes naturally (repeat limit reached).
     * This is not invoked by {@link #cancel()}.
     * </p>
     */
    private Runnable onComplete;   // Callback.

    /**
     * Creates a tween runtime instance with the provided configuration.
     *
     * <p>
     * This constructor is private. Use the factory methods ({@link #of}, {@link #vec2}, {@link #color},
     * {@link #dimensional}) to create instances with correctly wired {@link Applier} behavior.
     * </p>
     *
     * <p>
     * Duration is clamped to a tiny positive minimum to keep normalization stable.
     * </p>
     *
     * @param duration duration in seconds (clamped to a minimum positive value)
     * @param tween    easing curve (non-null)
     * @param applier  value writer (non-null)
     * @throws NullPointerException if {@code tween} or {@code applier} is null
     */
    private TweenInstance(float duration, Tween tween, Applier applier) {
        this.duration = Math.max(0.000001f, duration);
        this.tween = Objects.requireNonNull(tween, "Tween cannot be null");
        this.applier = Objects.requireNonNull(applier, "Applier cannot be null");
    }

    /**
     * Creates a float tween.
     *
     * <p>
     * The returned instance will:
     * </p>
     * <ul>
     *     <li>Map normalized time into {@code [0..1]}</li>
     *     <li>Apply {@code tween}</li>
     *     <li>Lerp from {@code from} to {@code to}</li>
     *     <li>Write the value into {@code setter}</li>
     * </ul>
     *
     * <p>
     * This does not mutate any external state except through the provided setter.
     * </p>
     *
     * @param from     start value
     * @param to       end value
     * @param duration seconds for one loop
     * @param tween    easing curve (non-null)
     * @param setter   value sink (non-null)
     * @return a new tween instance configured for float interpolation
     * @throws NullPointerException if {@code tween} or {@code setter} is null
     */
    public static TweenInstance of(float from, float to, float duration, Tween tween, TweenSetter setter) {
        Objects.requireNonNull(setter, "TweenSetter cannot be null");
        return new TweenInstance(duration, tween, easedT -> setter.set(MathUtils.lerp(from, to, easedT)));
    }

    /**
     * Creates a {@code Vector2f}-style tween using raw float endpoints.
     *
     * <p>
     * This is useful when you do not want to allocate new {@link Vector2f} instances during setup.
     * The interpolation is performed independently for X and Y using the same eased alpha.
     * </p>
     *
     * @param fromX    start x
     * @param fromY    start y
     * @param toX      end x
     * @param toY      end y
     * @param duration seconds for one loop
     * @param tween    easing curve (non-null)
     * @param setter   sink that receives the interpolated x/y each update (non-null)
     * @return a new tween instance configured for vec2 interpolation
     * @throws NullPointerException if {@code tween} or {@code setter} is null
     */
    public static TweenInstance vec2(float fromX, float fromY, float toX, float toY, float duration, Tween tween, Vec2Setter setter) {
        Objects.requireNonNull(setter, "TweenSetter cannot be null");
        return new TweenInstance(duration, tween, easedT -> setter.set(MathUtils.lerp(fromX, toX, easedT), MathUtils.lerp(fromY, toY, easedT)));
    }

    /**
     * Creates a {@code Vector2f}-style tween using your {@link Vector2f} endpoints.
     *
     * <p>
     * This reads {@code from} and {@code to} immediately and then delegates to
     * {@link #vec2(float, float, float, float, float, Tween, Vec2Setter)}.
     * </p>
     *
     * <p>
     * Note: this does not keep references to {@code from} and {@code to}. If you mutate those vectors
     * after creating the tween, it will not change the tween endpoints.
     * </p>
     *
     * @param from     start vector (non-null)
     * @param to       end vector (non-null)
     * @param duration seconds for one loop
     * @param tween    easing curve (non-null)
     * @param setter   sink that receives interpolated x/y (non-null)
     * @return a new tween instance configured for vec2 interpolation
     * @throws NullPointerException if {@code from}, {@code to}, {@code tween}, or {@code setter} is null
     */
    public static TweenInstance vec2(Vector2f from, Vector2f to, float duration, Tween tween, Vec2Setter setter) {
        Objects.requireNonNull(from, "from Vector2f cannot be null");
        Objects.requireNonNull(to, "to Vector2f cannot be null");
        return vec2(from.getX(), from.getY(), to.getX(), to.getY(), duration, tween, setter);
    }

    /**
     * Creates a color tween using raw channel endpoints.
     *
     * <p>
     * Each channel is interpolated independently using the same eased alpha.
     * This is ideal for fading alpha, tinting UI, or animating material colors.
     * </p>
     *
     * @param fromR    start red
     * @param fromG    start green
     * @param fromB    start blue
     * @param fromA    start alpha
     * @param toR      end red
     * @param toG      end green
     * @param toB      end blue
     * @param toA      end alpha
     * @param duration seconds for one loop
     * @param tween    easing curve (non-null)
     * @param setter   sink receiving interpolated channels each update (non-null)
     * @return a new tween instance configured for color interpolation
     * @throws NullPointerException if {@code tween} or {@code setter} is null
     */
    public static TweenInstance color(float fromR, float fromG, float fromB, float fromA, float toR, float toG, float toB, float toA, float duration, Tween tween, ColorSetter setter) {
        Objects.requireNonNull(setter, "TweenSetter cannot be null");
        return new TweenInstance(duration, tween, easedT -> setter.set(MathUtils.lerp(fromR, toR, easedT), MathUtils.lerp(fromG, toG, easedT), MathUtils.lerp(fromB, toB, easedT), MathUtils.lerp(fromA, toA, easedT)));
    }

    /**
     * Creates a color tween using your {@link Color} endpoints.
     *
     * <p>
     * Reads channel values immediately and delegates to the raw-channel overload.
     * This avoids retaining references to {@code from} or {@code to}.
     * </p>
     *
     * @param from     start color (non-null)
     * @param to       end color (non-null)
     * @param duration seconds for one loop
     * @param tween    easing curve (non-null)
     * @param setter   sink receiving interpolated channels (non-null)
     * @return a new tween instance configured for color interpolation
     * @throws NullPointerException if {@code from}, {@code to}, {@code tween}, or {@code setter} is null
     */
    public static TweenInstance color(Color from, Color to, float duration, Tween tween, ColorSetter setter) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        return color(from.r(), from.g(), from.b(), from.a(), to.r(), to.g(), to.b(), to.a(), duration, tween, setter);
    }

    /**
     * Creates a {@link Dimensional} tween.
     *
     * <p>
     * This method reads the starting values immediately from {@code target}:
     * {@code x,y,width,height}. It then interpolates them toward the provided end values.
     * </p>
     *
     * <p>
     * The {@code mode} controls which fields are written:
     * </p>
     * <ul>
     *     <li>{@code X} writes only x</li>
     *     <li>{@code Y} writes only y</li>
     *     <li>{@code WIDTH} writes only width</li>
     *     <li>{@code HEIGHT} writes only height</li>
     *     <li>{@code XY} writes x and y</li>
     *     <li>{@code WH} writes width and height</li>
     *     <li>{@code XYWH} writes all four</li>
     * </ul>
     *
     * <p>
     * This is designed for UI layout animations, panels sliding/resizing, and any object that implements
     * your {@link Dimensional} interface.
     * </p>
     *
     * @param target   dimensional target to read/write (non-null)
     * @param toX      target x
     * @param toY      target y
     * @param toW      target width
     * @param toH      target height
     * @param duration seconds for one loop
     * @param tween    easing curve (non-null)
     * @param mode     which fields to write (non-null)
     * @return a new tween instance configured for dimensional interpolation
     * @throws NullPointerException if {@code target}, {@code tween}, or {@code mode} is null
     */
    public static TweenInstance dimensional(Dimensional target, float toX, float toY, float toW, float toH, float duration, Tween tween, TweenDimensionalTarget mode) {
        Objects.requireNonNull(target, "Dimensional target cannot be null");
        Objects.requireNonNull(mode, "TweenDimensionalTarget cannot be null");

        final float fromX = target.getX();
        final float fromY = target.getY();
        final float fromW = target.getWidth();
        final float fromH = target.getHeight();

        return new TweenInstance(duration, tween, easedT -> {
            float x = MathUtils.lerp(fromX, toX, easedT);
            float y = MathUtils.lerp(fromY, toY, easedT);
            float w = MathUtils.lerp(fromW, toW, easedT);
            float h = MathUtils.lerp(fromH, toH, easedT);

            switch (mode) {
                case X -> target.setX(x);
                case Y -> target.setY(y);
                case WIDTH -> target.setWidth(w);
                case HEIGHT -> target.setHeight(h);
                case XY -> {
                    target.setX(x);
                    target.setY(y);
                }
                case WH -> {
                    target.setWidth(w);
                    target.setHeight(h);
                }
                case XYWH -> {
                    target.setX(x);
                    target.setY(y);
                    target.setWidth(w);
                    target.setHeight(h);
                }
            }
        });
    }

    /**
     * Sets a start delay.
     *
     * <p>
     * Delay is consumed before the tween begins advancing {@link #time}.
     * If the update step completes the delay and still has leftover time, the leftover is applied
     * immediately to the tween in the same call.
     * </p>
     *
     * @param seconds delay in seconds (negative values are clamped to {@code 0})
     * @return this instance for chaining
     */
    public TweenInstance delay(float seconds) {
        this.delay = Math.max(0f, seconds);
        return this;
    }

    /**
     * Sets the tween instance to loop infinitely by configuring the repeat count
     * to a value that represents infinite repetitions.
     *
     * @return the current TweenInstance object with infinite looping enabled
     */
    public TweenInstance infinite() {
        this.repeats = -1;
        return this;
    }

    /**
     * Sets repeat count.
     *
     * <p>
     * Semantics:
     * </p>
     * <ul>
     *     <li>{@code 0}: play once</li>
     *     <li>{@code 1}: play twice</li>
     *     <li>{@code N}: play {@code N+1} total loops</li>
     *     <li>{@code -1}: infinite looping</li>
     * </ul>
     *
     * <p>
     * Completion occurs after the tween applies its final value for the last loop.
     * </p>
     *
     * @param count repeat count configuration
     * @return this instance for chaining
     */
    public TweenInstance repeat(int count) {
        this.repeats = count;
        return this;
    }

    /**
     * Enables or disables yoyo mode.
     *
     * <p>
     * When yoyo is enabled, the direction flips every loop. This is implemented without changing
     * the applier endpoints: the normalized curve input/output is mirrored.
     * </p>
     *
     * @param enabled true to enable yoyo, false to disable it
     * @return this instance for chaining
     */
    public TweenInstance yoyo(boolean enabled) {
        this.yoyo = enabled;
        return this;
    }

    /**
     * Sets a completion callback.
     *
     * <p>
     * The callback runs once when the tween finishes naturally (repeat limit reached).
     * It does not run when {@link #cancel()} is called.
     * </p>
     *
     * @param r completion callback (may be null to clear)
     * @return this instance for chaining
     */
    public TweenInstance onComplete(Runnable r) {
        this.onComplete = r;
        return this;
    }

    /**
     * Cancels the tween immediately.
     *
     * <p>
     * This sets {@link #finished} to true, causing {@link #update(float)} to no-op from that point on.
     * No completion callback is fired, and the tween does not force-apply an end value.
     * </p>
     */
    public void cancel() {
        this.finished = true;
    }

    /**
     * Returns whether the tween is finished.
     *
     * <p>
     * Finished means either:
     * </p>
     * <ul>
     *     <li>It naturally completed its final loop and repeat limit (non-infinite mode), or</li>
     *     <li>It was cancelled via {@link #cancel()}.</li>
     * </ul>
     *
     * @return true if finished, otherwise false
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Restarts runtime state.
     *
     * <p>
     * This resets only the runtime counters:
     * </p>
     * <ul>
     *     <li>{@link #time} becomes {@code 0}</li>
     *     <li>{@link #playedLoops} becomes {@code 0}</li>
     *     <li>{@link #finished} becomes {@code false}</li>
     * </ul>
     *
     * <p>
     * Configuration such as {@link #delay}, {@link #repeats}, {@link #yoyo}, and {@link #onComplete}
     * is left unchanged.
     * </p>
     */
    public void restart() {
        this.time = 0f;
        this.playedLoops = 0;
        this.finished = false;
    }

    /**
     * Advances the tween by {@code dt} seconds and applies the updated value.
     *
     * <p>
     * Update flow:
     * </p>
     * <ol>
     *     <li>Clamp negative {@code dt} to {@code 0}.</li>
     *     <li>If a delay is active, consume delay first and return early if delay remains.</li>
     *     <li>Advance {@link #time} and apply value based on {@code time/duration}.</li>
     *     <li>If the loop completes this update, apply {@code t=1}, increment loops, then decide finish/repeat.</li>
     *     <li>If repeating, apply the leftover time fraction into the next loop (single-step; no multi-loop catch-up).</li>
     * </ol>
     *
     * <p>
     * Note: this implementation intentionally does not run a {@code while} loop to catch up multiple loops
     * in a single frame. If {@code dt} is very large (pause/hitch), it will complete at most one loop per update.
     * </p>
     *
     * @param dt delta time in seconds (negative values are treated as {@code 0})
     */
    public void update(float dt) {
        if (finished) return;

        float step = Math.max(0f, dt);

        if (delay > 0f) {
            delay -= step;

            if (delay > 0f) return;

            step = -delay;
            delay = 0f;
        }

        time += step;

        if (time < duration) {
            applyAt(time / duration);
            return;
        }

        applyAt(1f);

        time -= duration;
        playedLoops++;

        if (repeats != -1 && playedLoops > repeats) {
            finished = true;
            if (onComplete != null) onComplete.run();
            return;
        }

        float nextT = (duration <= 0f) ? 0f : (time / duration);
        applyAt(nextT);
    }

    /**
     * Applies the tween at a specific normalized time value.
     *
     * <p>
     * This method:
     * </p>
     * <ol>
     *     <li>Clamps {@code rawT} into {@code [0..1]} via {@link Tween#clamp01(float)}.</li>
     *     <li>Determines whether the current loop is reversed (yoyo + odd loop).</li>
     *     <li>Mirrors curve input for reversed loops.</li>
     *     <li>Applies {@link #tween} to curve the input.</li>
     *     <li>Mirrors eased output back for reversed loops so endpoints remain correct.</li>
     *     <li>Calls {@link #applier} with the final eased alpha.</li>
     * </ol>
     *
     * <p>
     * The mirroring of both input and output ensures consistent "from/to" mapping even if an easing
     * function overshoots.
     * </p>
     *
     * @param rawT normalized time (expected [0..1], but clamped)
     */
    private void applyAt(float rawT) {
        float t = Tween.clamp01(rawT);

        boolean reverse = yoyo && ((playedLoops & 1) == 1);
        float curveIn = reverse ? (1f - t) : t;

        float eased = tween.apply(curveIn);
        float easedForApplier = reverse ? (1f - eased) : eased;

        applier.apply(easedForApplier);
    }
}