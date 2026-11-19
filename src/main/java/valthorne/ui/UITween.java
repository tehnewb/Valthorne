package valthorne.ui;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;

/**
 * Universal Tween Engine accessor shared by all retained and Nano UI nodes.
 * Position uses local top-left layout coordinates and size uses layout units.
 * Prefer absolute positioning for movement: flow layout, percentage constraints,
 * margins and parent padding can constrain the final bounds. Layout values are
 * captured from computed geometry when the tween starts. Rotation is in degrees.
 * Opacity affects the whole subtree and is multiplied with ancestor opacity.
 * Rotation and opacity updates allocate nothing; layout updates use the existing
 * layout setters and their immutable values. All access is on the UI thread.
 */
public final class UITween implements TweenAccessor<UINode> {
    /**
     * Local top-left position, with targets X and Y.
     */
    public static final int POSITION = 1;
    /**
     * Layout width and height, with two target values.
     */
    public static final int SIZE = 2;
    /**
     * Counterclockwise rotation in degrees, with one target value.
     */
    public static final int ROTATION = 3;
    /**
     * Subtree opacity, with one target value from zero through one.
     */
    public static final int OPACITY = 4;
    /**
     * Shared stateless accessor used when a root initializes its manager.
     */
    static final UITween ACCESSOR = new UITween();

    static {
        Tween.registerAccessor(UINode.class, ACCESSOR);
    }

    /**
     * Creates the stateless shared accessor during class initialization.
     */
    private UITween() {}

    /**
     * Creates a pooled tween with an explicit accessor class for UI subclasses.
     * @param node target node
     * @param property property identifier
     * @param duration nonnegative duration in seconds
     * @return unstarted configured tween
     */
    static Tween create(UINode node, int property, float duration) {
        /*
         * Explicit casting avoids runtime superclass discovery in browser exports.
         */
        if (property < POSITION || property > OPACITY) throw new IllegalArgumentException("Unknown UI tween property: " + property);
        if (!Float.isFinite(duration) || duration < 0) throw new IllegalArgumentException("Duration must be finite and nonnegative");
        return Tween.to(node, property, duration)
                .cast(UINode.class);
    }

    @Override
    public int getValues(UINode node, int property, float[] values) {
        if ((property == POSITION || property == SIZE) && !node.hasYogaNode())
            throw new IllegalStateException("Layout tweens require an attached UI node");
        switch (property) {
            case POSITION -> {
                values[0] = node.getX();
                values[1] = node.getY();
                return 2;
            }
            case SIZE -> {
                values[0] = node.getWidth();
                values[1] = node.getHeight();
                return 2;
            }
            case ROTATION -> {
                values[0] = node.getRotation();
                return 1;
            }
            case OPACITY -> {
                values[0] = node.getOpacity();
                return 1;
            }
            default -> throw new IllegalArgumentException("Unknown UI tween property: " + property);
        }
    }

    @Override
    public void setValues(UINode node, int property, float[] values) {
        // A callback can detach the target after the engine's cancellation check.
        if (!node.hasYogaNode()) return;
        switch (property) {
            case POSITION -> node.getLayout()
                    .left(values[0])
                    .top(values[1]);
            case SIZE -> node.setSize(Math.max(0, values[0]), Math.max(0, values[1]));
            case ROTATION -> node.setRotation(values[0]);
            case OPACITY -> node.setOpacity(values[0]);
            default -> throw new IllegalArgumentException("Unknown UI tween property: " + property);
        }
    }
}
