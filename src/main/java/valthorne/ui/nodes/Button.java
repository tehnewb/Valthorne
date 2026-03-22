package valthorne.ui.nodes;

import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.NodeAction;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * <p>
 * {@code Button} is a clickable UI node built on top of {@link Panel} that displays
 * a centered {@link Label} and optionally performs an action when pressed.
 * </p>
 *
 * <p>
 * This class is intended to be a simple, reusable button control for Valthorne's UI
 * system. It combines:
 * </p>
 *
 * <ul>
 *     <li>a background drawable resolved from style data</li>
 *     <li>a child label used to display text</li>
 *     <li>clickable and focusable interaction behavior</li>
 *     <li>an optional {@link NodeAction} callback executed on press</li>
 * </ul>
 *
 * <p>
 * The button automatically configures itself as clickable and focusable and centers
 * its internal label using its layout configuration. The visual background is not
 * hardcoded in the class itself. Instead, it is resolved from the button's style
 * using {@link #BACKGROUND_KEY}, allowing themes and skins to define how buttons
 * should look without changing button logic.
 * </p>
 *
 * <p>
 * Text content is managed through the embedded {@link Label} instance. This allows
 * the button to inherit all label-related rendering behavior while keeping the API
 * convenient through methods like {@link #text(String)} and {@link #getText()}.
 * </p>
 *
 * <p>
 * Interaction behavior is simple: when the button receives a mouse press event,
 * it executes its assigned action if one exists.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Button button = new Button("Play");
 *
 * button.getLayout()
 *       .width(180)
 *       .height(48);
 *
 * button.action(b -> {
 *     System.out.println("Clicked: " + b.getText());
 * });
 *
 * String text = button.getText();
 * Label label = button.getLabel();
 * NodeAction<Button> action = button.getAction();
 *
 * button.update(delta);
 * button.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete usage of the class: construction,
 * text assignment, layout sizing, action binding, label access, update, and draw.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Button extends Panel {

    /**
     * Style key used to resolve the background drawable for the button.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = StyleKey.of("background", Drawable.class);

    private final Label label = new Label(); // Embedded label used to display the button text
    private NodeAction<Button> action; // Action executed when the button is pressed

    /**
     * <p>
     * Creates a new button with no initial text.
     * </p>
     *
     * <p>
     * The button is configured as clickable and focusable, its layout is set to center
     * child content both along the item axis and the justification axis, and the
     * embedded label is added as a child node with no grow or shrink behavior.
     * </p>
     */
    public Button() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);

        getLayout()
                .itemsCenter()
                .justifyCenter();

        label.getLayout()
                .noGrow()
                .noShrink();

        label.setClickable(false);

        super.add(label);
    }

    /**
     * <p>
     * Creates a new button with the given initial text.
     * </p>
     *
     * <p>
     * This constructor delegates to the default constructor and then assigns the
     * provided text to the embedded label.
     * </p>
     *
     * @param text the initial button text
     */
    public Button(String text) {
        this();
        text(text);
    }

    /**
     * <p>
     * Returns the embedded {@link Label} used by this button.
     * </p>
     *
     * @return the internal label
     */
    public Label getLabel() {
        return label;
    }

    /**
     * <p>
     * Returns the button text currently displayed by the embedded label.
     * </p>
     *
     * @return the current button text
     */
    public String getText() {
        return label.getText();
    }

    /**
     * <p>
     * Sets the text shown by the embedded label.
     * </p>
     *
     * <p>
     * This method delegates directly to {@link Label#text(String)} and returns this
     * button for fluent configuration.
     * </p>
     *
     * @param text the new button text
     * @return this button
     */
    public Button text(String text) {
        label.text(text);
        return this;
    }

    /**
     * <p>
     * Sets the action performed when this button is pressed.
     * </p>
     *
     * @param action the action to execute on press
     * @return this button
     */
    public Button action(NodeAction<Button> action) {
        this.action = action;
        return this;
    }

    /**
     * <p>
     * Returns the currently assigned button action.
     * </p>
     *
     * @return the assigned action, or {@code null} if none exists
     */
    public NodeAction<Button> getAction() {
        return action;
    }

    /**
     * <p>
     * Updates this button.
     * </p>
     *
     * <p>
     * This implementation currently performs no per-frame logic, but the method exists
     * to fulfill the UI node lifecycle contract and to provide a future extension point.
     * </p>
     *
     * @param delta the frame delta time
     */
    @Override
    public void update(float delta) {
    }

    /**
     * <p>
     * Handles a mouse press event for this button.
     * </p>
     *
     * <p>
     * If an action has been assigned, it is executed immediately with this button as
     * the action target.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (action != null)
            action.perform(this);
    }

    /**
     * <p>
     * Draws the button using the provided {@link TextureBatch}.
     * </p>
     *
     * <p>
     * The method first resolves the button style and, if available, draws the
     * background drawable using the current render bounds. It then draws the
     * embedded label on top.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        ResolvedStyle style = getStyle();
        if (style != null) {
            Drawable background = style.get(BACKGROUND_KEY);

            if (background != null)
                background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());
        }

        label.draw(batch);
    }
}