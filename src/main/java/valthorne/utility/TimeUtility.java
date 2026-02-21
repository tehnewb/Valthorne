package valthorne.utility;

/**
 * Time-related utilities focused on real engine needs (game loop, cooldowns, timers),
 * without duplicating obvious {@link System} wrappers.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Conversion
 * long ms = TimeUtils.toMillis(0.5f);      // 500
 * float s  = TimeUtils.toSeconds(250);     // 0.25
 *
 * // Clamp delta (avoid huge jumps after tab-out)
 * dt = TimeUtils.clampDelta(dt, 0.1f);
 *
 * // Smooth/lerp delta
 * smoothedDt = TimeUtils.smoothDelta(smoothedDt, dt, 0.08f);
 *
 * // Cooldown
 * if (TimeUtils.cooldownReady(nowSec, lastFireSec, 0.2f)) lastFireSec = nowSec;
 *
 * // Fixed-step accumulator
 * accumulator = TimeUtils.stepFixed(accumulator, dt, 1f / 60f, 5, () -> physicsStep());
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 20th, 2026
 */
public final class TimeUtility {

    private static final float MILLIS_PER_SECOND = 1000f;     // 1 second = 1000 ms
    private static final float EPSILON = 1e-6f;               // Small float tolerance for time comparisons

    /**
     * Converts float seconds to milliseconds.
     * <p>
     * Examples:
     * <ul>
     *     <li>1.0f  -> 1000</li>
     *     <li>0.5f  -> 500</li>
     *     <li>2.25f -> 2250</li>
     * </ul>
     *
     * @param seconds time in seconds
     * @return time in milliseconds
     */
    public static long toMillis(float seconds) {
        return (long) (seconds * MILLIS_PER_SECOND);
    }

    /**
     * Converts milliseconds to float seconds.
     * <p>
     * Examples:
     * <ul>
     *     <li>1000 -> 1.0f</li>
     *     <li>500  -> 0.5f</li>
     *     <li>250  -> 0.25f</li>
     * </ul>
     *
     * @param millis time in milliseconds
     * @return time in seconds
     */
    public static float toSeconds(long millis) {
        return millis / MILLIS_PER_SECOND;
    }

    /**
     * Clamps delta-time to avoid huge simulation jumps (e.g., when the window was paused or unfocused).
     *
     * @param deltaSeconds    measured dt
     * @param maxDeltaSeconds maximum allowed dt (common values: 0.05f to 0.25f)
     * @return clamped dt
     */
    public static float clampDelta(float deltaSeconds, float maxDeltaSeconds) {
        if (deltaSeconds < 0f) return 0f;
        return Math.min(deltaSeconds, maxDeltaSeconds);
    }

    /**
     * Applies exponential smoothing to delta-time.
     * Useful to reduce jitter for camera movement, UI animations, or variable frame rates.
     *
     * @param previousSmoothed previous smoothed dt
     * @param currentDelta     current measured dt
     * @param smoothing        0..1 (lower = smoother, higher = more responsive). Typical: 0.05f - 0.2f
     * @return new smoothed dt
     */
    public static float smoothDelta(float previousSmoothed, float currentDelta, float smoothing) {
        if (smoothing <= 0f) return previousSmoothed;
        if (smoothing >= 1f) return currentDelta;
        return previousSmoothed + (currentDelta - previousSmoothed) * smoothing;
    }

    /**
     * Calculates FPS (frames per second) from delta seconds.
     *
     * @param deltaSeconds dt in seconds
     * @return fps, or 0 if dt <= 0
     */
    public static int fpsFromDelta(float deltaSeconds) {
        if (deltaSeconds <= 0f) return 0;
        return (int) (1f / deltaSeconds);
    }

    /**
     * Accumulates dt and returns true if enough time has passed to "tick" at a fixed interval.
     * This is handy for cheap periodic logic (blink, UI pulses, autosave timer).
     *
     * <p>Usage pattern:</p>
     * <pre>{@code
     * acc += dt;
     * if (TimeUtils.tick(acc, 0.25f)) {
     *     acc -= 0.25f;
     *     doThing();
     * }
     * }</pre>
     *
     * @param accumulatorSeconds current accumulator (seconds)
     * @param intervalSeconds    tick interval (seconds)
     * @return true if accumulator >= interval
     */
    public static boolean tick(float accumulatorSeconds, float intervalSeconds) {
        return accumulatorSeconds + EPSILON >= intervalSeconds;
    }

    /**
     * Advances a fixed-step simulation using an accumulator.
     *
     * <p>Why this exists:</p>
     * Variable dt makes physics unstable and non-deterministic.
     * This helper consumes dt in fixed increments and runs {@code stepAction} for each step,
     * with an upper bound to prevent spiral-of-death.
     *
     * <p>Pattern:</p>
     * <pre>{@code
     * accumulator = TimeUtils.stepFixed(accumulator, dt, 1f/60f, 5, this::physicsStep);
     * }</pre>
     *
     * @param accumulatorSeconds current accumulator (seconds)
     * @param deltaSeconds       frame dt in seconds
     * @param stepSeconds        fixed step size (e.g., 1/60f)
     * @param maxStepsPerFrame   safety cap (e.g., 5..10)
     * @param stepAction         executed once per fixed step
     * @return updated accumulator
     */
    public static float stepFixed(float accumulatorSeconds, float deltaSeconds, float stepSeconds, int maxStepsPerFrame, Runnable stepAction) {
        if (stepSeconds <= 0f) return accumulatorSeconds;
        if (maxStepsPerFrame < 1) maxStepsPerFrame = 1;

        accumulatorSeconds += Math.max(0f, deltaSeconds);

        int steps = 0;
        while (accumulatorSeconds + EPSILON >= stepSeconds && steps < maxStepsPerFrame) {
            stepAction.run();
            accumulatorSeconds -= stepSeconds;
            steps++;
        }

        // If we hit the cap, drop extra time to avoid spiraling forever.
        if (steps >= maxStepsPerFrame && accumulatorSeconds > stepSeconds) {
            accumulatorSeconds = 0f;
        }

        return accumulatorSeconds;
    }

    /**
     * Returns interpolation alpha for rendering between fixed steps.
     * Use this after {@link #stepFixed(float, float, float, int, Runnable)}.
     *
     * @param accumulatorSeconds remaining accumulator (seconds)
     * @param stepSeconds        fixed step size (seconds)
     * @return alpha in [0..1]
     */
    public static float fixedAlpha(float accumulatorSeconds, float stepSeconds) {
        if (stepSeconds <= 0f) return 0f;
        float a = accumulatorSeconds / stepSeconds;
        if (a < 0f) return 0f;
        return Math.min(a, 1f);
    }

    /**
     * Returns true if a cooldown has finished.
     *
     * @param nowSeconds      current time in seconds (your engine time, not necessarily wall clock)
     * @param lastSeconds     last time the action was performed (seconds)
     * @param cooldownSeconds cooldown length (seconds)
     * @return true if now - last >= cooldown
     */
    public static boolean cooldownReady(float nowSeconds, float lastSeconds, float cooldownSeconds) {
        return (nowSeconds - lastSeconds) + EPSILON >= cooldownSeconds;
    }

    /**
     * Returns remaining cooldown time (seconds).
     *
     * @param nowSeconds      current time in seconds
     * @param lastSeconds     time when action started (seconds)
     * @param cooldownSeconds cooldown length (seconds)
     * @return remaining seconds (0 if ready)
     */
    public static float cooldownRemaining(float nowSeconds, float lastSeconds, float cooldownSeconds) {
        float rem = cooldownSeconds - (nowSeconds - lastSeconds);
        return Math.max(rem, 0f);
    }

    /**
     * Returns progress in [0..1] for a cooldown or timed action.
     *
     * @param nowSeconds      current time in seconds
     * @param startSeconds    start time in seconds
     * @param durationSeconds duration in seconds
     * @return progress 0..1
     */
    public static float progress01(float nowSeconds, float startSeconds, float durationSeconds) {
        if (durationSeconds <= 0f) return 1f;
        float t = (nowSeconds - startSeconds) / durationSeconds;
        if (t < 0f) return 0f;
        return Math.min(t, 1f);
    }

    /**
     * Returns true if a timed action has finished (now >= start + duration).
     *
     * @param nowSeconds      current time in seconds
     * @param startSeconds    start time in seconds
     * @param durationSeconds duration in seconds
     * @return true if finished
     */
    public static boolean finished(float nowSeconds, float startSeconds, float durationSeconds) {
        return (nowSeconds - startSeconds) + EPSILON >= durationSeconds;
    }

    /**
     * Formats seconds into "MM:SS" (minutes and seconds).
     * Intended for UI overlays (timers, speedrun clocks, etc).
     *
     * @param seconds time in seconds (>= 0)
     * @return formatted string
     */
    public static String formatMinutesSeconds(float seconds) {
        if (seconds < 0f) seconds = 0f;
        int total = (int) seconds;
        int m = total / 60;
        int s = total % 60;
        return two(m) + ":" + two(s);
    }

    /**
     * Formats seconds into "HH:MM:SS".
     *
     * @param seconds time in seconds (>= 0)
     * @return formatted string
     */
    public static String formatHoursMinutesSeconds(float seconds) {
        if (seconds < 0f) seconds = 0f;
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return two(h) + ":" + two(m) + ":" + two(s);
    }

    /**
     * Converts seconds to an integer frame count at a given FPS.
     * Useful for aligning animation durations to frames.
     *
     * @param seconds duration in seconds
     * @param fps     frames per second (e.g., 60)
     * @return frame count (>= 0)
     */
    public static int secondsToFrames(float seconds, int fps) {
        if (seconds <= 0f || fps <= 0) return 0;
        return (int) (seconds * fps);
    }

    /**
     * Converts frame count to seconds at a given FPS.
     *
     * @param frames frame count (>= 0)
     * @param fps    frames per second (e.g., 60)
     * @return seconds
     */
    public static float framesToSeconds(int frames, int fps) {
        if (frames <= 0 || fps <= 0) return 0f;
        return frames / (float) fps;
    }

    /**
     * Pads an int to at least 2 digits (00-99+).
     *
     * @param v value
     * @return two-digit string
     */
    private static String two(int v) {
        if (v < 10) return "0" + v;
        return String.valueOf(v);
    }
}