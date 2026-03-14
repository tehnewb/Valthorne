package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NodeAction;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * <p>
 * {@code Checkbox} is a toggleable UI control built on top of {@link Panel}.
 * It supports checked and unchecked states, optional action callbacks, keyboard
 * activation, mouse activation, and theme-driven background and checkmark visuals.
 * </p>
 *
 * <p>
 * This class is designed to be a simple reusable boolean input control. It stores
 * a local checked flag and mirrors that flag into the inherited node checked state
 * through {@code setChecked(boolean)} whenever the value changes.
 * </p>
 *
 * <p>
 * Visuals are entirely style-driven:
 * </p>
 *
 * <ul>
 *     <li>{@link #BACKGROUND_KEY} resolves the checkbox background drawable</li>
 *     <li>{@link #CHECKMARK_KEY} resolves the drawable shown when checked</li>
 *     <li>{@link #ACTION_KEY} optionally resolves a style-provided action</li>
 * </ul>
 *
 * <p>
 * The checkbox may have an explicitly assigned action through
 * {@link #action(NodeAction)}. If no explicit action is assigned, it will attempt
 * to resolve one from the current style when the checked state changes.
 * </p>
 *
 * <p>
 * Interaction behavior is as follows:
 * </p>
 *
 * <ul>
 *     <li>Space or Enter toggles the checkbox when focused</li>
 *     <li>Mouse release toggles the checkbox when clicked</li>
 *     <li>Changing the checked state triggers the resolved action if one exists</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Checkbox checkbox = new Checkbox();
 *
 * checkbox.getLayout()
 *         .width(24)
 *         .height(24);
 *
 * checkbox.action(c -> {
 *     System.out.println("Checked: " + c.isChecked());
 * });
 *
 * checkbox.checked(true);
 * checkbox.toggle();
 *
 * boolean checked = checkbox.isChecked();
 * NodeAction<Checkbox> action = checkbox.getAction();
 *
 * checkbox.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete usage of the class: construction,
 * layout sizing, action assignment, state changes, querying state, and drawing.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Checkbox extends Panel {

    /**
     * Style key used to resolve the checkbox background drawable.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = StyleKey.of("background", Drawable.class);

    /**
     * Style key used to resolve the checkbox checkmark drawable.
     */
    public static final StyleKey<Drawable> CHECKMARK_KEY = StyleKey.of("checkmark", Drawable.class);

    /**
     * Style key used to resolve a fallback checkbox action from the current style.
     */
    public static final StyleKey<NodeAction<Checkbox>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<Checkbox>>) (Class<?>) NodeAction.class);

    private NodeAction<Checkbox> action; // Explicit action executed when the checked state changes
    private boolean checked; // Current checked state of the checkbox

    /**
     * <p>
     * Creates a new checkbox.
     * </p>
     *
     * <p>
     * The checkbox is configured as clickable and focusable so it can be interacted
     * with using both mouse and keyboard input.
     * </p>
     */
    public Checkbox() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
    }

    /**
     * <p>
     * Returns the explicitly assigned action for this checkbox.
     * </p>
     *
     * @return the explicit action, or {@code null} if none exists
     */
    public NodeAction<Checkbox> getAction() {
        return action;
    }

    /**
     * <p>
     * Assigns an explicit action to this checkbox.
     * </p>
     *
     * <p>
     * This action takes priority over any action resolved from the current style.
     * </p>
     *
     * @param action the action to assign
     * @return this checkbox
     */
    public Checkbox action(NodeAction<Checkbox> action) {
        this.action = action;
        return this;
    }

    /**
     * <p>
     * Returns whether this checkbox is currently checked.
     * </p>
     *
     * @return {@code true} if checked
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * <p>
     * Sets the checked state of this checkbox.
     * </p>
     *
     * <p>
     * If the state is unchanged, the method returns immediately. Otherwise the local
     * checked flag is updated, the inherited node checked state is synchronized, and
     * a resolved action is executed if one is available.
     * </p>
     *
     * <p>
     * Action resolution order is:
     * </p>
     *
     * <ol>
     *     <li>use the explicitly assigned action if present</li>
     *     <li>otherwise try to resolve {@link #ACTION_KEY} from the current style</li>
     * </ol>
     *
     * @param checked the new checked state
     * @return this checkbox
     */
    public Checkbox checked(boolean checked) {
        if (this.checked == checked)
            return this;

        this.checked = checked;
        setChecked(checked);

        NodeAction<Checkbox> resolvedAction = action;
        if (resolvedAction == null) {
            ResolvedStyle style = getStyle();
            if (style != null)
                resolvedAction = style.get(ACTION_KEY);
        }

        if (resolvedAction != null)
            resolvedAction.perform(this);

        return this;
    }

    /**
     * <p>
     * Toggles the checked state of this checkbox.
     * </p>
     *
     * @return this checkbox
     */
    public Checkbox toggle() {
        return checked(!checked);
    }

    /**
     * <p>
     * Handles keyboard activation for this checkbox.
     * </p>
     *
     * <p>
     * Pressing Space or Enter toggles the checked state.
     * </p>
     *
     * @param event the key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        int key = event.getKey();
        if (key == Keyboard.SPACE || key == Keyboard.ENTER)
            toggle();
    }

    /**
     * <p>
     * Handles mouse activation for this checkbox.
     * </p>
     *
     * <p>
     * Releasing the mouse over the checkbox toggles the checked state.
     * </p>
     *
     * @param event the mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        toggle();
    }

    /**
     * <p>
     * Draws the checkbox using the provided {@link TextureBatch}.
     * </p>
     *
     * <p>
     * If the checkbox is not visible, rendering is skipped. Otherwise the current
     * style is resolved and the background drawable is rendered first. If the
     * checkbox is checked, the checkmark drawable is then rendered centered inside
     * the checkbox bounds at half the checkbox's width and height.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (!isVisible())
            return;

        ResolvedStyle style = getStyle();
        if (style != null) {
            Drawable background = style.get(BACKGROUND_KEY);
            if (background != null)
                background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());

            if (checked) {
                Drawable checkmark = style.get(CHECKMARK_KEY);
                if (checkmark != null) {
                    float checkmarkWidth = getWidth() * 0.5f;
                    float checkmarkHeight = getHeight() * 0.5f;
                    float checkmarkX = getRenderX() + (getWidth() - checkmarkWidth) * 0.5f;
                    float checkmarkY = getRenderY() + (getHeight() - checkmarkHeight) * 0.5f;
                    checkmark.draw(batch, checkmarkX, checkmarkY, checkmarkWidth, checkmarkHeight);
                }
            }
        }
    }
}