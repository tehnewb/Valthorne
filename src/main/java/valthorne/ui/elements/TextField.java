package valthorne.ui.elements;

import org.lwjgl.BufferUtils;
import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Color;
import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.Element;
import valthorne.ui.UIAction;
import valthorne.ui.enums.Alignment;
import valthorne.ui.styles.TextFieldStyle;
import valthorne.viewport.Viewport;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * <h1>TextField</h1>
 *
 * <p>
 * {@code TextField} is a single-line editable UI input element that supports typing, caret movement,
 * selection, clipboard operations, placeholder text, password-style masking, horizontal scrolling,
 * and clipped rendering through the active {@link TextureBatch}.
 * </p>
 *
 * <p>
 * This class is designed for immediate-mode style scene rendering where the text field is updated
 * and drawn every frame, while still caching enough internal state to avoid unnecessary string
 * reconstruction. The element stores raw input text separately from displayed text so that masking
 * mode can present replacement characters without modifying the actual typed value.
 * </p>
 *
 * <h2>Core behavior</h2>
 *
 * <ul>
 *     <li>The field stores real user text in {@link #text}.</li>
 *     <li>The visible text is either the raw text or a masked equivalent.</li>
 *     <li>The caret position is tracked by {@link #cursorIndex}.</li>
 *     <li>The selection range is tracked by {@link #selectionStart} and {@link #selectionEnd}.</li>
 *     <li>When text exceeds the available width, the text scrolls horizontally to keep the caret visible.</li>
 * </ul>
 *
 * <h2>Rendering flow</h2>
 *
 * <p>
 * Rendering happens in a layered order:
 * </p>
 *
 * <ol>
 *     <li>The current background drawable is rendered.</li>
 *     <li>A scissor region is pushed into the batch so inner content is clipped.</li>
 *     <li>The selection highlight is drawn with a reusable 1x1 white texture.</li>
 *     <li>The font is drawn.</li>
 *     <li>The caret is drawn with the same reusable white texture.</li>
 *     <li>The scissor region is popped from the batch.</li>
 * </ol>
 *
 * <h2>Input behavior</h2>
 *
 * <p>
 * The field supports normal text editing controls such as left/right navigation, home/end,
 * backspace, delete, word jumps with control, selection extension with shift, and clipboard
 * copy/cut/paste. Double-click selects all text.
 * </p>
 *
 * <h2>Masking behavior</h2>
 *
 * <p>
 * When masking is enabled, displayed text is lazily rebuilt only when needed. This avoids
 * regenerating the masked string every frame while still keeping the visible content correct.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * TextFieldStyle style = TextFieldStyle.of()
 *     .fontData(fontData)
 *     .background(normalDrawable)
 *     .focused(focusedDrawable);
 *
 * TextField username = new TextField("Username", style, field -> {
 *     System.out.println(field.getText());
 * });
 *
 * username.setPosition(32f, 32f);
 * username.setSize(320f, 42f);
 * username.setCharacterLimit(32);
 *
 * TextField password = new TextField("Password", style, null);
 * password.setMasking(true);
 * password.setMaskCharacter('*');
 * password.setPosition(32f, 84f);
 * password.setSize(320f, 42f);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class TextField extends Element {

    private TextFieldStyle style; // The style that supplies the font data and background drawables.
    private Drawable current; // The drawable currently used to render the field background.
    private Font font; // The font used to render placeholder text or visible input text.
    private final Texture uiPixel; // The reusable 1x1 white texture used for selection and caret rendering.
    private final Color selectionColor = new Color(1f, 1f, 1f, 0.35f); // The tint used when drawing the selected text region.
    private final Color caretColor = new Color(1f, 1f, 1f, 1f); // The tint used when drawing the text caret.

    private String text = ""; // The raw unmasked text entered by the user.
    private String placeHolder; // The placeholder string shown while the field is empty and unfocused.
    private UIAction<TextField> action; // The action invoked when the field is submitted.

    private boolean masking; // Whether the field should render masked characters instead of real text.
    private char maskCharacter = '*'; // The character used to replace visible text while masking is enabled.

    private String displayText = ""; // The cached displayed text used when masking is enabled.
    private boolean displayTextDirty = true; // Whether the cached displayed text must be rebuilt.

    private int cursorIndex = 0; // The insertion index of the caret within the raw text.

    private int characterLimit; // The maximum allowed character count, or non-positive for unlimited.

    private int selectionStart = 0; // The anchor position of the active selection.
    private int selectionEnd = 0; // The moving position of the active selection.
    private boolean selecting; // Whether the user is currently dragging to update the selection.

    private boolean cursorActive = true; // Whether the blinking caret is currently visible.
    private float cursorBlinkTime; // The current accumulated time used by the caret blink timer.
    private float cursorBlinkInterval = 0.5f; // The number of seconds between caret visibility toggles.
    private float maxCaretHeight; // The maximum allowed height of the caret based on the font size.

    private float textOffsetX = 0f; // The horizontal scroll offset applied to the rendered text.
    private float padding = 10f; // The inner horizontal padding between the field bounds and the text.

    private float caretWidth = 2f; // The width of the rendered caret.
    private float caretPadY = 8f; // The vertical inset applied when computing the caret height.

    private float doubleClickTimer = 0f; // The timer used to determine whether a second click is a double click.
    private float doubleClickWindow = 0.25f; // The maximum allowed delay between clicks for double-click detection.
    private boolean pendingClick; // Whether the first click of a potential double click has already occurred.

    private float scissorFudge = 4f; // Extra clipping width used to reduce visual edge clipping at the text bounds.

    /**
     * Creates a new text field using the given placeholder, style, and submit action.
     *
     * <p>
     * This constructor initializes the internal font from the provided style, sets the initial
     * placeholder text into the font, selects the default background drawable, marks the element
     * as focusable, and creates the reusable white pixel texture used for caret and selection
     * rendering.
     * </p>
     *
     * @param placeHolder the placeholder text shown while the field is empty and not focused
     * @param style       the style that defines font data and drawables
     * @param action      the action invoked when enter is pressed, or null if none
     */
    public TextField(String placeHolder, TextFieldStyle style, UIAction<TextField> action) {
        this.style = style;
        this.font = new Font(style.getFontData());
        this.placeHolder = placeHolder;
        this.action = action;
        this.font.setText(placeHolder);
        this.current = style.getBackground();
        this.setFocusable(true);
        this.maxCaretHeight = font.getHeight() * 2;
        this.uiPixel = createWhitePixelTexture();
    }

    /**
     * Updates the text field state.
     *
     * <p>
     * This method advances the double-click timer when a click is pending and updates the caret
     * blink timer while the field is focused. When the blink interval is reached, caret visibility
     * toggles.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous frame
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
            return;
        }

        cursorBlinkTime += delta;
        if (cursorBlinkTime >= cursorBlinkInterval) {
            cursorBlinkTime = 0f;
            cursorActive = !cursorActive;
        }
    }

    /**
     * Draws the text field.
     *
     * <p>
     * The background is drawn first. Then an inner scissor region is pushed into the batch so that
     * text, selection, and caret are clipped to the input area. The selection highlight is rendered
     * before the text so it appears behind glyphs, and the caret is drawn last.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (current != null) {
            current.draw(batch, x, y, width, height);
        }

        float sx = x + padding - scissorFudge * 0.5f;
        float sw = (width - padding * 2f) + scissorFudge;

        batch.beginScissor(sx, y, sw, height);

        if (isFocused() && hasSelection()) {
            drawSelection(batch);
        }

        font.draw(batch);
        drawCaret(batch);

        batch.endScissor();
    }

    /**
     * Draws the current text selection highlight.
     *
     * <p>
     * The method computes the selected range in pixels using the font's width measurement functions,
     * vertically centers the selection rectangle in the field, and renders it using the reusable
     * white pixel texture tinted with {@link #selectionColor}.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    private void drawSelection(TextureBatch batch) {
        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);

        float x1 = font.getX() + getTextWidthUpTo(a);
        float x2 = font.getX() + getTextWidthUpTo(b);

        float caretHeight = getCaretHeight();
        float centerY = y + height * 0.5f;
        float top = centerY - caretHeight * 0.5f;

        batch.draw(uiPixel, x1, top, x2 - x1, caretHeight, selectionColor);
    }

    /**
     * Draws the caret if it is currently visible and the field is focused.
     *
     * <p>
     * The caret position is computed from the font origin plus the width of the visible text up to
     * the current cursor index. The caret rectangle is vertically centered in the field and rendered
     * using the reusable white texture tinted with {@link #caretColor}.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    private void drawCaret(TextureBatch batch) {
        if (!cursorActive || !isFocused()) {
            return;
        }

        float cx = font.getX() + getCursorX();
        float caretHeight = getCaretHeight();

        float centerY = y + height * 0.5f;
        float top = centerY - caretHeight * 0.5f;

        batch.draw(uiPixel, cx, top, caretWidth, caretHeight, caretColor);
    }

    /**
     * Handles key press input while the field is focused.
     *
     * <p>
     * This method supports character insertion, caret navigation, deletion, word jumping, clipboard
     * operations, submit actions, and select-all behavior. Modifier-aware input is processed before
     * normal key handling.
     * </p>
     *
     * @param event the key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        super.onKeyPress(event);
        if (!isFocused()) {
            return;
        }

        resetCursorBlink();

        boolean shift = event.isShiftDown();
        boolean ctrl = event.isCtrlDown();

        if (ctrl) {
            switch (event.getKey()) {
                case Keyboard.V -> pasteClipboard();
                case Keyboard.C -> copySelection();
                case Keyboard.X -> cutSelection();
                case Keyboard.A -> selectAll();
                case Keyboard.LEFT -> moveCursor(prevWord(cursorIndex), shift);
                case Keyboard.RIGHT -> moveCursor(nextWord(cursorIndex), shift);
            }
            return;
        }

        switch (event.getKey()) {
            case Keyboard.ENTER -> {
                if (action != null) {
                    action.perform(this);
                }
            }
            case Keyboard.LEFT -> moveCursor(cursorIndex - 1, shift);
            case Keyboard.RIGHT -> moveCursor(cursorIndex + 1, shift);
            case Keyboard.HOME -> moveCursor(0, shift);
            case Keyboard.END -> moveCursor(text.length(), shift);
            case Keyboard.BACKSPACE -> deleteBackward();
            case Keyboard.DELETE -> deleteForward();
            default -> insertChar(event.getChar());
        }
    }

    /**
     * Handles mouse press input.
     *
     * <p>
     * The pointer is converted from screen space into world space using the UI viewport. A double click
     * selects all text, otherwise the caret is moved to the closest character index and selection begins.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        super.onMousePress(event);

        Viewport viewport = getUI().getViewport();
        Vector2f world = viewport.screenToWorld(event.getX(), event.getY());
        if (world == null) {
            return;
        }

        float mx = world.getX();

        resetCursorBlink();

        if (pendingClick && doubleClickTimer <= doubleClickWindow) {
            pendingClick = false;
            doubleClickTimer = 0f;
            selectAll();
            selecting = false;
            return;
        }

        pendingClick = true;
        doubleClickTimer = 0f;

        selecting = true;
        cursorIndex = getIndexAtMouseX(mx);
        selectionStart = cursorIndex;
        selectionEnd = cursorIndex;

        updateScroll();
    }

    /**
     * Handles mouse drag input to extend or shrink the current selection.
     *
     * <p>
     * The drag position is converted into world space and then mapped to the closest character index.
     * The active selection endpoint follows the cursor while dragging.
     * </p>
     *
     * @param event the mouse drag event
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!selecting) {
            return;
        }

        Viewport viewport = getUI().getViewport();
        Vector2f world = viewport.screenToWorld(event.getX(), event.getY());
        if (world == null) {
            return;
        }

        cursorIndex = getIndexAtMouseX(world.getX());
        selectionEnd = cursorIndex;

        updateScroll();
    }

    /**
     * Ends selection dragging when the mouse button is released.
     *
     * @param event the mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        selecting = false;
    }

    /**
     * Returns whether a character limit is currently active.
     *
     * @return true if the character limit is greater than zero
     */
    private boolean hasCharacterLimit() {
        return characterLimit > 0;
    }

    /**
     * Computes how many more characters may still be inserted.
     *
     * <p>
     * When no character limit is active, this method returns {@link Integer#MAX_VALUE}.
     * </p>
     *
     * @return the remaining insert capacity
     */
    private int remainingCapacity() {
        if (!hasCharacterLimit()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, characterLimit - text.length());
    }

    /**
     * Trims a string so that it fits within the remaining character capacity.
     *
     * <p>
     * This is primarily used for paste operations. If no limit is active, the string is returned
     * unchanged. If no characters can be inserted, an empty string is returned.
     * </p>
     *
     * @param s the string to clamp
     * @return the clamped string, or null if the input was null
     */
    private String clampToLimit(String s) {
        if (s == null) {
            return null;
        }
        if (!hasCharacterLimit()) {
            return s;
        }

        int cap = remainingCapacity();
        if (cap <= 0) {
            return "";
        }
        if (s.length() <= cap) {
            return s;
        }

        return s.substring(0, cap);
    }

    /**
     * Inserts a printable character at the current cursor position.
     *
     * <p>
     * Existing selection is replaced if present. Line breaks are rejected because this field is
     * single-line only. After insertion, displayed text state, font content, and scroll offset are
     * updated.
     * </p>
     *
     * @param c the character to insert
     */
    private void insertChar(char c) {
        if (c == 0 || c == '\n' || c == '\r') {
            return;
        }

        if (hasSelection()) {
            deleteSelection();
        }

        if (hasCharacterLimit() && text.length() >= characterLimit) {
            return;
        }

        text = text.substring(0, cursorIndex) + c + text.substring(cursorIndex);
        cursorIndex++;

        markDisplayTextDirty();
        updateFontText();
        updateScroll();
    }

    /**
     * Deletes the character immediately before the caret, or deletes the active selection.
     */
    private void deleteBackward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (cursorIndex == 0) {
            return;
        }

        text = text.substring(0, cursorIndex - 1) + text.substring(cursorIndex);
        cursorIndex--;

        markDisplayTextDirty();
        updateFontText();
        updateScroll();
    }

    /**
     * Deletes the character immediately after the caret, or deletes the active selection.
     */
    private void deleteForward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (cursorIndex >= text.length()) {
            return;
        }

        text = text.substring(0, cursorIndex) + text.substring(cursorIndex + 1);

        markDisplayTextDirty();
        updateFontText();
        updateScroll();
    }

    /**
     * Deletes the currently selected text range.
     *
     * <p>
     * The selection range is normalized before removal so it works regardless of drag direction.
     * The caret then collapses to the start of the removed range.
     * </p>
     */
    private void deleteSelection() {
        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);

        text = text.substring(0, a) + text.substring(b);
        cursorIndex = a;
        clearSelection();

        markDisplayTextDirty();
        updateFontText();
        updateScroll();
    }

    /**
     * Moves the caret to a new index and optionally extends selection.
     *
     * <p>
     * The index is clamped into the valid text range. When extension is enabled, the previous
     * cursor position becomes the anchor for the selection if no selection was active already.
     * </p>
     *
     * @param index  the desired new cursor index
     * @param extend whether selection should be extended to the new cursor position
     */
    private void moveCursor(int index, boolean extend) {
        index = MathUtils.clamp(index, 0, text.length());

        if (extend) {
            if (!hasSelection()) {
                selectionStart = cursorIndex;
            }
            selectionEnd = index;
        } else {
            clearSelection();
        }

        cursorIndex = index;
        updateScroll();
    }

    /**
     * Returns whether a selection is currently active.
     *
     * @return true if the selection range is non-empty
     */
    private boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    /**
     * Clears the current selection by collapsing it to the caret position.
     */
    private void clearSelection() {
        selectionStart = cursorIndex;
        selectionEnd = cursorIndex;
    }

    /**
     * Selects the entire text contents.
     *
     * <p>
     * The caret is moved to the end of the text and the scroll offset is updated so the caret
     * remains visible.
     * </p>
     */
    private void selectAll() {
        selectionStart = 0;
        selectionEnd = text.length();
        cursorIndex = selectionEnd;
        updateScroll();
    }

    /**
     * Copies the active selection into the system clipboard.
     */
    private void copySelection() {
        if (!hasSelection()) {
            return;
        }

        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);
        setClipboard(text.substring(a, b));
    }

    /**
     * Cuts the active selection by copying it and then deleting it.
     */
    private void cutSelection() {
        copySelection();
        deleteSelection();
    }

    /**
     * Pastes clipboard text into the field at the caret position.
     *
     * <p>
     * Existing selection is replaced if present. The pasted text is clamped to the remaining
     * character capacity when a limit is active.
     * </p>
     */
    private void pasteClipboard() {
        String s = getClipboard();
        if (s == null || s.isEmpty()) {
            return;
        }

        if (hasSelection()) {
            deleteSelection();
        }

        s = clampToLimit(s);
        if (s.isEmpty()) {
            updateFontText();
            updateScroll();
            return;
        }

        text = text.substring(0, cursorIndex) + s + text.substring(cursorIndex);
        cursorIndex += s.length();

        markDisplayTextDirty();
        updateFontText();
        updateScroll();
    }

    /**
     * Reads plain text from the system clipboard.
     *
     * @return the clipboard text, or null if clipboard access fails
     */
    private String getClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes plain text into the system clipboard.
     *
     * @param s the string to place into the clipboard
     */
    private void setClipboard(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    /**
     * Resets the caret blink state so the caret becomes visible immediately.
     */
    private void resetCursorBlink() {
        cursorBlinkTime = 0f;
        cursorActive = true;
    }

    /**
     * Marks the cached displayed text as needing a rebuild.
     */
    private void markDisplayTextDirty() {
        displayTextDirty = true;
    }

    /**
     * Returns the current caret x offset relative to the rendered text origin.
     *
     * @return the horizontal caret offset
     */
    private float getCursorX() {
        return getTextWidthUpTo(cursorIndex);
    }

    /**
     * Measures the displayed text width from index zero up to the supplied index.
     *
     * @param index the exclusive end index
     * @return the measured width
     */
    private float getTextWidthUpTo(int index) {
        if (index <= 0) {
            return 0f;
        }
        return font.getWidth(getDisplayText(), 0, index);
    }

    /**
     * Returns the visible text string that should be rendered.
     *
     * <p>
     * When masking is disabled, the raw text is returned directly. When masking is enabled,
     * a cached string of repeated mask characters is returned and rebuilt only when necessary.
     * </p>
     *
     * @return the currently visible display text
     */
    private String getDisplayText() {
        if (!masking) {
            return text;
        }

        if (displayTextDirty) {
            int length = text.length();
            if (length == 0) {
                displayText = "";
            } else {
                char[] chars = new char[length];
                Arrays.fill(chars, maskCharacter);
                displayText = new String(chars);
            }
            displayTextDirty = false;
        }

        return displayText;
    }

    /**
     * Finds the text index closest to the given mouse x coordinate.
     *
     * <p>
     * The x position is converted into local text space and compared against cumulative measured
     * character widths until the nearest insertion point is found.
     * </p>
     *
     * @param mouseX the pointer x coordinate in world space
     * @return the nearest text insertion index
     */
    private int getIndexAtMouseX(float mouseX) {
        float local = mouseX - font.getX() - padding;
        if (local <= 0f) {
            return 0;
        }

        String d = getDisplayText();
        int length = d.length();

        for (int i = 1; i <= length; i++) {
            if (font.getWidth(d, 0, i) >= local) {
                return i;
            }
        }

        return length;
    }

    /**
     * Finds the previous word boundary before the supplied index.
     *
     * @param i the starting index
     * @return the previous word boundary
     */
    private int prevWord(int i) {
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /**
     * Finds the next word boundary after the supplied index.
     *
     * @param i the starting index
     * @return the next word boundary
     */
    private int nextWord(int i) {
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Updates the text currently assigned to the font.
     *
     * <p>
     * When the field is empty and not focused, the placeholder is shown. Otherwise the visible
     * display text is shown.
     * </p>
     */
    private void updateFontText() {
        font.setText(text.isEmpty() && !isFocused() ? placeHolder : getDisplayText());
    }

    /**
     * Updates horizontal scrolling so the caret remains visible inside the field.
     *
     * <p>
     * If the full text fits within the available width, scrolling is reset. Otherwise the text
     * offset is adjusted and clamped so the caret remains inside the visible input area.
     * </p>
     */
    private void updateScroll() {
        float avail = width - padding * 2f;

        if (avail <= 0f) {
            textOffsetX = 0f;
            updateFontPosition();
            return;
        }

        float textWidth = font.getWidth(getDisplayText());
        float cx = getCursorX();

        if (textWidth <= avail) {
            textOffsetX = 0f;
            updateFontPosition();
            return;
        }

        float minOffset = avail - textWidth;
        float maxOffset = 0f;

        textOffsetX = MathUtils.clamp(textOffsetX, minOffset, maxOffset);

        float caretX = cx + textOffsetX;

        if (caretX > avail) {
            textOffsetX = avail - cx;
        } else if (caretX < 0f) {
            textOffsetX = -cx;
        }

        textOffsetX = MathUtils.clamp(textOffsetX, minOffset, maxOffset);
        updateFontPosition();
    }

    /**
     * Updates the font position based on field alignment, padding, and current scroll.
     *
     * <p>
     * Text is aligned horizontally to the start and vertically to the center of the field.
     * The active horizontal text offset is applied after alignment.
     * </p>
     */
    private void updateFontPosition() {
        Vector2f align = Alignment.align(this, font, Alignment.START, Alignment.CENTER);
        font.setPosition(align.getX() + padding + textOffsetX, align.getY());
    }

    /**
     * Sets the world position of the field and updates the text render position.
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     */
    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        updateFontPosition();
    }

    /**
     * Sets the field size and updates the text render position.
     *
     * @param width  the new width
     * @param height the new height
     */
    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        updateFontPosition();
    }

    /**
     * Updates focus state and refreshes drawable, text, and scroll state.
     *
     * <p>
     * The caret blink timer is reset whenever focus changes. The background drawable switches
     * between normal and focused drawables when available.
     * </p>
     *
     * @param value true if the field should become focused
     */
    @Override
    protected void setFocused(boolean value) {
        super.setFocused(value);

        resetCursorBlink();
        current = value ? style.getFocused() : style.getBackground();

        updateFontText();
        updateScroll();
    }

    /**
     * Returns whether text masking is enabled.
     *
     * @return true if masking is enabled
     */
    public boolean isMasking() {
        return masking;
    }

    /**
     * Enables or disables text masking.
     *
     * <p>
     * When masking changes, cached display text is invalidated and rendered text state is refreshed.
     * </p>
     *
     * @param masking true to enable masking
     */
    public void setMasking(boolean masking) {
        if (this.masking == masking) {
            return;
        }

        this.masking = masking;
        markDisplayTextDirty();

        updateFontText();
        updateScroll();
    }

    /**
     * Returns the mask character currently used while masking is enabled.
     *
     * @return the mask character
     */
    public char getMaskCharacter() {
        return maskCharacter;
    }

    /**
     * Sets the mask character used while masking is enabled.
     *
     * @param maskCharacter the new mask character
     */
    public void setMaskCharacter(char maskCharacter) {
        if (this.maskCharacter == maskCharacter) {
            return;
        }

        this.maskCharacter = maskCharacter;
        if (masking) {
            markDisplayTextDirty();
            updateFontText();
            updateScroll();
        }
    }

    /**
     * Returns the raw stored text.
     *
     * @return the unmasked text value
     */
    public String getText() {
        return text;
    }

    /**
     * Replaces the field text with the supplied value.
     *
     * <p>
     * Null becomes an empty string. The value is clamped to the character limit when one is active.
     * The caret and selection are then clamped into the new valid range.
     * </p>
     *
     * @param value the new text value
     */
    public void setText(String value) {
        if (value == null) {
            value = "";
        }

        if (hasCharacterLimit() && value.length() > characterLimit) {
            value = value.substring(0, characterLimit);
        }

        this.text = value;
        markDisplayTextDirty();

        cursorIndex = MathUtils.clamp(cursorIndex, 0, text.length());
        selectionStart = cursorIndex;
        selectionEnd = cursorIndex;

        updateFontText();
        updateScroll();
    }

    /**
     * Sets the maximum number of characters allowed in this field.
     *
     * <p>
     * If the current text exceeds the new limit, it is trimmed immediately and the caret and selection
     * are corrected to remain valid.
     * </p>
     *
     * @param limit the new character limit, or non-positive for unlimited
     */
    public void setCharacterLimit(int limit) {
        this.characterLimit = limit;

        if (hasCharacterLimit() && text.length() > characterLimit) {
            text = text.substring(0, characterLimit);
            markDisplayTextDirty();

            cursorIndex = MathUtils.clamp(cursorIndex, 0, text.length());
            selectionStart = cursorIndex;
            selectionEnd = cursorIndex;

            updateFontText();
            updateScroll();
        }
    }

    /**
     * Returns the current character limit.
     *
     * @return the character limit, or non-positive for unlimited
     */
    public int getCharacterLimit() {
        return characterLimit;
    }

    /**
     * Computes the caret height based on field height and configured vertical padding.
     *
     * @return the computed caret height
     */
    private float getCaretHeight() {
        float availHeight = height - caretPadY * 2f;
        return Math.min(maxCaretHeight, availHeight);
    }

    /**
     * Returns the font used by this text field.
     *
     * @return the field font
     */
    public Font getFont() {
        return font;
    }

    /**
     * Disposes resources owned directly by this field.
     *
     * <p>
     * This includes the internal font and the reusable pixel texture used for caret and selection
     * rendering.
     * </p>
     */
    public void dispose() {
        font.dispose();
        uiPixel.dispose();
    }

    /**
     * Creates a 1x1 white texture used for flat colored rectangle rendering.
     *
     * <p>
     * The returned texture is used by the field to draw the caret and selection highlight without
     * requiring immediate-mode OpenGL rectangle rendering.
     * </p>
     *
     * @return a white 1x1 texture
     */
    private static Texture createWhitePixelTexture() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        buffer.put((byte) 255);
        buffer.put((byte) 255);
        buffer.put((byte) 255);
        buffer.put((byte) 255);
        buffer.flip();
        return new Texture(new TextureData(buffer, (short) 1, (short) 1));
    }
}