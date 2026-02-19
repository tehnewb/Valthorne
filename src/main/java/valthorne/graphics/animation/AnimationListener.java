package valthorne.graphics.animation;

/**
 * AnimationListener for animation lifecycle events.
 *
 * <p>All callbacks are invoked from within {@link Animation#update(float)}.</p>
 * <p>Keep implementations lightweight to avoid stalling your frame loop.</p>
 */
public interface AnimationListener {

    /**
     * Called when the currently active frame index of the animation changes.
     *
     * @param animation the animation instance where the frame change occurred
     * @param fromIndex the previous frame index before the change
     * @param toIndex   the new frame index after the change
     */
    void onFrameChanged(Animation animation, int fromIndex, int toIndex);

    /**
     * Called when the animation has finished.
     *
     * @param animation the animation instance that has completed
     */
    void onFinished(Animation animation);

    /**
     * Called when a loop completes.
     *
     * <p>FORWARD/REVERSE: completing a loop means wrapping around from end→start or start→end.</p>
     * <p>BIDIRECTIONAL: a "full loop" is counted when the ping-pong returns back to the start boundary.</p>
     *
     * @param animation      this animation instance
     * @param loopsCompleted total loops completed after increment
     */
    void onLoop(Animation animation, int loopsCompleted);
}