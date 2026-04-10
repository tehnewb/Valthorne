package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * <p>
 * {@code Modal} is a top-level overlay dialog container built on top of
 * {@link Panel}. It is designed to display a centered dialog panel above the
 * normal UI while optionally blocking or intercepting interaction outside the
 * dialog area.
 * </p>
 *
 * <p>
 * This class provides modal-style behavior by rendering itself in the UI root's
 * overlay layer and by managing an internal {@link Panel} called {@code dialog}
 * that holds the actual modal content. The modal itself fills the available
 * render space, while the dialog panel is centered inside it.
 * </p>
 *
 * <p>
 * The modal supports:
 * </p>
 *
 * <ul>
 *     <li>a full-screen or full-root modal backdrop</li>
 *     <li>a dedicated dialog container for child content</li>
 *     <li>optional closing with Escape</li>
 *     <li>optional closing when clicking outside the dialog</li>
 *     <li>theme-resolved backdrop and dialog background drawables</li>
 *     <li>focus transfer when opened</li>
 *     <li>overlay-layer integration when attached to a {@link UIRoot}</li>
 * </ul>
 *
 * <p>
 * Visual appearance is theme-driven:
 * </p>
 *
 * <ul>
 *     <li>{@link #BACKGROUND_KEY} resolves the modal backdrop drawable</li>
 *     <li>{@link #DIALOG_BACKGROUND_KEY} resolves the dialog panel background drawable</li>
 * </ul>
 *
 * <p>
 * When the modal is open and attached to a root, it is shown through the root's
 * overlay mechanism. This ensures it renders above the normal UI tree. The modal
 * may also be used without a root, in which case opening and closing only affect
 * its visibility flag.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Modal modal = new Modal(parentNode);
 *
 * Label message = new Label("Are you sure?");
 * modal.content(message)
 *      .closeOnEscape(true)
 *      .closeOnOutsideClick(true);
 *
 * Panel dialog = modal.getDialog();
 * UINode content = modal.getContent();
 * UINode owner = modal.getModalParent();
 *
 * modal.open();
 *
 * boolean open = modal.isOpen();
 *
 * modal.toggle();
 * modal.close();
 *
 * modal.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the full usage of the class: creation, content
 * assignment, behavior flags, dialog access, modal lifecycle, and drawing.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Modal extends Panel {

    /**
     * Style key used to resolve the full modal backdrop drawable.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = StyleKey.of("background", Drawable.class);

    /**
     * Style key used to resolve the dialog panel background drawable.
     */
    public static final StyleKey<Drawable> DIALOG_BACKGROUND_KEY = StyleKey.of("dialogBackground", Drawable.class);

    private final Panel dialog = new Panel(); // Inner dialog panel centered inside the modal
    private final UINode parentNode; // Node whose root and UI context own this modal
    private UINode content; // Content currently attached inside the dialog panel

    private boolean closeOnEscape = true; // Whether pressing Escape should close the modal
    private boolean closeOnOutsideClick; // Whether clicking outside the dialog should close the modal

    private Drawable background; // Resolved backdrop drawable for the modal
    private Drawable dialogBackground; // Resolved background drawable for the dialog panel

    /**
     * <p>
     * Creates a new modal attached to the given parent node.
     * </p>
     *
     * <p>
     * The supplied parent node is required so the modal can locate its root and
     * participate in overlay-based behavior when opened. The modal is configured to
     * be clickable, focusable, and scrollable, and it is laid out to fill its
     * available space while centering the inner dialog panel.
     * </p>
     *
     * <p>
     * The dialog panel is configured not to grow or shrink and is added as the sole
     * child of the modal. The modal starts hidden.
     * </p>
     *
     * @param parentNode the node whose UI context owns this modal
     * @throws NullPointerException if {@code parentNode} is {@code null}
     */
    public Modal(UINode parentNode) {
        if (parentNode == null)
            throw new NullPointerException("parentNode");

        this.parentNode = parentNode;

        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        setBit(SCROLLABLE_BIT, true);

        getLayout()
                .fill()
                .grow()
                .justifyCenter()
                .itemsCenter();

        dialog.getLayout()
                .noGrow()
                .noShrink()
                .justifyCenter()
                .itemsCenter();

        super.add(dialog);
        setVisible(false);
    }

    /**
     * <p>
     * Returns the inner dialog panel used to host modal content.
     * </p>
     *
     * @return the dialog panel
     */
    public Panel getDialog() {
        return dialog;
    }

    /**
     * <p>
     * Returns the parent node associated with this modal.
     * </p>
     *
     * @return the owning parent node
     */
    public UINode getModalParent() {
        return parentNode;
    }

    /**
     * <p>
     * Returns the content currently attached to the dialog panel.
     * </p>
     *
     * @return the current dialog content, or {@code null} if none exists
     */
    public UINode getContent() {
        return content;
    }

    /**
     * <p>
     * Sets the content displayed inside the dialog panel.
     * </p>
     *
     * <p>
     * If existing content is present, it is removed first. The new content is then
     * added to the dialog panel if non-null. Layout is marked dirty afterward so the
     * modal can be relaid out to reflect the new content.
     * </p>
     *
     * @param content the new dialog content
     * @return this modal
     */
    public Modal content(UINode content) {
        if (this.content != null)
            dialog.remove(this.content);

        this.content = content;

        if (content != null)
            dialog.add(content);

        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns whether this modal closes when Escape is pressed.
     * </p>
     *
     * @return {@code true} if Escape closes the modal
     */
    public boolean isCloseOnEscape() {
        return closeOnEscape;
    }

    /**
     * <p>
     * Sets whether this modal should close when Escape is pressed.
     * </p>
     *
     * @param closeOnEscape whether Escape closes the modal
     * @return this modal
     */
    public Modal closeOnEscape(boolean closeOnEscape) {
        this.closeOnEscape = closeOnEscape;
        return this;
    }

    /**
     * <p>
     * Returns whether this modal closes when the user clicks outside the dialog.
     * </p>
     *
     * @return {@code true} if outside clicks close the modal
     */
    public boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    /**
     * <p>
     * Sets whether this modal should close when the user clicks outside the dialog.
     * </p>
     *
     * @param closeOnOutsideClick whether outside clicks close the modal
     * @return this modal
     */
    public Modal closeOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
        return this;
    }

    /**
     * <p>
     * Returns whether this modal is currently open.
     * </p>
     *
     * <p>
     * The modal is considered open whenever it is visible.
     * </p>
     *
     * @return {@code true} if the modal is open
     */
    public boolean isOpen() {
        return isVisible();
    }

    /**
     * <p>
     * Opens this modal.
     * </p>
     *
     * <p>
     * If the owning parent has a {@link UIRoot}, the modal is shown through the
     * root overlay layer and focus is transferred to the modal itself. Otherwise the
     * modal is simply made visible.
     * </p>
     *
     * @return this modal
     */
    public Modal open() {
        UIRoot root = parentNode.getRoot();
        if (root != null) {
            root.showOverlay(this);
            root.setFocusTo(this);
        } else {
            setVisible(true);
        }

        return this;
    }

    /**
     * <p>
     * Closes this modal.
     * </p>
     *
     * <p>
     * If the modal is attached to a {@link UIRoot}, it is removed from the overlay
     * layer and focus is cleared if the modal currently owns it. Otherwise the modal
     * is simply made invisible.
     * </p>
     *
     * @return this modal
     */
    public Modal close() {
        UIRoot root = getRoot();
        if (root != null) {
            if (root.getFocused() == this)
                root.setFocusTo(null);

            root.hideOverlay(this);
        } else {
            setVisible(false);
        }

        return this;
    }

    /**
     * <p>
     * Toggles the modal open state.
     * </p>
     *
     * <p>
     * If the modal is open, it is closed. Otherwise it is opened.
     * </p>
     *
     * @return this modal
     */
    public Modal toggle() {
        if (isOpen())
            close();
        else
            open();

        return this;
    }

    /**
     * <p>
     * Finds the top-most matching node inside this modal at the given coordinates.
     * </p>
     *
     * <p>
     * If the modal is not visible or is disabled, the search immediately returns
     * {@code null}. If the coordinates lie outside the modal bounds, the search also
     * returns {@code null}. Otherwise the method delegates to the superclass search.
     * If no child matches, the modal itself is returned when the required bit is
     * satisfied, allowing the backdrop to participate in input handling.
     * </p>
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param requiredBit the required interaction bit
     * @return the matched node, or {@code null} if none is found
     */
    @Override
    public UINode findNodeAt(float x, float y, int requiredBit) {
        if (!isVisible() || isDisabled())
            return null;

        if (!contains(x, y))
            return null;

        UINode hit = super.findNodeAt(x, y, requiredBit);
        if (hit != null)
            return hit;

        return requiredBit < 0 || getBit(requiredBit) ? this : null;
    }

    /**
     * <p>
     * Handles mouse press events for this modal.
     * </p>
     *
     * <p>
     * If closing on outside click is disabled or no content is currently attached,
     * nothing happens. Otherwise the event position is optionally converted through
     * the root viewport when one exists, and the modal closes if the click occurred
     * outside the dialog panel bounds.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (!closeOnOutsideClick || content == null)
            return;

        float x = event.getX();
        float y = event.getY();

        if (getRoot() != null && getRoot().getViewport() != null) {
            var world = getRoot().getViewport().screenToWorld(x, y);
            if (world != null) {
                x = world.getX();
                y = world.getY();
            }
        }

        if (!dialog.contains(x, y))
            close();
    }

    /**
     * <p>
     * Handles key press events for this modal.
     * </p>
     *
     * <p>
     * If closing on Escape is enabled and the pressed key is
     * {@link Keyboard#ESCAPE}, the modal is closed.
     * </p>
     *
     * @param event the key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (closeOnEscape && event.getKey() == Keyboard.ESCAPE)
            close();
    }

    /**
     * <p>
     * Applies style-driven layout and drawable resolution for this modal.
     * </p>
     *
     * <p>
     * The current style is resolved and used to update the modal backdrop drawable
     * and the dialog background drawable. If no style exists, both drawables are
     * cleared. After resolving visuals, the superclass layout application continues.
     * </p>
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            background = style.get(BACKGROUND_KEY);
            dialogBackground = style.get(DIALOG_BACKGROUND_KEY);
        } else {
            background = null;
            dialogBackground = null;
        }

        super.applyLayout();
    }

    /**
     * <p>
     * Draws this modal using the provided {@link TextureBatch}.
     * </p>
     *
     * <p>
     * If the modal is not visible, rendering is skipped. Otherwise the modal
     * backdrop is drawn first, then the dialog background, and finally the dialog
     * panel itself and its contents.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (!isVisible())
            return;

        if (background != null)
            background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());

        if (dialogBackground != null)
            dialogBackground.draw(batch, dialog.getRenderX(), dialog.getRenderY(), dialog.getWidth(), dialog.getHeight());

        dialog.draw(batch);
    }
}