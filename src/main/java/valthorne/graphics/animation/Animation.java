package valthorne.graphics.animation;

import valthorne.collections.bits.ByteBits;
import valthorne.graphics.Drawable;
import valthorne.math.MathUtils;

/**
 * A lightweight, time-driven frame animation player.
 *
 * <p>{@code Animation} cycles through an array of {@link AnimationFrame} objects based on each frame's
 * duration, and exposes a simple API to update and render the current frame.</p>
 *
 * <h2>Playback</h2>
 * <ul>
 *     <li>{@link PlaybackMode#FORWARD}: 0 → 1 → 2 → ... → last</li>
 *     <li>{@link PlaybackMode#REVERSE}: last → ... → 2 → 1 → 0</li>
 *     <li>{@link PlaybackMode#BIDIRECTIONAL}: ping-pongs: 0 → ... → last → ... → 0</li>
 * </ul>
 *
 * <h2>Looping and pausing</h2>
 * <ul>
 *     <li>Looping is controlled by {@link #setLooping(boolean)}.</li>
 *     <li>Pausing is controlled by {@link #setPaused(boolean)}.</li>
 *     <li>Non-looping animations auto-pause when they reach the end of their path (depending on mode).</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Animation walk = new Animation(
 *         PlaybackMode.FORWARD,
 *         new AnimationFrame(playerWalk0, 0.08f),
 *         new AnimationFrame(playerWalk1, 0.08f),
 *         new AnimationFrame(playerWalk2, 0.08f),
 *         new AnimationFrame(playerWalk3, 0.08f)
 * );
 * walk.setLooping(true);
 *
 * // In your update loop:
 * walk.update(delta);
 *
 * // In your render loop:
 * walk.draw(x, y, width, height);
 * }</pre>
 *
 * <p><b>Important:</b> This class assumes {@code frames} is non-null and has at least 1 frame.
 * If you pass a null/empty frame array, {@link #update(float)} will safely no-op, but some
 * getters (like {@link #getCurrentFrame()}, {@link #getWidth()}, {@link #getHeight()}) may throw
 * if called.</p>
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
 */
public class Animation implements Drawable {

    private static byte PAUSED = 0;                 // Bit index: whether the animation update is paused.
    private static byte LOOPING = 1;                // Bit index: whether the animation loops when reaching an endpoint.
    private static byte RETURNING = 2;              // Bit index: BIDIRECTIONAL-only flag indicating we are moving backward.

    private final ByteBits bits = new ByteBits();   // Packed boolean state flags (paused/looping/returning).
    private PlaybackMode playbackMode;              // Current playback direction/behavior mode.
    private final AnimationFrame[] frames;          // Ordered array of frames that this animation plays.
    private short currentIndex;                     // Current frame index within {@link #frames}.
    private float elapsedTime;                      // Time accumulated on the current frame (seconds).

    /**
     * Creates an {@code Animation} with a playback mode and a set of frames.
     *
     * <p>The animation begins on frame index 0, with elapsed time 0.</p>
     *
     * @param playbackMode the playback mode to use (FORWARD/REVERSE/BIDIRECTIONAL)
     * @param frames       the frames to play (ordered)
     */
    public Animation(PlaybackMode playbackMode, AnimationFrame... frames) {
        this.playbackMode = playbackMode;
        this.frames = frames;
    }

    /**
     * Draws the current frame using the provided destination rectangle.
     *
     * <p>If the current frame is null, this method does nothing.</p>
     *
     * @param x      destination x in world space
     * @param y      destination y in world space
     * @param width  destination width
     * @param height destination height
     */
    public void draw(float x, float y, float width, float height) {
        AnimationFrame currentFrame = getCurrentFrame();
        if (currentFrame == null) return;

        currentFrame.drawable().draw(x, y, width, height);
    }

    /**
     * Returns the width of the current frame's drawable.
     *
     * <p>This is a convenience pass-through to {@link Drawable#getWidth()}.</p>
     *
     * @return current frame drawable width
     */
    @Override
    public float getWidth() {
        return getCurrentFrame().drawable().getWidth();
    }

    /**
     * Returns the height of the current frame's drawable.
     *
     * <p>This is a convenience pass-through to {@link Drawable#getHeight()}.</p>
     *
     * @return current frame drawable height
     */
    @Override
    public float getHeight() {
        return getCurrentFrame().drawable().getHeight();
    }

    /**
     * Advances the animation based on elapsed time.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If paused, does nothing.</li>
     *     <li>If {@link #frames} is null or empty, does nothing.</li>
     *     <li>Accumulates {@code delta} into {@link #elapsedTime}.</li>
     *     <li>When accumulated time exceeds the current frame's duration, advances to the next frame
     *         depending on {@link #playbackMode}.</li>
     * </ul>
     *
     * <p>Endpoint rules (non-looping):</p>
     * <ul>
     *     <li>FORWARD stops at the last frame and pauses.</li>
     *     <li>REVERSE stops at the first frame and pauses.</li>
     *     <li>BIDIRECTIONAL bounces at the ends; when not looping it pauses after the first bounce step.</li>
     * </ul>
     *
     * <p><b>Note:</b> This implementation advances at most one frame per call (even if {@code delta}
     * is large). If you want to catch up across multiple frames in a single update, change the duration
     * check into a {@code while (elapsedTime >= frame.duration())} loop.</p>
     *
     * @param delta elapsed time since last update, in seconds
     */
    public void update(float delta) {
        if (bits.get(PAUSED) || frames == null || frames.length == 0) return;

        elapsedTime += delta;

        AnimationFrame frame = frames[currentIndex];
        if (elapsedTime < frame.duration()) return;

        elapsedTime -= frame.duration();

        switch (playbackMode) {

            case FORWARD -> {
                currentIndex++;

                if (currentIndex >= frames.length) {
                    if (bits.get(LOOPING)) {
                        currentIndex = 0;
                    } else {
                        currentIndex = (short) (frames.length - 1);
                        bits.set(PAUSED);
                    }
                }
            }

            case REVERSE -> {
                currentIndex--;

                if (currentIndex < 0) {
                    if (bits.get(LOOPING)) {
                        currentIndex = (short) (frames.length - 1);
                    } else {
                        currentIndex = 0;
                        bits.set(PAUSED);
                    }
                }
            }

            case BIDIRECTIONAL -> {
                if (!bits.get(RETURNING)) {
                    currentIndex++;
                    if (currentIndex >= frames.length) {
                        currentIndex = (short) (frames.length - 2);
                        bits.set(RETURNING);
                        if (!bits.get(LOOPING)) bits.set(PAUSED);
                    }
                } else {
                    currentIndex--;
                    if (currentIndex < 0) {
                        currentIndex = 1;
                        bits.clear(RETURNING);
                        if (!bits.get(LOOPING)) bits.set(PAUSED);
                    }
                }
            }
        }
    }

    /**
     * Returns whether the animation has finished playing (FORWARD semantics).
     *
     * <p>This check is most meaningful for {@link PlaybackMode#FORWARD} non-looping animations.
     * In other modes, "finished" can be ambiguous. This method preserves your original logic:
     * it reports finished when the index is on the last frame and the time has reached/exceeded
     * that frame's duration.</p>
     *
     * @return true if the animation is at the last frame and has consumed its duration
     */
    public boolean isFinished() {
        return this.currentIndex >= frames.length - 1 && elapsedTime >= getCurrentFrame().duration();
    }

    /**
     * Forces the animation to a specific frame index and resets elapsed time on that frame.
     *
     * <p>The index is clamped into the valid range: {@code [0, frames.length - 1]}.</p>
     *
     * @param index the desired frame index
     */
    public void setFrame(int index) {
        this.elapsedTime = 0;
        this.currentIndex = (short) MathUtils.clamp(index, 0, frames.length - 1);
    }

    /**
     * Returns the current {@link AnimationFrame}.
     *
     * @return the current frame (may be null if the array contains null entries)
     */
    public AnimationFrame getCurrentFrame() {
        return frames[currentIndex];
    }

    /**
     * Returns the backing array of frames.
     *
     * <p>No defensive copy is made. Mutating the returned array will mutate the animation.</p>
     *
     * @return the frame array
     */
    public AnimationFrame[] getFrames() {
        return frames;
    }

    /**
     * Returns the current frame index.
     *
     * @return current frame index
     */
    public short getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Enables or disables looping.
     *
     * <p>If looping is enabled, reaching an endpoint will wrap/bounce depending on {@link #playbackMode}
     * rather than pausing.</p>
     *
     * @param looping true to loop, false for one-shot behavior
     */
    public void setLooping(boolean looping) {
        this.bits.set(LOOPING, looping);
    }

    /**
     * Pauses or unpauses the animation.
     *
     * <p>When paused, {@link #update(float)} does nothing and the current frame is held.</p>
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
     * Sets a new playback mode for this animation.
     *
     * <p>This does not reset {@link #currentIndex} or {@link #elapsedTime}. If you want a clean restart
     * after changing modes, call {@link #setFrame(int)} as well.</p>
     *
     * @param playbackMode the new playback mode
     */
    public void setPlaybackMode(PlaybackMode playbackMode) {
        this.playbackMode = playbackMode;
    }
}