package valthorne.event.events;

import valthorne.event.EventTypes;

/**
 * Mouse movement event emitted while a button is being dragged.
 *
 * <p>
 * Although this class extends {@link MouseMoveEvent} to reuse movement payload fields, it has its
 * own numeric route ({@link EventTypes#MOUSE_DRAG}). No superclass traversal is required during
 * publication.
 * </p>
 *
 * <p>Deltas are computed from the current payload rather than accumulated input.
 * Ending coordinates inherit signed-short storage, so their range differs from
 * the integer starting coordinates. Do not retain reusable events as snapshots.</p>
 *
 * @author Albert Beaupre
 */
public class MouseDragEvent extends MouseMoveEvent {

    /**
     * Creates a reusable notification on its concrete numeric route. Supplied payload
     * values are stored using the field types without additional range validation.
     *
     * @param button    dragged mouse button
     * @param modifiers modifier bit mask
     * @param fromX     starting X coordinate
     * @param fromY     starting Y coordinate
     * @param toX       ending X coordinate
     * @param toY       ending Y coordinate
     */
    public MouseDragEvent(int button, int modifiers, int fromX, int fromY, int toX, int toY) {
        super(EventTypes.MOUSE_DRAG, button, modifiers, fromX, fromY, toX, toY);
    }

    /**
     * Computes the current horizontal displacement as ending minus starting coordinate.
     * No input is accumulated or normalized; signed-short endpoint storage can affect the result.
     *
     * @return horizontal movement delta
     */
    public int getDeltaX() {
        return getToX() - getX();
    }

    /**
     * Computes the current vertical displacement as ending minus starting coordinate.
     * No input is accumulated or normalized; signed-short endpoint storage can affect the result.
     *
     * @return vertical movement delta
     */
    public int getDeltaY() {
        return getToY() - getY();
    }
}
