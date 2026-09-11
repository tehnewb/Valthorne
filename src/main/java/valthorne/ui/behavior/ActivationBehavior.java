package valthorne.ui.behavior;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.ui.UINode;

/**
 * Applies the activation policy shared by texture and NanoVG buttons and
 * checkboxes. Keyboard activation accepts Enter or Space on an enabled node;
 * release activation delegates to the node's left-button hit-test policy.
 * Accepted events are consumed before the action runs.
 *
 * <p>This utility supplies no focus routing, press tracking, repeat suppression,
 * or listener registration. Call it from a node's routed input callbacks so the
 * root remains responsible for choosing the receiver. An already-consumed event
 * is not filtered here; normal routing must avoid delivering it again.</p>
 *
 * <p>Actions execute synchronously on the calling thread. Their exceptions
 * propagate and do not undo event consumption or earlier action side effects.</p>
 *
 * @author Albert Beaupre
 */
public final class ActivationBehavior {
    /**
     * Prevents construction of this stateless utility. Activation is performed
     * through the static keyboard and release helpers without per-control state.
     */
    private ActivationBehavior() {}

    /**
     * Consumes an Enter or Space press on an enabled node and immediately invokes
     * the action. Other keys and disabled nodes leave the event untouched.
     * This helper does not independently verify focus or suppress key repeats.
     *
     * @param node   the routed receiver whose enabled state gates activation
     * @param event  the key press to inspect and consume if accepted
     * @param action the synchronous action to run for an accepted press
     * @throws NullPointerException if node is null, event is null while the node
     *                              is enabled, or action is null when activation is accepted
     */
    public static void key(UINode node, KeyPressEvent event, Runnable action) {
        if (node.isEnabled() && (event.getKey() == Keyboard.ENTER || event.getKey() == Keyboard.SPACE)) {
            event.consume();
            action.run();
        }
    }

    /**
     * Runs an action when {@link UINode#isActivationRelease(MouseReleaseEvent)}
     * accepts the release. That policy requires the left button, an enabled node
     * attached to a root, and this node as the clickable hit-test result at the
     * release position. It does not itself establish a matching earlier press.
     *
     * @param node   the routed receiver whose activation-release policy is checked
     * @param event  the release consumed before an accepted action runs
     * @param action the synchronous action for an accepted release
     * @throws NullPointerException if node or event is null, or action is null
     *                              when the release is accepted
     */
    public static void release(UINode node, MouseReleaseEvent event, Runnable action) {
        if (node.isActivationRelease(event)) {
            event.consume();
            action.run();
        }
    }
}
