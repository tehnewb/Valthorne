package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.Vector2f;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

public class NanoModal extends NanoPanel {

    public static final StyleKey<Color> DIALOG_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    public static final StyleKey<Color> DIALOG_HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogHoverBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    public static final StyleKey<Color> DIALOG_FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogFocusedBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    public static final StyleKey<Color> DIALOG_PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogPressedBackgroundColor", Color.class, new Color(0xFF1F1F1F));
    public static final StyleKey<Color> DIALOG_DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.modal.dialogDisabledBackgroundColor", Color.class, new Color(0xFF1F1F1F));

    public static final StyleKey<Color> DIALOG_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogBorderColor", Color.class, new Color(0xFF4A4A4A));
    public static final StyleKey<Color> DIALOG_HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogHoverBorderColor", Color.class, new Color(0xFF4A4A4A));
    public static final StyleKey<Color> DIALOG_FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogFocusedBorderColor", Color.class, new Color(0xFF4A4A4A));
    public static final StyleKey<Color> DIALOG_PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogPressedBorderColor", Color.class, new Color(0xFF4A4A4A));
    public static final StyleKey<Color> DIALOG_DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.modal.dialogDisabledBorderColor", Color.class, new Color(0xFF4A4A4A));

    public static final StyleKey<Float> DIALOG_CORNER_RADIUS_KEY = StyleKey.of("nano.modal.dialogCornerRadius", Float.class, 6f);
    public static final StyleKey<Float> DIALOG_BORDER_WIDTH_KEY = StyleKey.of("nano.modal.dialogBorderWidth", Float.class, 1f);

    private final NanoPanel dialog = new NanoPanel();
    private final UINode parentNode;
    private UINode content;

    private boolean closeOnEscape = true;
    private boolean closeOnOutsideClick;

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

    public NanoPanel getDialog() {
        return dialog;
    }

    public UINode getModalParent() {
        return parentNode;
    }

    public UINode getContent() {
        return content;
    }

    public NanoModal content(UINode content) {
        if (this.content != null) dialog.remove(this.content);

        this.content = content;

        if (content != null) dialog.add(content);

        markLayoutDirty();
        return this;
    }

    public boolean isCloseOnEscape() {
        return closeOnEscape;
    }

    public NanoModal closeOnEscape(boolean closeOnEscape) {
        this.closeOnEscape = closeOnEscape;
        return this;
    }

    public boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    public NanoModal closeOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
        return this;
    }

    public boolean isOpen() {
        return isVisible();
    }

    public NanoModal open() {
        UIRoot root = parentNode.getRoot();
        if (root != null) {
            root.showOverlay(this);
            root.setFocusTo(this);
        } else {
            setVisible(true);
        }

        return this;
    }

    public NanoModal close() {
        UIRoot root = getRoot();
        if (root != null) {
            if (root.getFocused() == this) root.setFocusTo(null);

            root.hideOverlay(this);
        } else {
            setVisible(false);
        }

        return this;
    }

    public NanoModal toggle() {
        if (isOpen()) close();
        else open();

        return this;
    }

    @Override
    public UINode findNodeAt(float x, float y, int requiredBit) {
        if (!isVisible() || isDisabled()) return null;

        if (!contains(x, y)) return null;

        UINode hit = super.findNodeAt(x, y, requiredBit);
        if (hit != null) return hit;

        return requiredBit < 0 || getBit(requiredBit) ? this : null;
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        if (!closeOnOutsideClick || content == null) return;

        Vector2f world = screenToWorld(event.getX(), event.getY());
        float x = world.getX();
        float y = world.getY();

        if (!dialog.contains(x, y)) close();
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (closeOnEscape && event.getKey() == Keyboard.ESCAPE) close();
    }

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

    @Override
    public void update(float delta) {
        super.update(delta);
    }

    @Override
    public void draw(TextureBatch batch) {
    }
}