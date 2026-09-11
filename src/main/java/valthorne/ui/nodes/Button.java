package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.ui.NodeAction;
import valthorne.ui.theme.StyleKey;

/**
 * A themed panel with a centered label and primary-button/keyboard activation.
 * Release activation uses the root's left-button hit-test policy; keyboard activation
 * accepts Enter or Space on an enabled receiver. Input routing supplies focus and
 * capture behavior; these callbacks do not independently require an earlier press.
 *
 * <p>All children, including NanoVG nodes, use the panel's shared rendering path.
 * Background styling is inherited from Panel.</p>
 * Actions run synchronously after event consumption, and a null action disables
 * only the callback, not the button's normal input handling.
 *
 * @author Albert Beaupre
 */
public class Button extends Panel {
    /**
     * Alias of the panel background key, allowing button rules to use the shared
     * drawable property without registering a separate key.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = Panel.BACKGROUND_KEY;

    private final Label label = new Label(); // Centered child label owned by the node hierarchy.
    private NodeAction<Button> action; // Optional synchronous activation callback.

    /**
     * Creates a clickable, focusable button with a centered non-clickable label.
     * The label does not grow or shrink and is attached through the panel lifecycle.
     */
    public Button() {
        setClickable(true);
        setFocusable(true);
        getLayout().itemsCenter().justifyCenter();
        label.getLayout().noGrow().noShrink();
        label.setClickable(false);
        super.add(label);
    }

    /**
     * Creates a default button and supplies its initial label text.
     * Text handling and measurement are delegated to the child label.
     *
     * @param text initial label text
     */
    public Button(String text) {
        this();
        text(text);
    }

    /**
     * Returns the live child label for typography and layout customization.
     * The button hierarchy retains ownership of this child.
     *
     * @return the owned label
     */
    public Label getLabel() { return label; }
    /**
     * Reads the current text from the child label without creating a text snapshot
     * or changing layout.
     *
     * @return current label text
     */
    public String getText() { return label.getText(); }

    /**
     * Replaces the label text through the label's normal text and measurement path.
     * The action and focus state are preserved.
     *
     * @param text replacement label text
     * @return this button
     */
    public Button text(String text) {
        label.text(text);
        return this;
    }

    /**
     * Replaces the callback invoked for accepted activations. The callback receives
     * this button and runs synchronously; exceptions propagate to input dispatch.
     *
     * @param action replacement callback, or null to clear it
     * @return this button
     */
    public Button action(NodeAction<Button> action) {
        this.action = action;
        return this;
    }

    /**
     * Returns the configured activation callback without invoking it.
     *
     * @return current callback, or null when unset
     */
    public NodeAction<Button> getAction() { return action; }

    /**
     * Applies the shared release-activation policy, consuming accepted releases
     * before invoking the optional action. Rejected releases have no effect here.
     *
     * @param event routed mouse release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        valthorne.ui.behavior.ActivationBehavior.release(this, event, () -> { if (action != null) action.perform(this); });
    }

    /**
     * Consumes Enter or Space on an enabled button and invokes the optional action.
     * Focus routing and repeat filtering are not performed by this callback itself.
     *
     * @param event routed key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        valthorne.ui.behavior.ActivationBehavior.key(this, event, () -> { if (action != null) action.perform(this); });
    }
}
