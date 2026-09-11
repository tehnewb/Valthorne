package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import org.joml.Vector2f;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * NanoVG modal overlay containing a centered dialog panel and optional content.
 * The constructor's parent locates the root when opening; root registration owns
 * focus scoping and pointer cancellation. Without a parent root, open only changes
 * visibility and does not establish input isolation. Content may use either renderer.
 *
 * <p>Escape dismissal is enabled by default; outside-click dismissal is opt-in
 * and requires non-null content. Dialog style keys customize the inner panel,
 * while inherited NanoPanel settings paint the surrounding overlay.</p>
 *
 * <pre>{@code
 * NanoModal modal = new NanoModal(parentNode);
 * modal.getDialog().getLayout().width(360).height(200).padding(16);
 * modal.content(new NanoLabel("Settings"));
 * modal.closeOnOutsideClick(true).open();
 * // Dismiss through the modal API to unwind the root's focus scope:
 * modal.close();
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class NanoModal extends NanoPanel {

    /**
     * Theme background color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    /**
     * Theme hover background color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogHoverBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    /**
     * Theme focused background color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogFocusedBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    /**
     * Theme pressed background color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogPressedBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    /**
     * Theme disabled background color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogDisabledBackgroundColor", Color.class, new Color(0xFF1F1F1F));

    /**
     * Theme border color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogBorderColor", Color.class, new Color(0xFF4A4A4A));
    /**
     * Theme hover border color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogHoverBorderColor", Color.class, new Color(0xFF4A4A4A));
    /**
     * Theme focused border color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogFocusedBorderColor", Color.class, new Color(0xFF4A4A4A));
    /**
     * Theme pressed border color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogPressedBorderColor", Color.class, new Color(0xFF4A4A4A));
    /**
     * Theme disabled border color for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Color> DIALOG_DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogDisabledBorderColor", Color.class, new Color(0xFF4A4A4A));

    /**
     * Theme corner radius for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Float> DIALOG_CORNER_RADIUS_KEY = StyleKey.of("nano.modal.dialogCornerRadius", Float.class, 6f);
    /**
     * Theme border width for the inner dialog, separate from overlay painting.
     */
    public static final StyleKey<Float> DIALOG_BORDER_WIDTH_KEY = StyleKey.of("nano.modal.dialogBorderWidth", Float.class, 1f);

    private final NanoPanel dialog = new NanoPanel(); // Owned centered panel containing managed modal content.
    private final UINode parentNode; // Borrowed node used to find the root when opening.
    private UINode content; // Managed content reference, null for an empty dialog.

    private boolean closeOnEscape = true; // Whether Escape closes and consumes its routed event.
    private boolean closeOnOutsideClick; // Whether outside-dialog presses close when content exists.

    /**
     * Retains a parent for root lookup, creates a centered dialog, and starts hidden.
     * The overlay is clickable, focusable, and scrollable to participate in modal
     * input routing. It is not attached to the parent's ordinary children here.
     *
     * @param parentNode non-null node whose root will own the opened overlay
     * @throws NullPointerException if parentNode is null
     */
    public NanoModal(UINode parentNode) {
        if (parentNode == null) throw new NullPointerException("parentNode");

        this.parentNode = parentNode;

        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        setBit(SCROLLABLE_BIT, true);

        getLayout().fill().grow().justifyCenter().itemsCenter();

        dialog.getLayout().noGrow().noShrink().justifyCenter().itemsCenter();

        super.add(dialog);
        setVisible(false);
    }

    /**
     * Exposes the live inner panel for size, padding, and appearance configuration.
     * Use content to replace the managed content node; arbitrary children are separate.
     *
     * @return owned dialog panel
     */
    public NanoPanel getDialog() {
        return dialog;
    }

    /**
     * Returns the constructor-supplied root-lookup node, which need not be the
     * modal's current structural parent in the overlay hierarchy.
     *
     * @return borrowed modal parent reference
     */
    public UINode getModalParent() {
        return parentNode;
    }

    /**
     * Reads the content reference last assigned through content.
     *
     * @return managed content, or null
     */
    public UINode getContent() {
        return content;
    }

    /**
     * Removes the old managed content, stores the replacement, and adds it to the
     * dialog when non-null. Marks layout dirty. Removal precedes insertion, so an
     * insertion failure is not a transactional rollback to the old content.
     *
     * @param content replacement node accepted by the dialog's child-ownership policy,
     * or null to clear
     * @return this modal
     */
    public NanoModal content(UINode content) {
        if (this.content != null) dialog.remove(this.content);

        this.content = content;

        if (content != null) dialog.add(content);

        markLayoutDirty();
        return this;
    }

    /**
     * Reads whether an Escape press is handled as modal dismissal.
     *
     * @return configured Escape-dismissal flag
     */
    public boolean isCloseOnEscape() {
        return closeOnEscape;
    }

    /**
     * Changes Escape handling without opening or closing the overlay.
     *
     * @param closeOnEscape true to dismiss and consume Escape presses
     * @return this modal
     */
    public NanoModal closeOnEscape(boolean closeOnEscape) {
        this.closeOnEscape = closeOnEscape;
        return this;
    }

    /**
     * Reads the requested outside-click policy. It only takes effect when content
     * is non-null and a pointer press is delivered to this handler.
     *
     * @return outside-click dismissal flag
     */
    public boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    /**
     * Changes outside-dialog press handling without altering current visibility.
     * The handler does not restrict dismissal to a particular pointer button.
     *
     * @param closeOnOutsideClick true to enable outside-press dismissal
     * @return this modal
     */
    public NanoModal closeOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
        return this;
    }

    /**
     * Reports visibility, which does not by itself guarantee root modal registration.
     * Detached open calls can make the overlay visible without a focus scope.
     *
     * @return current visible state
     */
    public boolean isOpen() {
        return isVisible();
    }

    /**
     * Shows through the parent's root when available, letting it establish a modal
     * focus scope and cancel prior pointer state. If no root exists, only makes
     * this node visible. Repeated root registration is handled by the root.
     *
     * @return this modal
     */
    public NanoModal open() {
        UIRoot root = parentNode.getRoot();
        if (root != null) {
            root.showModal(this);
        } else {
            setVisible(true);
        }

        return this;
    }

    /**
     * Asks the current root to hide the modal and unwind overlay/focus state.
     * Without a root, only clears visibility. Content remains available for reopening.
     *
     * @return this modal
     */
    public NanoModal close() {
        UIRoot root = getRoot();
        if (root != null) {
            root.hideModal(this);
        } else {
            setVisible(false);
        }

        return this;
    }

    /**
     * Chooses close or open from current visibility. Uses the same root lookup and
     * focus-scope behavior as those operations.
     *
     * @return this modal
     */
    public NanoModal toggle() {
        if (isOpen()) close();
        else open();

        return this;
    }

    /**
     * Rejects invisible, disabled, or out-of-bounds queries, then searches descendants.
     * Falls back to the overlay itself when it satisfies the requested capability,
     * preventing an otherwise empty modal surface from passing that hit through.
     *
     * @param x hit-test X in UI world coordinates
     * @param y hit-test Y in UI world coordinates
     * @param requiredBit node capability bit, or negative to accept any
     * @return matching modal descendant/overlay, or null
     */
    @Override
    public UINode findNodeAt(float x, float y, int requiredBit) {
        if (!isVisible() || isDisabled()) return null;

        if (!contains(x, y)) return null;

        UINode hit = super.findNodeAt(x, y, requiredBit);
        if (hit != null) return hit;

        return requiredBit < 0 || getBit(requiredBit) ? this : null;
    }

    /**
     * When enabled and content exists, converts screen coordinates to UI world space
     * and closes on a press outside the dialog bounds. Does not consume the event
     * or restrict the mouse button; root modal routing supplies input isolation.
     *
     * @param event routed pointer press
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (!closeOnOutsideClick || content == null) return;

        Vector2f world = screenToWorld(event.getX(), event.getY());
        float x = world.x();
        float y = world.y();

        if (!dialog.contains(x, y)) close();
    }

    /**
     * Closes and consumes Escape when dismissal is enabled. Other keys are left
     * untouched; root routing determines whether this handler receives the event.
     *
     * @param event routed key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (closeOnEscape && event.getKey() == Keyboard.ESCAPE) {
            close();
            event.consume();
        }
    }

    /**
     * Copies non-null modal-specific theme colors and dimensions into the inner
     * dialog, then applies inherited overlay panel styling and layout. The dialog's
     * own resolved styles can also participate in its later layout pass.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Color dialogBackgroundColor = style.get(DIALOG_BACKGROUND_COLOR_KEY);
            Color dialogHoverBackgroundColor = style.get(DIALOG_HOVER_BACKGROUND_COLOR_KEY);
            Color dialogFocusedBackgroundColor = style.get(DIALOG_FOCUSED_BACKGROUND_COLOR_KEY);
            Color dialogPressedBackgroundColor = style.get(DIALOG_PRESSED_BACKGROUND_COLOR_KEY);
            Color dialogDisabledBackgroundColor = style.get(DIALOG_DISABLED_BACKGROUND_COLOR_KEY);

            Color dialogBorderColor = style.get(DIALOG_BORDER_COLOR_KEY);
            Color dialogHoverBorderColor = style.get(DIALOG_HOVER_BORDER_COLOR_KEY);
            Color dialogFocusedBorderColor = style.get(DIALOG_FOCUSED_BORDER_COLOR_KEY);
            Color dialogPressedBorderColor = style.get(DIALOG_PRESSED_BORDER_COLOR_KEY);
            Color dialogDisabledBorderColor = style.get(DIALOG_DISABLED_BORDER_COLOR_KEY);

            Float dialogCornerRadius = style.get(DIALOG_CORNER_RADIUS_KEY);
            Float dialogBorderWidth = style.get(DIALOG_BORDER_WIDTH_KEY);

            if (dialogBackgroundColor != null) dialog.backgroundColor(dialogBackgroundColor);
            if (dialogHoverBackgroundColor != null) dialog.hoverBackgroundColor(dialogHoverBackgroundColor);
            if (dialogFocusedBackgroundColor != null) dialog.focusedBackgroundColor(dialogFocusedBackgroundColor);
            if (dialogPressedBackgroundColor != null) dialog.pressedBackgroundColor(dialogPressedBackgroundColor);
            if (dialogDisabledBackgroundColor != null) dialog.disabledBackgroundColor(dialogDisabledBackgroundColor);

            if (dialogBorderColor != null) dialog.borderColor(dialogBorderColor);
            if (dialogHoverBorderColor != null) dialog.hoverBorderColor(dialogHoverBorderColor);
            if (dialogFocusedBorderColor != null) dialog.focusedBorderColor(dialogFocusedBorderColor);
            if (dialogPressedBorderColor != null) dialog.pressedBorderColor(dialogPressedBorderColor);
            if (dialogDisabledBorderColor != null) dialog.disabledBorderColor(dialogDisabledBorderColor);

            if (dialogCornerRadius != null) dialog.cornerRadius(dialogCornerRadius);
            if (dialogBorderWidth != null) dialog.borderWidth(dialogBorderWidth);
        }

        super.applyLayout();
    }

    /**
     * Delegates to normal panel/container update traversal for the dialog subtree.
     * Visibility eligibility is supplied by the surrounding UI lifecycle.
     *
     * @param delta elapsed update seconds
     */
    @Override
    public void update(float delta) {
        super.update(delta);
    }

    /**
     * Routes the overlay through shared UI rendering so inherited NanoVG decoration
     * and mixed-renderer dialog children receive prepared backend state.
     *
     * @param batch active texture batch used by UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }
}
