package valthorne.ui.elements;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.Element;
import valthorne.ui.UIAction;
import valthorne.ui.styles.CheckboxStyle;

/**
 * <h1>Checkbox</h1>
 *
 * <p>
 * {@code Checkbox} is a toggleable UI element that represents a boolean state. It renders a background
 * drawable and, when checked, an optional checkmark drawable centered inside that background. The
 * checkbox supports mouse interaction, keyboard toggling, state-aware styling, and an optional action
 * callback that fires whenever the checked state changes.
 * </p>
 *
 * <p>
 * This element is intended for common on/off UI scenarios such as enabling settings, selecting options,
 * acknowledging conditions, or activating flags inside forms and menus. It integrates with the rest of
 * the UI system through the normal {@link Element} lifecycle and state model.
 * </p>
 *
 * <h2>Visual states</h2>
 * <p>
 * A checkbox can present different drawables depending on its interaction state:
 * </p>
 * <ul>
 *     <li>Default background</li>
 *     <li>Hovered background</li>
 *     <li>Pressed background</li>
 *     <li>Focused background</li>
 *     <li>Disabled background</li>
 * </ul>
 *
 * <p>
 * The actual displayed background is stored in {@link #current}, which is updated as the checkbox moves
 * through interaction states. If the checkbox is checked and a checkmark drawable exists, the checkmark
 * is drawn centered within the checkbox bounds at half the size of the control.
 * </p>
 *
 * <h2>Input behavior</h2>
 * <p>
 * Mouse release toggles the checked state after restoring the correct visual background. Keyboard input
 * supports toggling through Enter and Space when the checkbox is focused and enabled. This gives the
 * element standard accessibility-like behavior for keyboard navigation in UI flows.
 * </p>
 *
 * <h2>Action callback behavior</h2>
 * <p>
 * Whenever the checked state changes through {@link #setChecked(boolean)}, the optional
 * {@link UIAction} is invoked. This allows external code to react immediately to state changes without
 * polling the checkbox every frame.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Checkbox rememberMe = new Checkbox(
 *     CheckboxStyle.of()
 *         .background(boxDrawable)
 *         .hovered(boxHoveredDrawable)
 *         .pressed(boxPressedDrawable)
 *         .focused(boxFocusedDrawable)
 *         .disabled(boxDisabledDrawable)
 *         .checkmark(checkDrawable)
 * );
 *
 * rememberMe.setPosition(32f, 32f);
 * rememberMe.setSize(24f, 24f);
 * rememberMe.setAction(cb -> {
 *     System.out.println("Checked: " + cb.isChecked());
 * });
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class Checkbox extends Element {

    private CheckboxStyle style; // The style object that supplies the checkbox drawables for all visual states.
    private Drawable current; // The currently active background drawable resolved from the checkbox state.
    private UIAction<Checkbox> action; // The optional callback invoked whenever the checked state changes.
    private boolean checked; // Whether this checkbox is currently checked.

    /**
     * Creates a checkbox using the supplied style.
     *
     * <p>
     * The checkbox starts with the style's default background as its current drawable and is marked as
     * focusable so it can participate in keyboard navigation and keyboard toggling.
     * </p>
     *
     * @param style the style that defines the checkbox appearance
     */
    public Checkbox(CheckboxStyle style) {
        if (style == null) {
            throw new NullPointerException("style");
        }

        this.style = style;
        this.current = style.getBackground();
        this.setFocusable(true);
    }

    /**
     * Creates a checkbox with a default empty {@link CheckboxStyle}.
     *
     * <p>
     * This constructor is useful when the style will be configured later through {@link #setStyle(CheckboxStyle)}.
     * </p>
     */
    public Checkbox() {
        this(new CheckboxStyle());
    }

    /**
     * Updates the checkbox each frame.
     *
     * <p>
     * The checkbox currently has no time-based behavior, so this method performs no work. It still
     * exists to satisfy the UI lifecycle and to allow future expansion without changing the class API.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    @Override
    public void update(float delta) {
    }

    /**
     * Draws the checkbox background and optional checkmark.
     *
     * <p>
     * Rendering occurs in two stages:
     * </p>
     * <ol>
     *     <li>The current background drawable is rendered using the checkbox bounds.</li>
     *     <li>If the checkbox is checked and a checkmark drawable exists, the checkmark is rendered
     *     centered inside the checkbox at half the checkbox size.</li>
     * </ol>
     *
     * <p>
     * If no current background drawable is available, nothing is drawn.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (current == null) {
            return;
        }

        current.draw(batch, this.x, this.y, this.width, this.height);

        if (checked && style.getCheckmark() != null) {
            float checkmarkWidth = this.width * 0.5f;
            float checkmarkHeight = this.height * 0.5f;
            float checkmarkX = this.x + (this.width - checkmarkWidth) * 0.5f;
            float checkmarkY = this.y + (this.height - checkmarkHeight) * 0.5f;

            style.getCheckmark().draw(batch, checkmarkX, checkmarkY, checkmarkWidth, checkmarkHeight);
        }
    }

    /**
     * Handles key press input for the checkbox.
     *
     * <p>
     * When the checkbox is enabled and focused, pressing Space or Enter toggles the checked state.
     * This gives the checkbox standard keyboard interaction behavior.
     * </p>
     *
     * @param event the key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (isDisabled()) {
            return;
        }

        if (event.getKey() == Keyboard.SPACE || event.getKey() == Keyboard.ENTER) {
            setChecked(!checked);
        }
    }

    /**
     * Handles key release input for the checkbox.
     *
     * <p>
     * When a key is released, the checkbox restores its visual background according to its current state.
     * This prevents the checkbox from remaining visually pressed after keyboard interaction ends.
     * </p>
     *
     * @param event the key release event
     */
    @Override
    public void onKeyRelease(KeyReleaseEvent event) {
        current = resolveCurrentDrawable();
    }

    /**
     * Handles mouse press input for the checkbox.
     *
     * <p>
     * When the checkbox is enabled and a pressed drawable exists, that drawable becomes the current
     * background so the control reflects an active press state immediately.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (isDisabled()) {
            return;
        }

        if (style.getPressed() != null) {
            current = style.getPressed();
        }
    }

    /**
     * Handles mouse release input for the checkbox.
     *
     * <p>
     * When released, the checkbox first restores the correct background based on its current state and
     * then toggles the checked value. Toggling occurs through {@link #setChecked(boolean)} so the action
     * callback is fired consistently.
     * </p>
     *
     * @param event the mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (isDisabled()) {
            return;
        }

        current = resolveCurrentDrawable();
        setChecked(!checked);
    }

    /**
     * Sets the style used by this checkbox.
     *
     * <p>
     * The style must not be null. After the style is replaced, the current drawable is immediately
     * re-resolved so the checkbox appearance matches its current state under the new style.
     * </p>
     *
     * @param style the new checkbox style
     * @return this checkbox for chaining
     */
    public Checkbox setStyle(CheckboxStyle style) {
        if (style == null) {
            throw new NullPointerException("style");
        }

        this.style = style;
        this.current = resolveCurrentDrawable();
        return this;
    }

    /**
     * Returns the current style used by this checkbox.
     *
     * @return the active checkbox style
     */
    public CheckboxStyle getStyle() {
        return style;
    }

    /**
     * Updates the disabled state of the checkbox.
     *
     * <p>
     * After the disabled state changes, the current drawable is re-resolved so the visual appearance
     * immediately reflects whether the checkbox is enabled or disabled.
     * </p>
     *
     * @param value true to disable the checkbox, false to enable it
     */
    @Override
    public void setDisabled(boolean value) {
        super.setDisabled(value);
        current = resolveCurrentDrawable();
    }

    /**
     * Updates the focused state of the checkbox.
     *
     * <p>
     * After the focus state changes, the current drawable is re-resolved so the checkbox can visually
     * reflect focus when the style provides a focused drawable.
     * </p>
     *
     * @param value true if focused, false otherwise
     */
    @Override
    protected void setFocused(boolean value) {
        super.setFocused(value);
        current = resolveCurrentDrawable();
    }

    /**
     * Updates the hovered state of the checkbox.
     *
     * <p>
     * After the hover state changes, the current drawable is re-resolved so the checkbox can visually
     * reflect pointer hover when the style provides a hovered drawable.
     * </p>
     *
     * @param value true if hovered, false otherwise
     */
    @Override
    protected void setHovered(boolean value) {
        super.setHovered(value);
        current = resolveCurrentDrawable();
    }

    /**
     * Assigns the action callback invoked whenever the checked state changes.
     *
     * @param action the callback to assign, or null to clear it
     * @return this checkbox for chaining
     */
    public Checkbox setAction(UIAction<Checkbox> action) {
        this.action = action;
        return this;
    }

    /**
     * Returns the action callback currently assigned to this checkbox.
     *
     * @return the current checkbox action, or null if none is assigned
     */
    public UIAction<Checkbox> getAction() {
        return action;
    }

    /**
     * Returns whether the checkbox is currently checked.
     *
     * @return true if checked, otherwise false
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Sets whether the checkbox is checked.
     *
     * <p>
     * If the checked state changes, the optional action callback is invoked. If the supplied state is
     * the same as the current state, the checkbox remains unchanged and no action is fired.
     * </p>
     *
     * @param checked the new checked state
     * @return this checkbox for chaining
     */
    public Checkbox setChecked(boolean checked) {
        if (this.checked == checked) {
            return this;
        }

        this.checked = checked;

        if (action != null) {
            action.perform(this);
        }

        return this;
    }

    /**
     * Resolves the background drawable that should currently be displayed.
     *
     * <p>
     * Resolution follows the checkbox state in priority order:
     * </p>
     * <ol>
     *     <li>Disabled</li>
     *     <li>Pressed</li>
     *     <li>Hovered</li>
     *     <li>Focused</li>
     *     <li>Background</li>
     * </ol>
     *
     * <p>
     * If a state-specific drawable is missing, resolution falls through to lower-priority defaults.
     * </p>
     *
     * @return the drawable that should be used as the current background
     */
    private Drawable resolveCurrentDrawable() {
        if (isDisabled() && style.getDisabled() != null) {
            return style.getDisabled();
        }

        if (isPressed() && style.getPressed() != null) {
            return style.getPressed();
        }

        if (isHovered() && style.getHovered() != null) {
            return style.getHovered();
        }

        if (isFocused() && style.getFocused() != null) {
            return style.getFocused();
        }

        return style.getBackground();
    }
}