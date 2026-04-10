package valthorne.ui.nodes;

import org.lwjgl.BufferUtils;
import valthorne.Keyboard;
import valthorne.event.events.*;
import valthorne.graphics.Color;
import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.NodeAction;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.viewport.Viewport;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.ByteBuffer;

/**
 * <p>
 * {@code TextField} is an interactive single-line text input control built on top of
 * {@link Panel}. It supports typing, caret movement, text selection, clipboard
 * operations, placeholder text, optional masking, horizontal scrolling, blinking
 * caret rendering, and style-driven visuals.
 * </p>
 *
 * <p>
 * This class is designed to be the primary editable text input component in the
 * Valthorne UI system. It integrates with keyboard input, mouse interaction,
 * viewport-aware coordinate conversion, and theme resolution. The control manages
 * both the raw logical text and a derived display string used when masking is enabled.
 * </p>
 *
 * <p>
 * The text field supports the following editing behaviors:
 * </p>
 *
 * <ul>
 *     <li>typing printable characters</li>
 *     <li>caret movement with arrow keys, Home, End, and Ctrl+word navigation</li>
 *     <li>selection through mouse drag and Shift+keyboard movement</li>
 *     <li>double-click select-all behavior</li>
 *     <li>Backspace and Delete removal</li>
 *     <li>clipboard copy, cut, and paste with Ctrl+C, Ctrl+X, and Ctrl+V</li>
 *     <li>Ctrl+A select all</li>
 *     <li>Enter-triggered action callbacks</li>
 * </ul>
 *
 * <p>
 * Rendering is style-driven and can resolve:
 * </p>
 *
 * <ul>
 *     <li>background drawables for normal, hovered, and focused states</li>
 *     <li>a font for text rendering</li>
 *     <li>colors for text, placeholder text, caret, and selection</li>
 *     <li>padding and caret dimensions</li>
 *     <li>selection fallback color</li>
 *     <li>an optional action callback from style data</li>
 * </ul>
 *
 * <p>
 * The text field keeps track of:
 * </p>
 *
 * <ul>
 *     <li>the raw input text</li>
 *     <li>the placeholder text</li>
 *     <li>the current caret position</li>
 *     <li>selection range</li>
 *     <li>whether masking is enabled</li>
 *     <li>the visual horizontal text scroll offset</li>
 *     <li>caret blink state</li>
 *     <li>double-click timing state</li>
 * </ul>
 *
 * <p>
 * Because the control is single-line, pasted and assigned text is sanitized so line
 * breaks, tabs, and other control characters are converted into spaces rather than
 * being inserted directly into the field.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * TextField field = new TextField("Enter your name");
 *
 * field.getLayout()
 *      .width(240)
 *      .height(36);
 *
 * field.text("Albert")
 *      .caretIndex(3)
 *      .masking(false)
 *      .action(textField -> {
 *          System.out.println("Submitted: " + textField.getText());
 *      });
 *
 * String value = field.getText();
 * String placeholder = field.getPlaceholder();
 * int caret = field.getCaretIndex();
 * boolean masked = field.isMasking();
 * char mask = field.getMaskChar();
 * NodeAction<TextField> action = field.getAction();
 *
 * field.update(delta);
 * field.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete intended use of the class: construction,
 * sizing, text assignment, placeholder usage, caret positioning, masking,
 * action binding, querying state, updating, and drawing.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class TextField extends Panel {

    /**
     * Style key used to resolve the normal background drawable.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = StyleKey.of("background", Drawable.class);

    /**
     * Style key used to resolve the hovered background drawable.
     */
    public static final StyleKey<Drawable> HOVER_BACKGROUND_KEY = StyleKey.of("hoverBackground", Drawable.class);

    /**
     * Style key used to resolve the focused background drawable.
     */
    public static final StyleKey<Drawable> FOCUSED_BACKGROUND_KEY = StyleKey.of("focusedBackground", Drawable.class);

    /**
     * Style key used to resolve the text field font.
     */
    public static final StyleKey<Font> FONT_KEY = StyleKey.of("font", Font.class);

    /**
     * Style key used to resolve the normal text color.
     */
    public static final StyleKey<Color> COLOR_KEY = StyleKey.of("color", Color.class);

    /**
     * Style key used to resolve the placeholder text color.
     */
    public static final StyleKey<Color> PLACEHOLDER_COLOR_KEY = StyleKey.of("placeholderColor", Color.class);

    /**
     * Style key used to resolve the caret color.
     */
    public static final StyleKey<Color> CARET_COLOR_KEY = StyleKey.of("caretColor", Color.class);

    /**
     * Style key used to resolve the selection highlight color.
     */
    public static final StyleKey<Color> SELECTION_COLOR_KEY = StyleKey.of("selectionColor", Color.class);

    /**
     * Style key used to resolve horizontal text padding.
     */
    public static final StyleKey<Float> PADDING_KEY = StyleKey.of("padding", Float.class, 10f);

    /**
     * Style key used to resolve caret width.
     */
    public static final StyleKey<Float> CARET_WIDTH_KEY = StyleKey.of("caretWidth", Float.class, 2f);

    /**
     * Style key used to resolve vertical caret padding.
     */
    public static final StyleKey<Float> CARET_PADDING_Y_KEY = StyleKey.of("caretPaddingY", Float.class, 8f);

    /**
     * Style key used to resolve a small scissor expansion value.
     */
    public static final StyleKey<Float> SCISSOR_FUDGE_KEY = StyleKey.of("scissorFudge", Float.class, 4f);

    /**
     * Style key used to resolve a fallback action callback.
     */
    public static final StyleKey<NodeAction<TextField>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<TextField>>) (Class<?>) NodeAction.class);

    /**
     * Style key used to resolve the default selection color when no explicit selection color exists.
     */
    public static final StyleKey<Color> DEFAULT_SELECTION_COLOR_KEY = StyleKey.of("defaultSelectionColor", Color.class, new Color(1f, 1f, 1f, 0.35f));

    /**
     * Shared 1x1 white texture used for drawing caret and selection rectangles.
     */
    private static Texture CARET_PIXEL;

    private Color defaultSelectionColor = new Color(1f, 1f, 1f, 0.35f); // Fallback selection color when style does not provide one

    private String text = ""; // Raw logical text stored by the field
    private String placeholder = ""; // Placeholder text shown when the field is empty
    private String displayText = ""; // Cached masked display text when masking is enabled
    private boolean displayTextDirty = true; // Whether the cached display text must be rebuilt

    private int caretIndex; // Current caret insertion index
    private int selectionStart; // Selection anchor/start index
    private int selectionEnd; // Selection active/end index
    private boolean selecting; // Whether mouse drag selection is currently active

    private boolean masking; // Whether logical text should be visually masked
    private char maskChar = '*'; // Character used when masking is enabled

    private boolean caretVisible = true; // Whether the blinking caret is currently visible
    private float blinkTime; // Accumulated blink timer
    private float blinkInterval = 0.5f; // Time interval between caret visibility toggles

    private float padding = 10f; // Horizontal text padding inside the field
    private float caretWidth = 2f; // Width of the rendered caret
    private float caretPadY = 8f; // Vertical inset applied to caret and selection rendering
    private float scissorFudge = 4f; // Small expansion amount applied to the scissor box
    private float textOffsetX; // Horizontal scroll offset used when text exceeds available width

    private float doubleClickTimer; // Timer used for double-click detection
    private float doubleClickWindow = 0.25f; // Maximum time window for recognizing a double-click
    private boolean pendingClick; // Whether a click is waiting to see if it becomes a double-click

    private Drawable background; // Resolved normal background drawable
    private Drawable hoverBackground; // Resolved hovered background drawable
    private Drawable focusedBackground; // Resolved focused background drawable
    private Font font; // Resolved font used for rendering text
    private Color textColor; // Resolved text color
    private Color placeholderColor; // Resolved placeholder text color
    private Color caretColor; // Resolved caret color
    private Color selectionColor; // Resolved selection highlight color

    private NodeAction<TextField> action; // Explicit action performed when Enter is pressed

    /**
     * <p>
     * Creates a new empty text field.
     * </p>
     *
     * <p>
     * The field is configured as clickable and focusable and immediately marks
     * its layout dirty so it can size itself once styled.
     * </p>
     */
    public TextField() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        this.markLayoutDirty();
    }

    /**
     * <p>
     * Creates a new text field with the provided placeholder text.
     * </p>
     *
     * @param placeholder the placeholder text to display when the field is empty
     */
    public TextField(String placeholder) {
        this();
        placeholder(placeholder);
    }

    /**
     * <p>
     * Returns the raw logical text stored by this field.
     * </p>
     *
     * @return the current text
     */
    public String getText() {
        return text;
    }

    /**
     * <p>
     * Sets the raw logical text stored by this field.
     * </p>
     *
     * <p>
     * The supplied text is sanitized so newlines, tabs, carriage returns, and other
     * ISO control characters are converted into spaces. Consecutive control-derived
     * spaces are collapsed so multiple adjacent control characters do not generate
     * repeated spaces unnecessarily.
     * </p>
     *
     * <p>
     * After assignment, the caret is clamped into the valid range, selection is
     * cleared, display text is marked dirty, layout is marked dirty, and horizontal
     * scrolling is updated.
     * </p>
     *
     * @param text the new logical text
     * @return this text field
     */
    public TextField text(String text) {
        if (text == null) {
            this.text = "";
        } else {
            StringBuilder builder = new StringBuilder(text.length());

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                if (c == '\r' || c == '\n' || c == '\t' || Character.isISOControl(c)) {
                    if (builder.isEmpty() || builder.charAt(builder.length() - 1) != ' ')
                        builder.append(' ');
                } else {
                    builder.append(c);
                }
            }

            this.text = builder.toString();
        }

        caretIndex = MathUtils.clamp(caretIndex, 0, this.text.length());
        clearSelection();
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    /**
     * <p>
     * Returns the current placeholder text.
     * </p>
     *
     * @return the placeholder text
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * <p>
     * Sets the placeholder text shown when the field is empty.
     * </p>
     *
     * @param placeholder the new placeholder text
     * @return this text field
     */
    public TextField placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns the current caret index.
     * </p>
     *
     * @return the caret insertion index
     */
    public int getCaretIndex() {
        return caretIndex;
    }

    /**
     * <p>
     * Sets the caret index.
     * </p>
     *
     * <p>
     * The value is clamped into the valid range of the current text, selection is
     * cleared, cursor blinking is reset, and horizontal scrolling is updated.
     * </p>
     *
     * @param caretIndex the new caret index
     * @return this text field
     */
    public TextField caretIndex(int caretIndex) {
        this.caretIndex = MathUtils.clamp(caretIndex, 0, text.length());
        clearSelection();
        resetCursorBlink();
        updateScroll();
        return this;
    }

    /**
     * <p>
     * Returns whether masking is currently enabled.
     * </p>
     *
     * @return {@code true} if masking is enabled
     */
    public boolean isMasking() {
        return masking;
    }

    /**
     * <p>
     * Enables or disables visual masking of the field text.
     * </p>
     *
     * <p>
     * When masking is enabled, the logical text remains unchanged but the displayed
     * string is replaced with repeated instances of {@link #maskChar}. Changing this
     * setting rebuilds display text and updates layout and scrolling.
     * </p>
     *
     * @param masking whether masking should be enabled
     * @return this text field
     */
    public TextField masking(boolean masking) {
        if (this.masking == masking) return this;

        this.masking = masking;
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    /**
     * <p>
     * Returns the current masking character.
     * </p>
     *
     * @return the mask character
     */
    public char getMaskChar() {
        return maskChar;
    }

    /**
     * <p>
     * Sets the masking character used when masking is enabled.
     * </p>
     *
     * @param maskChar the new masking character
     * @return this text field
     */
    public TextField maskChar(char maskChar) {
        if (this.maskChar == maskChar) return this;

        this.maskChar = maskChar;
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    /**
     * <p>
     * Returns the explicitly assigned Enter action.
     * </p>
     *
     * @return the explicit action, or {@code null} if none is assigned
     */
    public NodeAction<TextField> getAction() {
        return action;
    }

    /**
     * <p>
     * Assigns an explicit Enter action to this field.
     * </p>
     *
     * @param action the action to perform when Enter is pressed
     * @return this text field
     */
    public TextField action(NodeAction<TextField> action) {
        this.action = action;
        return this;
    }

    /**
     * <p>
     * Updates this text field.
     * </p>
     *
     * <p>
     * The method advances double-click timing when a click is pending, then updates
     * caret blinking while the field is focused. When not focused, the caret is forced
     * invisible and the blink timer is reset.
     * </p>
     *
     * @param delta the frame delta time in seconds
     */
    @Override
    public void update(float delta) {
        if (pendingClick) {
            doubleClickTimer += delta;
            if (doubleClickTimer > doubleClickWindow) {
                pendingClick = false;
                doubleClickTimer = 0f;
            }
        }

        if (!isFocused()) {
            blinkTime = 0f;
            caretVisible = false;
            return;
        }

        blinkTime += delta;
        if (blinkTime >= blinkInterval) {
            blinkTime = 0f;
            caretVisible = !caretVisible;
        }
    }

    /**
     * <p>
     * Handles mouse movement over the text field.
     * </p>
     *
     * <p>
     * This implementation currently delegates directly to the superclass.
     * </p>
     *
     * @param event the mouse move event
     */
    @Override
    public void onMouseMove(MouseMoveEvent event) {
        super.onMouseMove(event);
    }

    /**
     * <p>
     * Handles mouse press interaction for caret placement and selection behavior.
     * </p>
     *
     * <p>
     * A second click within the double-click window triggers select-all behavior.
     * Otherwise a new selection begins at the character index nearest the pressed
     * mouse position. Caret blinking is reset and scrolling is updated afterward.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        float mouseX = getEventX(event);

        if (pendingClick && doubleClickTimer <= doubleClickWindow) {
            pendingClick = false;
            doubleClickTimer = 0f;
            selectAll();
            selecting = false;
            resetCursorBlink();
            updateScroll();
            return;
        }

        pendingClick = true;
        doubleClickTimer = 0f;

        selecting = true;
        caretIndex = getIndexAtMouseX(mouseX);
        selectionStart = caretIndex;
        selectionEnd = caretIndex;

        resetCursorBlink();
        updateScroll();
    }

    /**
     * <p>
     * Handles mouse drag selection updates.
     * </p>
     *
     * <p>
     * While a selection drag is active, the caret and selection end are moved to the
     * character nearest the dragged mouse position.
     * </p>
     *
     * @param event the mouse drag event
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!selecting) return;

        float mouseX = getEventX(event);

        caretIndex = getIndexAtMouseX(mouseX);
        selectionEnd = caretIndex;

        resetCursorBlink();
        updateScroll();
    }

    /**
     * <p>
     * Handles mouse release by ending active selection dragging.
     * </p>
     *
     * @param event the mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        selecting = false;
    }

    /**
     * <p>
     * Handles keyboard editing and command shortcuts.
     * </p>
     *
     * <p>
     * If the field is not focused, no action is taken. When focused, the caret blink
     * state is reset and the event is interpreted according to modifier state:
     * </p>
     *
     * <ul>
     *     <li>Ctrl+A selects all</li>
     *     <li>Ctrl+C copies selection</li>
     *     <li>Ctrl+X cuts selection</li>
     *     <li>Ctrl+V pastes clipboard text</li>
     *     <li>Ctrl+Left and Ctrl+Right move by words</li>
     *     <li>Backspace and Delete remove text</li>
     *     <li>Arrow keys, Home, and End move the caret</li>
     *     <li>Enter performs the explicit or style-resolved action</li>
     *     <li>Printable characters are inserted at the caret</li>
     * </ul>
     *
     * @param event the key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (!isFocused()) return;

        resetCursorBlink();

        boolean shift = event.isShiftDown();
        boolean ctrl = event.isCtrlDown();
        int key = event.getKey();

        if (ctrl) {
            switch (key) {
                case Keyboard.A -> selectAll();
                case Keyboard.C -> copySelection();
                case Keyboard.X -> cutSelection();
                case Keyboard.V -> pasteClipboard();
                case Keyboard.LEFT -> moveCursor(prevWord(caretIndex), shift);
                case Keyboard.RIGHT -> moveCursor(nextWord(caretIndex), shift);
            }
            return;
        }

        switch (key) {
            case Keyboard.BACKSPACE -> deleteBackward();
            case Keyboard.DELETE -> deleteForward();
            case Keyboard.LEFT -> moveCursor(caretIndex - 1, shift);
            case Keyboard.RIGHT -> moveCursor(caretIndex + 1, shift);
            case Keyboard.HOME -> moveCursor(0, shift);
            case Keyboard.END -> moveCursor(text.length(), shift);
            case Keyboard.ENTER -> {
                if (action != null) {
                    action.perform(this);
                } else {
                    ResolvedStyle style = getStyle();
                    if (style != null) {
                        NodeAction<TextField> resolved = style.get(ACTION_KEY);
                        if (resolved != null) resolved.perform(this);
                    }
                }
                return;
            }
            default -> {
                char c = event.getChar();

                if (c == 0 || c == '\n' || c == '\r')
                    return;

                if (hasSelection()) deleteSelection();

                text = text.substring(0, caretIndex) + c + text.substring(caretIndex);
                caretIndex++;
            }

        }

        updateScroll();
        markDisplayTextDirty();
        markLayoutDirty();
    }

    /**
     * <p>
     * Applies style-driven layout configuration and resolved rendering resources.
     * </p>
     *
     * <p>
     * This method resolves drawables, colors, font, padding, caret dimensions,
     * scissor fudge, and default selection color from the current style. If a font
     * exists, the field sizes itself automatically when width or height are set to
     * auto. Layout is then delegated to the superclass.
     * </p>
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            background = style.get(BACKGROUND_KEY);
            hoverBackground = style.get(HOVER_BACKGROUND_KEY);
            focusedBackground = style.get(FOCUSED_BACKGROUND_KEY);
            font = style.get(FONT_KEY);
            textColor = style.get(COLOR_KEY);
            placeholderColor = style.get(PLACEHOLDER_COLOR_KEY);
            caretColor = style.get(CARET_COLOR_KEY);
            selectionColor = style.get(SELECTION_COLOR_KEY);

            Color resolvedDefaultSelectionColor = style.get(DEFAULT_SELECTION_COLOR_KEY);
            defaultSelectionColor = resolvedDefaultSelectionColor != null
                    ? resolvedDefaultSelectionColor
                    : new Color(1f, 1f, 1f, 0.35f);

            Float resolvedPadding = style.get(PADDING_KEY);
            padding = resolvedPadding != null ? resolvedPadding : 10f;

            Float resolvedCaretWidth = style.get(CARET_WIDTH_KEY);
            caretWidth = resolvedCaretWidth != null ? resolvedCaretWidth : 2f;

            Float resolvedCaretPadY = style.get(CARET_PADDING_Y_KEY);
            caretPadY = resolvedCaretPadY != null ? resolvedCaretPadY : 8f;

            Float resolvedScissorFudge = style.get(SCISSOR_FUDGE_KEY);
            scissorFudge = resolvedScissorFudge != null ? resolvedScissorFudge : 4f;

            if (font != null) {
                String measured = getDisplayText();
                String fallback = placeholder == null ? "" : placeholder;
                float measuredWidth = Math.max(font.getWidth(measured), font.getWidth(fallback)) + padding * 2f;
                float measuredHeight = font.getHeight("Ay") + padding * 2f;

                if (getLayout().getWidth().isAuto())
                    getLayout().width(measuredWidth);

                if (getLayout().getHeight().isAuto())
                    getLayout().height(measuredHeight);
            }
        } else {
            background = null;
            hoverBackground = null;
            focusedBackground = null;
            font = null;
            textColor = null;
            placeholderColor = null;
            caretColor = null;
            selectionColor = null;
            defaultSelectionColor = new Color(1f, 1f, 1f, 0.35f);
        }

        super.applyLayout();
        updateScroll();
    }

    /**
     * <p>
     * Draws the text field.
     * </p>
     *
     * <p>
     * The currently resolved background is drawn first. If no font is available,
     * rendering stops there. Otherwise a scissor region is established for the inner
     * text area, selection is drawn if present, then text and caret are rendered,
     * and finally scissoring is ended.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        ResolvedStyle style = getStyle();
        Drawable drawBackground = style != null ? style.get(BACKGROUND_KEY) : null;

        if (drawBackground != null)
            drawBackground.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());

        if (font == null)
            return;

        float scissorX = getRenderX() + padding - scissorFudge * 0.5f;
        float scissorW = Math.max(0f, getWidth() - padding * 2f + scissorFudge);

        batch.beginScissor(scissorX, getRenderY(), scissorW, getHeight());

        if (isFocused() && hasSelection())
            drawSelection(batch);

        drawText(batch);
        drawCaret(batch);

        batch.endScissor();
    }

    /**
     * <p>
     * Updates focus state for this field.
     * </p>
     *
     * <p>
     * When focus changes, cursor blinking is reset, active selection dragging is
     * canceled if focus is lost, and horizontal scrolling is recalculated.
     * </p>
     *
     * @param focused the new focus state
     */
    @Override
    public void setFocused(boolean focused) {
        boolean previous = isFocused();
        super.setFocused(focused);

        if (previous == focused) return;

        resetCursorBlink();
        if (!focused) selecting = false;

        updateScroll();
    }

    /**
     * <p>
     * Draws either the current text or placeholder text.
     * </p>
     *
     * <p>
     * If the visible text is empty, placeholder text is rendered when available.
     * Otherwise the visible text is drawn at the horizontally scrolled text origin.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    private void drawText(TextureBatch batch) {
        String visibleText = getDisplayText();
        boolean empty = visibleText.isEmpty();

        float drawX = getRenderX() + padding + textOffsetX;
        float drawY = getRenderY() + (getHeight() - font.getHeight("Ay")) * 0.5f;

        if (empty) {
            if (!placeholder.isEmpty()) {
                if (placeholderColor != null)
                    font.draw(batch, placeholder, getRenderX() + padding, drawY, placeholderColor);
                else font.draw(batch, placeholder, getRenderX() + padding, drawY);
            }
            return;
        }

        if (textColor != null) font.draw(batch, visibleText, drawX, drawY, textColor);
        else font.draw(batch, visibleText, drawX, drawY);
    }

    /**
     * <p>
     * Draws the active text selection highlight.
     * </p>
     *
     * <p>
     * The selection rectangle is computed from the measured width up to the selected
     * character indices. A solid color quad is drawn using the shared caret pixel
     * texture and the resolved or fallback selection color.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    private void drawSelection(TextureBatch batch) {
        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);

        float drawX = getRenderX() + padding + textOffsetX;
        float x1 = drawX + getTextWidthUpTo(a);
        float x2 = drawX + getTextWidthUpTo(b);

        float selectionH = Math.max(0f, getHeight() - caretPadY * 2f);
        float selectionY = getRenderY() + (getHeight() - selectionH) * 0.5f;

        Color drawSelectionColor = selectionColor != null ? selectionColor : defaultSelectionColor;
        batch.draw(getCaretPixel(), x1, selectionY, x2 - x1, selectionH, drawSelectionColor);
    }

    /**
     * <p>
     * Draws the caret if the field is focused and the blink state says it should be visible.
     * </p>
     *
     * <p>
     * The caret X position is based on the measured width up to the current caret
     * index plus the horizontal text offset.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    private void drawCaret(TextureBatch batch) {
        if (!isFocused() || !caretVisible) return;

        float drawX = getRenderX() + padding + textOffsetX;
        float caretX = drawX + getTextWidthUpTo(caretIndex);
        float caretH = Math.max(0f, getHeight() - caretPadY * 2f);
        float caretY = getRenderY() + (getHeight() - caretH) * 0.5f;

        Color drawCaretColor = caretColor != null ? caretColor : (textColor != null ? textColor : Color.WHITE);
        batch.draw(getCaretPixel(), caretX, caretY, caretWidth, caretH, drawCaretColor);
    }

    /**
     * <p>
     * Returns the event X position in the field's active coordinate space for mouse press events.
     * </p>
     *
     * @param event the mouse press event
     * @return the resolved X coordinate
     */
    private float getEventX(MousePressEvent event) {
        return getEventPosition(event.getX(), event.getY()).getX();
    }

    /**
     * <p>
     * Returns the event X position in the field's active coordinate space for mouse drag events.
     * </p>
     *
     * @param event the mouse drag event
     * @return the resolved X coordinate
     */
    private float getEventX(MouseDragEvent event) {
        return getEventPosition(event.getX(), event.getY()).getX();
    }

    /**
     * <p>
     * Converts screen-space event coordinates into viewport world coordinates when a
     * viewport exists, otherwise returns the raw coordinates.
     * </p>
     *
     * @param screenX the screen X coordinate
     * @param screenY the screen Y coordinate
     * @return the resolved interaction position
     */
    private Vector2f getEventPosition(float screenX, float screenY) {
        UIRoot root = getRoot();
        if (root != null) {
            Viewport viewport = root.getViewport();
            if (viewport != null) {
                Vector2f world = viewport.screenToWorld(screenX, screenY);
                if (world != null) return world;
            }
        }

        return new Vector2f(screenX, screenY);
    }

    /**
     * <p>
     * Resets caret blinking so the caret becomes visible immediately.
     * </p>
     */
    private void resetCursorBlink() {
        blinkTime = 0f;
        caretVisible = true;
    }

    /**
     * <p>
     * Returns whether a non-empty text selection currently exists.
     * </p>
     *
     * @return {@code true} if the selection range is non-empty
     */
    private boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    /**
     * <p>
     * Clears the current selection by collapsing both selection ends to the caret index.
     * </p>
     */
    private void clearSelection() {
        selectionStart = caretIndex;
        selectionEnd = caretIndex;
    }

    /**
     * <p>
     * Selects all text currently stored by the field.
     * </p>
     *
     * <p>
     * The caret moves to the end of the text and scrolling is updated.
     * </p>
     */
    private void selectAll() {
        selectionStart = 0;
        selectionEnd = text.length();
        caretIndex = text.length();
        updateScroll();
    }

    /**
     * <p>
     * Moves the caret to a new index and optionally extends the selection.
     * </p>
     *
     * <p>
     * When extending is enabled, the selection anchor is preserved or initialized.
     * Otherwise the selection is collapsed to the new caret position.
     * </p>
     *
     * @param index  the target caret index
     * @param extend whether selection should be extended
     */
    private void moveCursor(int index, boolean extend) {
        index = MathUtils.clamp(index, 0, text.length());

        if (extend) {
            if (!hasSelection()) selectionStart = caretIndex;
            selectionEnd = index;
        } else {
            selectionStart = index;
            selectionEnd = index;
        }

        caretIndex = index;
        updateScroll();
    }

    /**
     * <p>
     * Deletes the character before the caret, or deletes the current selection if one exists.
     * </p>
     */
    private void deleteBackward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (caretIndex <= 0) return;

        text = text.substring(0, caretIndex - 1) + text.substring(caretIndex);
        caretIndex--;
        clearSelection();

        markDisplayTextDirty();
        markLayoutDirty();
    }

    /**
     * <p>
     * Deletes the character after the caret, or deletes the current selection if one exists.
     * </p>
     */
    private void deleteForward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (caretIndex >= text.length()) return;

        text = text.substring(0, caretIndex) + text.substring(caretIndex + 1);
        clearSelection();

        markDisplayTextDirty();
        markLayoutDirty();
    }

    /**
     * <p>
     * Deletes the currently selected text range.
     * </p>
     *
     * <p>
     * After deletion, the caret moves to the beginning of the removed range and the
     * selection is collapsed.
     * </p>
     */
    private void deleteSelection() {
        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);

        text = text.substring(0, a) + text.substring(b);
        caretIndex = a;
        selectionStart = a;
        selectionEnd = a;

        markDisplayTextDirty();
        markLayoutDirty();
    }

    /**
     * <p>
     * Copies the currently selected text to the system clipboard.
     * </p>
     */
    private void copySelection() {
        if (!hasSelection()) return;

        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.substring(a, b)), null);
    }

    /**
     * <p>
     * Cuts the currently selected text to the system clipboard.
     * </p>
     *
     * <p>
     * This is implemented as copy followed by delete-selection.
     * </p>
     */
    private void cutSelection() {
        if (!hasSelection()) return;

        copySelection();
        deleteSelection();
    }

    /**
     * <p>
     * Pastes text from the system clipboard into the field.
     * </p>
     *
     * <p>
     * Clipboard text is sanitized in the same way as assigned text, with control
     * characters converted to spaces. If a selection exists, it is replaced by the
     * pasted text. Errors while accessing the clipboard are ignored.
     * </p>
     */
    private void pasteClipboard() {
        try {
            String value = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (value == null || value.isEmpty())
                return;

            if (hasSelection())
                deleteSelection();

            StringBuilder builder = new StringBuilder(value.length());

            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);

                if (c == '\r' || c == '\n' || c == '\t' || Character.isISOControl(c)) {
                    if (builder.isEmpty() || builder.charAt(builder.length() - 1) != ' ')
                        builder.append(' ');
                } else {
                    builder.append(c);
                }
            }

            value = builder.toString();

            text = text.substring(0, caretIndex) + value + text.substring(caretIndex);
            caretIndex += value.length();
            clearSelection();

            markDisplayTextDirty();
            markLayoutDirty();
            updateScroll();
        } catch (Throwable ignored) {
        }
    }

    /**
     * <p>
     * Returns the currently visible text representation.
     * </p>
     *
     * <p>
     * When masking is disabled or the logical text is empty, this returns the raw text.
     * When masking is enabled, a cached masked string is generated and returned.
     * </p>
     *
     * @return the visible text used for rendering and measurement
     */
    private String getDisplayText() {
        if (!masking || text.isEmpty()) return text;

        if (displayTextDirty) {
            StringBuilder builder = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++)
                builder.append(maskChar);
            displayText = builder.toString();
            displayTextDirty = false;
        }

        return displayText;
    }

    /**
     * <p>
     * Marks the cached display text as dirty so it will be rebuilt when next needed.
     * </p>
     */
    private void markDisplayTextDirty() {
        displayTextDirty = true;
    }

    /**
     * <p>
     * Returns the rendered width of the visible text up to the given character index.
     * </p>
     *
     * @param index the exclusive character index
     * @return the rendered width up to the given index
     */
    private float getTextWidthUpTo(int index) {
        if (font == null || index <= 0) return 0f;

        String visibleText = getDisplayText();
        return font.getWidth(visibleText, 0, Math.min(index, visibleText.length()));
    }

    /**
     * <p>
     * Returns the nearest caret index for a given mouse X coordinate.
     * </p>
     *
     * <p>
     * The coordinate is interpreted relative to the left edge of the visible text area
     * including horizontal scrolling. The method walks forward through the visible
     * text until the measured width meets or exceeds the local coordinate.
     * </p>
     *
     * @param mouseX the mouse X coordinate in the active interaction space
     * @return the nearest caret index
     */
    private int getIndexAtMouseX(float mouseX) {
        if (font == null) return text.length();

        float local = mouseX - (getRenderX() + padding + textOffsetX);
        if (local <= 0f) return 0;

        String visibleText = getDisplayText();
        int length = visibleText.length();

        for (int i = 1; i <= length; i++) {
            if (font.getWidth(visibleText, 0, i) >= local) return i;
        }

        return length;
    }

    /**
     * <p>
     * Returns the previous word-boundary caret position.
     * </p>
     *
     * <p>
     * The method first skips trailing whitespace to the left and then skips the
     * preceding run of letters or digits.
     * </p>
     *
     * @param index the starting caret index
     * @return the previous word boundary
     */
    private int prevWord(int index) {
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) index--;
        while (index > 0 && Character.isLetterOrDigit(text.charAt(index - 1))) index--;
        return index;
    }

    /**
     * <p>
     * Returns the next word-boundary caret position.
     * </p>
     *
     * <p>
     * The method first skips whitespace to the right and then skips the next run
     * of letters or digits.
     * </p>
     *
     * @param index the starting caret index
     * @return the next word boundary
     */
    private int nextWord(int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        while (index < text.length() && Character.isLetterOrDigit(text.charAt(index))) index++;
        return index;
    }

    /**
     * <p>
     * Updates the horizontal scroll offset so the caret remains visible.
     * </p>
     *
     * <p>
     * If the visible text fits within the available inner width, no scrolling is used.
     * Otherwise the offset is clamped and adjusted so the caret stays within the visible
     * text area.
     * </p>
     */
    private void updateScroll() {
        if (font == null) return;

        float available = getWidth() - padding * 2f;
        if (available <= 0f) {
            textOffsetX = 0f;
            return;
        }

        float textWidth = font.getWidth(getDisplayText());
        float caretX = getTextWidthUpTo(caretIndex);

        if (textWidth <= available) {
            textOffsetX = 0f;
            return;
        }

        float minOffset = available - textWidth;
        float maxOffset = 0f;

        textOffsetX = MathUtils.clamp(textOffsetX, minOffset, maxOffset);

        float visibleCaret = caretX + textOffsetX;
        if (visibleCaret > available) {
            textOffsetX = available - caretX;
        } else if (visibleCaret < 0f) {
            textOffsetX = -caretX;
        }

        textOffsetX = MathUtils.clamp(textOffsetX, minOffset, maxOffset);
    }

    /**
     * <p>
     * Returns the shared 1x1 white texture used for drawing the caret and selection.
     * </p>
     *
     * <p>
     * The texture is lazily created on first use from a single white RGBA pixel.
     * </p>
     *
     * @return the shared caret pixel texture
     */
    private static Texture getCaretPixel() {
        if (CARET_PIXEL == null) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(4);
            buffer.put((byte) 255);
            buffer.put((byte) 255);
            buffer.put((byte) 255);
            buffer.put((byte) 255);
            buffer.flip();
            CARET_PIXEL = new Texture(new TextureData(buffer, 1, 1));
        }

        return CARET_PIXEL;
    }
}