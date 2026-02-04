package valthorne.ui.elements;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.DrawFunction;
import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.math.Vector2f;
import valthorne.ui.*;
import valthorne.ui.styles.ButtonStyle;

/**
 * A clickable UI button element that renders a styled background and centered (or aligned) text.
 *
 * <p>This {@link Button} integrates with your UI event system and {@link ButtonStyle} to provide
 * standard button interactions:</p>
 *
 * <ul>
 *     <li>Hover state (mouse over)</li>
 *     <li>Pressed state (mouse down / Enter key down)</li>
 *     <li>Focused state (UI focus)</li>
 *     <li>Disabled state (non-interactive)</li>
 * </ul>
 *
 * <p>Rendering is performed using the fixed-function pipeline through {@link Drawable} and {@link Font}.
 * Drawing is clipped to the button's bounds using {@code Viewport.applyScissor(...)} so text and
 * backgrounds never bleed outside the element rectangle.</p>
 *
 * <h2>Interaction Model</h2>
 * <ul>
 *     <li>Mouse press sets the pressed background.</li>
 *     <li>Mouse release restores hovered/background and triggers {@link UIAction} (if set).</li>
 *     <li>Enter key press sets pressed background (if an action exists).</li>
 *     <li>Key release restores background.</li>
 *     <li>Hover/focus/disabled updates swap the current drawable if the style provides those drawables.</li>
 * </ul>
 *
 * <h2>Text Alignment</h2>
 * <p>The text is positioned using {@link Alignment#align(Dimensional, Sizeable, Alignment)} based on
 * {@link #textAlignment}. The font position is recalculated whenever the button moves or resizes.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ButtonStyle style = new ButtonStyle()
 *     .setFontData(myFontData)
 *     .setBackground(bg)
 *     .setHovered(hover)
 *     .setPressed(pressed)
 *     .setFocused(focused)
 *     .setDisabled(disabled);
 *
 * Button play = new Button("Play", Alignment.CENTER_CENTER, b -> {
 *     System.out.println("Play clicked!");
 * }, style);
 *
 * play.setPosition(40, 40);
 * play.setSize(240, 56);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 3rd, 2026
 */
public class Button extends Element {

    private final Font font;                 // Text renderer used to measure/draw the button label.
    private ButtonStyle style;               // Style source providing drawables for button states.
    private Drawable current;                // Currently active drawable (background/hovered/pressed/etc.).
    private UIAction<Button> action;         // Optional action invoked on activation (mouse release / Enter).
    private Alignment textAlignment;         // Alignment used to position the font relative to this element.
    private final DrawFunction draw;         // Scissored draw callback that renders background + text.

    /**
     * Constructs a button with centered text and a default {@link ButtonStyle}.
     *
     * <p>This is equivalent to {@code new Button(text, Alignment.CENTER_CENTER)}.</p>
     *
     * @param text initial button label (null becomes an empty string via {@link #setText(String)})
     */
    public Button(String text) {
        this(text, Alignment.CENTER_CENTER);
    }

    /**
     * Constructs a button with a specific text alignment and a default {@link ButtonStyle}.
     *
     * <p>This constructor does not assign an action. Use {@link #setAction(UIAction)} or a constructor
     * that accepts an action.</p>
     *
     * @param text      initial button label
     * @param alignment text alignment for the label
     */
    public Button(String text, Alignment alignment) {
        this(text, alignment, null, new ButtonStyle());
    }

    /**
     * Constructs a button with a specific alignment and style, but no action.
     *
     * @param text      initial button label
     * @param alignment text alignment for the label
     * @param style     style providing drawables and font data
     */
    public Button(String text, Alignment alignment, ButtonStyle style) {
        this(text, alignment, null, style);
    }

    /**
     * Constructs a button with centered text and a specific style, but no action.
     *
     * @param text  initial button label
     * @param style style providing drawables and font data
     */
    public Button(String text, ButtonStyle style) {
        this(text, Alignment.CENTER_CENTER, null, style);
    }

    /**
     * Constructs a centered button with a default style and an action.
     *
     * @param text   initial button label
     * @param action action invoked on activation (mouse release / Enter)
     */
    public Button(String text, UIAction<Button> action) {
        this(text, Alignment.CENTER_CENTER, action, new ButtonStyle());
    }

    /**
     * Constructs a button with a specific alignment, a default style, and an action.
     *
     * @param text      initial button label
     * @param alignment text alignment for the label
     * @param action    action invoked on activation (mouse release / Enter)
     */
    public Button(String text, Alignment alignment, UIAction<Button> action) {
        this(text, alignment, action, new ButtonStyle());
    }

    /**
     * Constructs a centered button with a specific style and an action.
     *
     * @param text   initial button label
     * @param action action invoked on activation (mouse release / Enter)
     * @param style  style providing drawables and font data
     */
    public Button(String text, UIAction<Button> action, ButtonStyle style) {
        this(text, Alignment.CENTER_CENTER, action, style);
    }

    /**
     * Primary constructor that initializes button state, styling, font, and draw callback.
     *
     * <p>Initialization steps:</p>
     * <ul>
     *     <li>Stores style and alignment</li>
     *     <li>Sets current drawable to style background</li>
     *     <li>Creates a new {@link Font} from {@link ButtonStyle#getFontData()}</li>
     *     <li>Sets the label text on the font</li>
     *     <li>Marks the button as focusable</li>
     *     <li>Builds the draw callback for scissored rendering</li>
     * </ul>
     *
     * <p>Note: This constructor does not automatically reposition text; the font is aligned when
     * {@link #setPosition(float, float)} and {@link #setSize(float, float)} are called.</p>
     *
     * @param text      initial button label
     * @param alignment text alignment for the label
     * @param action    action invoked on activation (may be null)
     * @param style     style providing drawables and font data (must not be null)
     */
    public Button(String text, Alignment alignment, UIAction<Button> action, ButtonStyle style) {
        this.style = style;
        this.textAlignment = alignment;
        this.current = style.getBackground();
        this.font = new Font(style.getFontData());
        this.font.setText(text);
        this.action = action;
        this.setFocusable(true);
        this.draw = () -> {
            current.draw(this.x, this.y, this.width, this.height);
            font.draw();
        };
    }

    /**
     * Updates the button.
     *
     * <p>This implementation is intentionally empty. The button is driven primarily by events
     * (hover/press/release/focus/disabled). Override if you later add animation, timers, or
     * per-frame logic.</p>
     *
     * @param delta time in seconds since last update
     */
    @Override
    public void update(float delta) {

    }

    /**
     * Draws the button background and text using scissored rendering.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If {@link #current} is null, the button renders nothing</li>
     *     <li>Otherwise, applies a scissor region matching the button bounds and renders via {@link #draw}</li>
     * </ul>
     *
     * <p>The draw callback renders the current background first, then the label text.</p>
     */
    @Override
    public void draw() {
        if (current == null)
            return;

        this.getUI().getViewport().applyScissor(this.x, this.y, this.width, this.height, draw);
    }

    /**
     * Handles key press events for button activation feedback.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If no action is configured, the button ignores key press input</li>
     *     <li>If Enter is pressed, switches the current drawable to {@link ButtonStyle#getPressed()}</li>
     * </ul>
     *
     * <p>Note: This method only changes the visual pressed state. The action is invoked on
     * mouse release in {@link #onMouseRelease(MouseReleaseEvent)}. If you want keyboard activation,
     * add {@code action.perform(this)} on Enter release (or press) as desired.</p>
     *
     * @param event key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (action == null)
            return;

        if (event.getKey() == Keyboard.ENTER)
            current = style.getPressed();
    }

    /**
     * Handles key release events by restoring the default background.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>On any key release, sets current drawable to {@link ButtonStyle#getBackground()}</li>
     * </ul>
     *
     * <p>Note: This does not check which key was released. If you want stricter behavior,
     * gate this logic on Enter only.</p>
     *
     * @param event key release event
     */
    @Override
    public void onKeyRelease(KeyReleaseEvent event) {
        current = style.getBackground();
    }

    /**
     * Handles mouse press events by switching the current drawable to the pressed state.
     *
     * @param event mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        current = style.getPressed();
    }

    /**
     * Handles mouse release events by restoring hovered/background state and invoking the action.
     *
     * <p>Visual restore behavior:</p>
     * <ul>
     *     <li>If the pointer is still hovering and hovered drawable exists, use hovered drawable</li>
     *     <li>Otherwise, use the default background drawable</li>
     * </ul>
     *
     * <p>Action behavior:</p>
     * <ul>
     *     <li>If no action is configured, no-op</li>
     *     <li>If an action is configured, invokes {@link UIAction#perform(Element)} with {@code this}</li>
     * </ul>
     *
     * <p>Note: This implementation performs the action regardless of whether the pointer released
     * inside the button. If you want "click-cancel" behavior, gate {@code action.perform(this)}
     * behind {@link #isHovered()}.</p>
     *
     * @param event mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (isHovered() && style.getHovered() != null)
            current = style.getHovered();
        else current = style.getBackground();

        if (action == null)
            return;

        action.perform(this);
    }

    /**
     * Sets the button style used for determining drawables and font data.
     *
     * <p>This method only updates the stored style reference. It does not automatically
     * re-apply drawables or rebuild the font.</p>
     *
     * @param style new style to use
     * @return this button for chaining
     */
    public Button setStyle(ButtonStyle style) {
        this.style = style;
        return this;
    }

    /**
     * Sets the button position and re-aligns the font to match the current {@link #textAlignment}.
     *
     * <p>Alignment is computed using {@link Alignment#align(Dimensional, Sizeable, Alignment)}.</p>
     *
     * @param x new x position
     * @param y new y position
     */
    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);

        Vector2f fontPosition = Alignment.align(this, font, textAlignment);

        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    /**
     * Sets the button size and re-aligns the font to match the current {@link #textAlignment}.
     *
     * <p>Alignment is computed using {@link Alignment#align(Dimensional, Sizeable, Alignment)}.</p>
     *
     * @param width  new width
     * @param height new height
     */
    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);

        Vector2f fontPosition = Alignment.align(this, font, textAlignment);

        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    /**
     * Returns the internal {@link Font} used to render the button label.
     *
     * <p>This allows advanced callers to adjust font properties (size, color, etc.) directly.</p>
     *
     * @return the button font
     */
    public Font getFont() {
        return font;
    }

    /**
     * Updates the button label text.
     *
     * <p>If {@code text} is null, the label becomes an empty string.</p>
     *
     * @param text new label text (null becomes "")
     * @return this button for chaining
     */
    public Button setText(String text) {
        if (text == null) {
            font.setText("");
            return this;
        }

        font.setText(text);
        return this;
    }

    /**
     * Sets the text alignment used to position the label inside the button.
     *
     * <p>Note: This does not immediately reposition the font. The font position is recalculated
     * on the next {@link #setPosition(float, float)} or {@link #setSize(float, float)} call.</p>
     *
     * @param alignment new alignment for the label
     * @return this button for chaining
     */
    public Button setTextAlignment(Alignment alignment) {
        this.textAlignment = alignment;
        return this;
    }

    /**
     * Sets the action invoked when the button is activated.
     *
     * @param action new action (may be null to disable activation)
     * @return this button for chaining
     */
    public Button setAction(UIAction<Button> action) {
        this.action = action;
        return this;
    }

    /**
     * Sets the disabled state and updates the current drawable if the style provides a disabled drawable.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>Calls {@code super.setDisabled(value)} to update base element state</li>
     *     <li>If {@link ButtonStyle#getDisabled()} is null, leaves the current drawable unchanged</li>
     *     <li>Otherwise sets current to disabled when true, background when false</li>
     * </ul>
     *
     * @param value true to disable, false to enable
     */
    @Override
    public void setDisabled(boolean value) {
        super.setDisabled(value);

        if (style.getDisabled() == null)
            return;

        current = value ? style.getDisabled() : style.getBackground();
    }

    /**
     * Updates focus state and swaps the drawable if the style provides a focused drawable.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>Calls {@code super.setFocused(value)} to update base element focus state</li>
     *     <li>If {@link ButtonStyle#getFocused()} is null, leaves the current drawable unchanged</li>
     *     <li>Otherwise sets current to focused when true, background when false</li>
     * </ul>
     *
     * @param value true if focused, false otherwise
     */
    @Override
    protected void setFocused(boolean value) {
        super.setFocused(value);

        if (style.getFocused() == null)
            return;

        current = value ? style.getFocused() : style.getBackground();
    }

    /**
     * Updates hover state and swaps the drawable if the style provides a hovered drawable.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>Calls {@code super.setHovered(value)} to update base element hover state</li>
     *     <li>If {@link ButtonStyle#getHovered()} is null, leaves the current drawable unchanged</li>
     *     <li>If hovered, uses hovered drawable; otherwise uses background drawable</li>
     * </ul>
     *
     * @param value true if hovered, false otherwise
     */
    @Override
    protected void setHovered(boolean value) {
        super.setHovered(value);

        if (style.getHovered() == null)
            return;

        if (value) {
            current = style.getHovered();
        } else {
            current = style.getBackground();
        }
    }
}
