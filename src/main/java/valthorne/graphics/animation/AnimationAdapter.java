package valthorne.graphics.animation;

/**
 * Adapter class for the {@link AnimationListener} interface.
 * <p>
 * This class provides empty implementations for the methods in the {@link AnimationListener} interface.
 * It can be used as a base class for creating listener objects where only a subset of methods need to be implemented.
 * Callbacks retain no state and do not alter playback; subclasses decide whether to
 * act on each notification delivered by the animation update loop.
 *
 * @author Albert Beaupre
 */
public class AnimationAdapter implements AnimationListener {
    /**
     * Ignores a frame transition. Override to react to a frame change without
     * implementing the remaining lifecycle callbacks.
     *
     * @param animation animation that advanced
     * @param fromIndex previous frame index
     * @param toIndex   newly active frame index
     */
    @Override
    public void onFrameChanged(Animation animation, int fromIndex, int toIndex) {

    }

    /**
     * Ignores playback completion and leaves the animation's finished state intact.
     *
     * @param animation animation that finished
     */
    @Override
    public void onFinished(Animation animation) {

    }

    /**
     * Ignores a completed loop. The supplied count has already been incremented
     * by the animation before this notification is delivered.
     *
     * @param animation      animation that crossed its loop boundary
     * @param loopsCompleted cumulative completed-loop count
     */
    @Override
    public void onLoop(Animation animation, int loopsCompleted) {

    }
}
