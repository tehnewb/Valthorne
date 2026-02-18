package valthorne.graphics.animation;

import valthorne.collections.bits.ByteBits;
import valthorne.graphics.Drawable;
import valthorne.math.MathUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A lightweight, time-driven frame animation player.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Build frames (each frame has a Drawable + a duration in seconds).
 * Animation walk = new Animation(
 *         PlaybackMode.FORWARD,
 *         new AnimationFrame(walk0, 0.08f),
 *         new AnimationFrame(walk1, 0.08f),
 *         new AnimationFrame(walk2, 0.08f),
 *         new AnimationFrame(walk3, 0.08f)
 * );
 *
 * // Configure.
 * walk.setLooping(true);     // keep looping forever
 * walk.setSpeed(1.0f);       // 1 = normal speed
 *
 * // Optional: events.
 * walk.setListener(new AnimationListener() {
 *     @Override public void onFrameChanged(Animation a, int from, int to) {
 *         // Example: play a footstep sound on certain frames.
 *         // if (to == 1 || to == 3) footstep.play();
 *     }
 *     @Override public void onFinished(Animation a) {
 *         // Example: switch state, fire event, etc.
 *     }
 *     @Override public void onLoop(Animation a, int loopsCompleted) {
 *         // Example: count loops for debugging.
 *     }
 * });
 *
 * // Game loop usage.
 * walk.update(deltaSeconds);
 * walk.draw(playerX, playerY, 64, 64);
 *
 * // Seek (optional).
 * walk.setTime(0.15f);       // jump to time in seconds along the timeline
 * walk.setFrame(2);          // force a specific frame
 * walk.pause();              // pause update
 * walk.play();               // resume update
 * }</pre>
 *
 * <h2>What this class does</h2>
 * <ul>
 *     <li>Stores an ordered array of {@link AnimationFrame} objects (a {@link Drawable} + duration).</li>
 *     <li>Advances time each {@link #update(float)} call and changes frames when their durations elapse.</li>
 *     <li>Supports multiple playback behaviors via {@link PlaybackMode}.</li>
 *     <li>Supports looping forever or for a fixed number of loops.</li>
 *     <li>Exposes optional callbacks via {@link AnimationListener}.</li>
 * </ul>
 *
 * <h2>Playback modes</h2>
 * <ul>
 *     <li>{@link PlaybackMode#FORWARD}: 0 → 1 → 2 → ... → last</li>
 *     <li>{@link PlaybackMode#REVERSE}: last → ... → 2 → 1 → 0</li>
 *     <li>{@link PlaybackMode#BIDIRECTIONAL}: ping-pongs: 0 → ... → last → ... → 0</li>
 * </ul>
 *
 * <h2>State flags</h2>
 * <p>This class uses {@link ByteBits} to pack common booleans into one byte:
 * paused, looping, returning (ping-pong), and finished.</p>
 *
 * <p><b>Important:</b> If {@code frames} is null/empty, {@link #update(float)} and {@link #draw(float, float, float, float)}
 * safely no-op and query methods return safe defaults.</p>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public class Animation implements Drawable {

    private static final byte PAUSED = 0;                 // Bit index: animation updates are paused when set.
    private static final byte LOOPING = 1;                // Bit index: animation loops at endpoints when set.
    private static final byte RETURNING = 2;              // Bit index: BIDIRECTIONAL-only: true when moving backward.
    private static final byte FINISHED = 3;               // Bit index: animation has finished (distinct from paused).

    private final ByteBits bits = new ByteBits();         // Packed flags: paused/looping/returning/finished.
    private final AnimationFrame[] frames;                // Ordered list of frames played by this animation.

    private PlaybackMode playbackMode;                    // Current playback mode (forward/reverse/bidirectional).
    private short currentIndex;                           // Current frame index into {@link #frames}.
    private float elapsedTime;                            // Elapsed time spent on the current frame (seconds).
    private float speed = 1f;                             // Time scale multiplier (1 = normal).
    private float totalDuration;                          // Sum of all frame durations (seconds, clamped >= 0).

    private int loopCountLimit = -1;                      // Loop limit: -1 infinite, 0 none, N loop N times.
    private int loopsCompleted;                           // Number of completed loops (meaning depends on mode).

    private AnimationListener animationListener;          // Optional callbacks for frame changes/finish/loop.

    /**
     * Creates an {@code Animation} using a playback mode and a frame list.
     *
     * <p>Construction behavior:</p>
     * <ul>
     *     <li>Stores the provided frame array reference (no copy).</li>
     *     <li>Starts at index 0 by default (unless later {@link #reset()} changes it for REVERSE).</li>
     *     <li>Precomputes {@link #totalDuration} as the sum of all non-null frame durations.</li>
     * </ul>
     *
     * <p>Note: If frames contain null elements, they are allowed. Null frames act like 0-duration frames for timing,
     * and {@link #draw(float, float, float, float)} will no-op when the current frame is null.</p>
     *
     * @param playbackMode playback mode to use
     * @param frames       ordered frames to play
     */
    public Animation(PlaybackMode playbackMode, AnimationFrame... frames) {
        this.playbackMode = playbackMode;
        this.frames = frames;

        if (frames != null) {
            for (AnimationFrame f : frames) {
                if (f != null) totalDuration += Math.max(0f, f.duration());
            }
        }
    }

    /**
     * Draws the current frame's {@link Drawable} into a destination rectangle.
     *
     * <p>This does not advance time. Call {@link #update(float)} to progress the animation.</p>
     * <p>If the current frame (or its drawable) is null, this method safely no-ops.</p>
     *
     * @param x      destination x (world space)
     * @param y      destination y (world space)
     * @param width  destination width
     * @param height destination height
     */
    public void draw(float x, float y, float width, float height) {
        AnimationFrame currentFrame = getCurrentFrame();
        if (currentFrame == null) return;
        if (currentFrame.drawable() == null) return;

        currentFrame.drawable().draw(x, y, width, height);
    }

    /**
     * Returns the width of the current frame's {@link Drawable}.
     *
     * <p>This is a convenience method for layout code. It does not reflect the width passed to {@link #draw(float, float, float, float)}.</p>
     * <p>If there is no current frame (null/empty frames) or drawable is null, returns 0.</p>
     *
     * @return drawable width or 0
     */
    @Override
    public float getWidth() {
        AnimationFrame f = getCurrentFrame();
        if (f == null || f.drawable() == null) return 0f;
        return f.drawable().getWidth();
    }

    /**
     * Returns the height of the current frame's {@link Drawable}.
     *
     * <p>This is a convenience method for layout code. It does not reflect the height passed to {@link #draw(float, float, float, float)}.</p>
     * <p>If there is no current frame (null/empty frames) or drawable is null, returns 0.</p>
     *
     * @return drawable height or 0
     */
    @Override
    public float getHeight() {
        AnimationFrame f = getCurrentFrame();
        if (f == null || f.drawable() == null) return 0f;
        return f.drawable().getHeight();
    }

    /**
     * Advances the animation timeline and updates the current frame index when needed.
     *
     * <p>Key properties:</p>
     * <ul>
     *     <li>Time is scaled by {@link #speed}.</li>
     *     <li>Large deltas can advance across multiple frames in one call.</li>
     *     <li>0-duration frames are skipped without getting stuck in an infinite loop.</li>
     *     <li>If {@link #isPaused()} or {@link #isFinished()} is true, this method does nothing.</li>
     * </ul>
     *
     * <p>Finish behavior:</p>
     * <ul>
     *     <li>If not looping: reaching the logical endpoint sets FINISHED and PAUSED.</li>
     *     <li>If loop-limited: reaching the limit sets FINISHED and PAUSED.</li>
     * </ul>
     *
     * @param delta elapsed seconds since last update
     */
    public void update(float delta) {
        if (frames == null || frames.length == 0) return;
        if (bits.get(PAUSED) || bits.get(FINISHED)) return;

        float scaled = delta * speed;
        if (scaled <= 0f) return;

        elapsedTime += scaled;

        while (true) {
            AnimationFrame frame = frames[currentIndex];
            float dur = (frame == null) ? 0f : Math.max(0f, frame.duration());

            if (dur > 0f && elapsedTime < dur) return;

            if (dur > 0f) elapsedTime -= dur;
            else elapsedTime = 0f;

            int from = currentIndex;

            switch (playbackMode) {

                case FORWARD -> {
                    currentIndex++;

                    if (currentIndex >= frames.length) {
                        if (shouldLoop()) {
                            currentIndex = 0;
                            onLoopCompleted();
                        } else {
                            currentIndex = (short) (frames.length - 1);
                            finishNow();
                            return;
                        }
                    }
                }

                case REVERSE -> {
                    currentIndex--;

                    if (currentIndex < 0) {
                        if (shouldLoop()) {
                            currentIndex = (short) (frames.length - 1);
                            onLoopCompleted();
                        } else {
                            currentIndex = 0;
                            finishNow();
                            return;
                        }
                    }
                }

                case BIDIRECTIONAL -> {
                    if (!bits.get(RETURNING)) {
                        currentIndex++;
                        if (currentIndex >= frames.length) {
                            currentIndex = (short) (frames.length - 2);
                            bits.set(RETURNING);

                            if (!shouldLoop()) {
                                finishNow();
                                return;
                            }
                        }
                    } else {
                        currentIndex--;
                        if (currentIndex < 0) {
                            currentIndex = 1;
                            bits.clear(RETURNING);

                            onLoopCompleted();
                            if (!shouldLoop()) {
                                finishNow();
                                return;
                            }
                        }
                    }
                }
            }

            if (animationListener != null && from != currentIndex) {
                animationListener.onFrameChanged(this, from, currentIndex);
            }

            AnimationFrame next = frames[currentIndex];
            float nd = (next == null) ? 0f : Math.max(0f, next.duration());
            if (nd > 0f) return;
        }
    }

    /**
     * Returns whether the animation is finished.
     *
     * <p>Finished is not the same as paused:</p>
     * <ul>
     *     <li>Paused can be set manually via {@link #pause()} or {@link #setPaused(boolean)}.</li>
     *     <li>Finished is set when reaching an endpoint in non-looping mode, or reaching a loop limit.</li>
     * </ul>
     *
     * @return true if finished, otherwise false
     */
    public boolean isFinished() {
        if (frames == null || frames.length == 0) return true;
        return bits.get(FINISHED);
    }

    /**
     * Restarts playback from the mode's logical start and unpauses.
     *
     * <p>This is equivalent to:</p>
     * <pre>{@code
     * reset();
     * play();
     * }</pre>
     */
    public void restart() {
        reset();
        play();
    }

    /**
     * Resets time and frame index to the mode's logical starting point.
     *
     * <p>Behavior by mode:</p>
     * <ul>
     *     <li>FORWARD: frame 0.</li>
     *     <li>REVERSE: last frame.</li>
     *     <li>BIDIRECTIONAL: frame 0 and "forward" direction.</li>
     * </ul>
     *
     * <p>This clears FINISHED and resets loop counters.</p>
     * <p>This does not automatically unpause (use {@link #play()} if desired).</p>
     */
    public void reset() {
        this.elapsedTime = 0f;
        this.loopsCompleted = 0;
        this.bits.clear(FINISHED);
        this.bits.clear(RETURNING);

        if (frames == null || frames.length == 0) {
            this.currentIndex = 0;
            return;
        }

        switch (playbackMode) {
            case FORWARD, BIDIRECTIONAL -> this.currentIndex = 0;
            case REVERSE -> this.currentIndex = (short) (frames.length - 1);
        }
    }

    /**
     * Unpauses the animation and clears FINISHED state.
     *
     * <p>If you want to restart from the beginning, call {@link #restart()} instead.</p>
     */
    public void play() {
        this.bits.clear(PAUSED);
        this.bits.clear(FINISHED);
    }

    /**
     * Pauses animation updates.
     *
     * <p>Calling {@link #draw(float, float, float, float)} still draws the current frame.</p>
     */
    public void pause() {
        this.bits.set(PAUSED);
    }

    /**
     * Stops playback by pausing and resetting to the mode's start.
     *
     * <p>This is useful for one-shot animations you want to reuse later.</p>
     */
    public void stop() {
        pause();
        reset();
    }

    /**
     * Forces the animation to a specific frame index.
     *
     * <p>This clears FINISHED and resets {@link #elapsedTime} so the frame starts "fresh".</p>
     * <p>The requested index is clamped into [0, frameCount-1].</p>
     *
     * @param index desired frame index
     */
    public void setFrame(int index) {
        if (frames == null || frames.length == 0) return;

        this.elapsedTime = 0f;
        this.bits.clear(FINISHED);
        this.currentIndex = (short) MathUtils.clamp(index, 0, frames.length - 1);
    }

    /**
     * Chooses a random start frame and jumps to it.
     *
     * <p>This clears FINISHED and resets elapsed time on the selected frame.</p>
     */
    public void setRandomStart() {
        if (frames == null || frames.length == 0) return;

        int idx = MathUtils.randomInt(0, frames.length - 1);
        setFrame(idx);
    }

    /**
     * Sets the playback speed multiplier.
     *
     * <p>Speed scales time inside {@link #update(float)}:</p>
     * <ul>
     *     <li>1.0: normal speed</li>
     *     <li>2.0: twice as fast</li>
     *     <li>0.5: half speed</li>
     * </ul>
     *
     * <p>Values <= 0 are clamped to a tiny positive value to avoid freezing or division edge cases.</p>
     *
     * @param speed playback speed multiplier
     */
    public void setSpeed(float speed) {
        this.speed = Math.max(0.000001f, speed);
    }

    /**
     * Returns the current playback speed multiplier.
     *
     * @return speed multiplier
     */
    public float getSpeed() {
        return speed;
    }

    /**
     * Returns the total duration of one forward pass (sum of all frame durations).
     *
     * <p>This is precomputed during construction from the provided frames.</p>
     *
     * @return total duration in seconds
     */
    public float getDuration() {
        return totalDuration;
    }

    /**
     * Returns the number of frames in this animation.
     *
     * @return frame count (0 if frames is null)
     */
    public int getFrameCount() {
        return frames == null ? 0 : frames.length;
    }

    /**
     * Returns progress in the range [0..1] for the current mode's forward-pass timeline.
     *
     * <p>Meaning:</p>
     * <ul>
     *     <li>FORWARD: 0 at start, 1 at end.</li>
     *     <li>REVERSE: still maps to 0..1 along the same forward timeline (via {@link #getTime()}).</li>
     *     <li>BIDIRECTIONAL: maps to the current forward-pass position (not the full ping-pong cycle).</li>
     * </ul>
     *
     * @return normalized progress in [0..1]
     */
    public float getProgress01() {
        if (frames == null || frames.length == 0) return 1f;
        if (totalDuration <= 0f) return 1f;

        float t = getTime();
        return MathUtils.clamp(t / totalDuration, 0f, 1f);
    }

    /**
     * Seeks the animation to an absolute time along the mode's logical timeline.
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>FORWARD: t = 0 means start; t = duration means end.</li>
     *     <li>REVERSE: t = 0 means end; t = duration means start.</li>
     *     <li>BIDIRECTIONAL: time wraps into a cycle of length 2*duration and selects forward/backward half.</li>
     * </ul>
     *
     * <p>This clears FINISHED state.</p>
     *
     * @param seconds time in seconds (clamped to >= 0 and into valid range)
     */
    public void setTime(float seconds) {
        if (frames == null || frames.length == 0) return;
        if (totalDuration <= 0f) {
            setFrame(0);
            return;
        }

        this.bits.clear(FINISHED);

        float t = Math.max(0f, seconds);

        if (playbackMode == PlaybackMode.BIDIRECTIONAL) {
            float cycle = totalDuration * 2f;
            if (cycle > 0f) t = t % cycle;

            if (t <= totalDuration) {
                bits.clear(RETURNING);
                setTimeForward(t);
            } else {
                bits.set(RETURNING);
                setTimeReverse(t - totalDuration);
            }
            return;
        }

        if (playbackMode == PlaybackMode.FORWARD) {
            setTimeForward(t);
        } else {
            setTimeReverse(t);
        }
    }

    /**
     * Returns the current absolute time along the mode's logical timeline.
     *
     * <p>Interpretation:</p>
     * <ul>
     *     <li>FORWARD / BIDIRECTIONAL: forward-pass time.</li>
     *     <li>REVERSE: reverse time where 0 means "at end".</li>
     * </ul>
     *
     * @return time in seconds
     */
    public float getTime() {
        if (frames == null || frames.length == 0) return 0f;

        return switch (playbackMode) {
            case FORWARD, BIDIRECTIONAL -> getTimeForward();
            case REVERSE -> getTimeReverse();
        };
    }

    /**
     * Sets an optional {@link AnimationListener}.
     *
     * <p>Set to null to disable callbacks.</p>
     *
     * @param animationListener animationListener or null
     */
    public void setListener(AnimationListener animationListener) {
        this.animationListener = animationListener;
    }

    /**
     * Returns the current {@link AnimationFrame}.
     *
     * <p>This may be null if frames are null/empty or if the current frame element is null.</p>
     *
     * @return current frame or null
     */
    public AnimationFrame getCurrentFrame() {
        if (frames == null || frames.length == 0) return null;
        return frames[currentIndex];
    }

    /**
     * Returns the backing frame array reference.
     *
     * <p>No defensive copy is made.</p>
     *
     * @return frames array (may be null)
     */
    public AnimationFrame[] getFrames() {
        return frames;
    }

    /**
     * Returns the current frame index.
     *
     * @return current frame index (0 when frames empty)
     */
    public short getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Enables or disables looping.
     *
     * <p>If you set a loop count with {@link #setLoopCount(int)}, looping is automatically enabled when loops != 0.</p>
     *
     * @param looping true to loop, false for one-shot behavior
     */
    public void setLooping(boolean looping) {
        this.bits.set(LOOPING, looping);
    }

    /**
     * Sets a loop count limit.
     *
     * <p>Values:</p>
     * <ul>
     *     <li>-1: infinite looping (if looping enabled)</li>
     *     <li>0: no looping (one-shot)</li>
     *     <li>N: loop N times</li>
     * </ul>
     *
     * <p>This also sets {@link #LOOPING} to (loops != 0).</p>
     *
     * @param loops loop limit (-1, 0, or N)
     */
    public void setLoopCount(int loops) {
        this.loopCountLimit = loops;
        this.bits.set(LOOPING, loops != 0);
    }

    /**
     * Returns the current loop count limit.
     *
     * @return loop limit (-1, 0, or N)
     */
    public int getLoopCount() {
        return loopCountLimit;
    }

    /**
     * Returns how many loops have completed so far.
     *
     * <p>This counter resets in {@link #reset()} and {@link #restart()}.</p>
     *
     * @return loops completed
     */
    public int getLoopsCompleted() {
        return loopsCompleted;
    }

    /**
     * Convenience helper to configure this as a one-shot animation.
     *
     * <p>This disables looping and clears any loop limit.</p>
     */
    public void playOnce() {
        setLoopCount(0);
        setLooping(false);
    }

    /**
     * Returns whether looping is enabled.
     *
     * @return true if looping
     */
    public boolean isLooping() {
        return bits.get(LOOPING);
    }

    /**
     * Returns whether updates are paused.
     *
     * @return true if paused
     */
    public boolean isPaused() {
        return bits.get(PAUSED);
    }

    /**
     * Pauses or unpauses the animation.
     *
     * <p>This does not affect the finished flag.</p>
     *
     * @param paused true to pause, false to resume
     */
    public void setPaused(boolean paused) {
        this.bits.set(PAUSED, paused);
    }

    /**
     * Returns the current playback mode.
     *
     * @return playback mode
     */
    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    /**
     * Sets a new playback mode.
     *
     * <p>This does not automatically reset the animation. If you want "mode start",
     * call {@link #reset()} after changing the mode.</p>
     *
     * @param playbackMode new playback mode
     */
    public void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = playbackMode;
    }

    /**
     * Determines whether another loop is allowed.
     *
     * <p>This checks:</p>
     * <ul>
     *     <li>Whether looping is enabled.</li>
     *     <li>Whether the loop limit (if any) has been reached.</li>
     * </ul>
     *
     * @return true if the animation should loop again
     */
    private boolean shouldLoop() {
        if (!bits.get(LOOPING)) return false;
        if (loopCountLimit < 0) return true;
        return loopsCompleted < loopCountLimit;
    }

    /**
     * Handles "loop completed" bookkeeping.
     *
     * <p>This increments {@link #loopsCompleted}, fires {@link AnimationListener#onLoop(Animation, int)},
     * and finishes the animation if the loop limit was reached.</p>
     */
    private void onLoopCompleted() {
        loopsCompleted++;

        if (animationListener != null) {
            animationListener.onLoop(this, loopsCompleted);
        }

        if (loopCountLimit >= 0 && loopsCompleted >= loopCountLimit) {
            finishNow();
        }
    }

    /**
     * Marks the animation as finished and paused, and fires {@link AnimationListener#onFinished(Animation)}.
     *
     * <p>This is used when a non-looping animation reaches an endpoint, or when a loop-limited
     * animation hits its loop limit.</p>
     */
    private void finishNow() {
        bits.set(PAUSED);
        bits.set(FINISHED);

        if (animationListener != null) {
            animationListener.onFinished(this);
        }
    }

    /**
     * Seeks to a forward time position within [0..duration].
     *
     * <p>This walks the frame durations cumulatively until it finds the frame containing {@code t},
     * then sets {@link #currentIndex} and {@link #elapsedTime} to match.</p>
     *
     * @param t forward time (seconds)
     */
    private void setTimeForward(float t) {
        t = MathUtils.clamp(t, 0f, totalDuration);

        float acc = 0f;
        for (int i = 0; i < frames.length; i++) {
            float d = frames[i] == null ? 0f : Math.max(0f, frames[i].duration());
            if (acc + d >= t || i == frames.length - 1) {
                currentIndex = (short) i;
                elapsedTime = Math.max(0f, t - acc);
                return;
            }
            acc += d;
        }

        currentIndex = (short) (frames.length - 1);
        elapsedTime = 0f;
    }

    /**
     * Seeks to a reverse time position where t=0 means "at end".
     *
     * <p>This converts reverse time into a forward remaining time and then selects the frame
     * that contains that remaining time.</p>
     *
     * @param t reverse time in seconds (0..duration)
     */
    private void setTimeReverse(float t) {
        t = MathUtils.clamp(t, 0f, totalDuration);

        float remaining = totalDuration - t;

        float acc = 0f;
        for (int i = 0; i < frames.length; i++) {
            float d = frames[i] == null ? 0f : Math.max(0f, frames[i].duration());
            if (acc + d >= remaining || i == frames.length - 1) {
                currentIndex = (short) i;
                elapsedTime = Math.max(0f, remaining - acc);
                return;
            }
            acc += d;
        }

        currentIndex = 0;
        elapsedTime = 0f;
    }

    /**
     * Computes the forward timeline time by summing all completed frame durations
     * plus {@link #elapsedTime} for the current frame.
     *
     * @return forward time in seconds
     */
    private float getTimeForward() {
        float acc = 0f;
        for (int i = 0; i < currentIndex; i++) {
            AnimationFrame f = frames[i];
            if (f != null) acc += Math.max(0f, f.duration());
        }
        return acc + elapsedTime;
    }

    /**
     * Computes the reverse timeline time where 0 means "at end".
     *
     * <p>This returns {@code duration - forwardTime}.</p>
     *
     * @return reverse time in seconds
     */
    private float getTimeReverse() {
        return Math.max(0f, totalDuration - getTimeForward());
    }
}