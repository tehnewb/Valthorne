package valthorne.graphics.animation;

/**
 * Adapter class for the {@link AnimationListener} interface.
 * <p>
 * This class provides empty implementations for the methods in the {@link AnimationListener} interface.
 * It can be used as a base class for creating listener objects where only a subset of methods need to be implemented.
 */
public class AnimationAdapter implements AnimationListener {
    @Override
    public void onFrameChanged(Animation animation, int fromIndex, int toIndex) {

    }

    @Override
    public void onFinished(Animation animation) {

    }

    @Override
    public void onLoop(Animation animation, int loopsCompleted) {

    }
}
