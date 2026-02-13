package valthorne.ui.elements;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.DrawFunction;
import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.*;
import valthorne.ui.enums.Alignment;
import valthorne.ui.styles.TextFieldStyle;
import valthorne.viewport.Viewport;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

import static org.lwjgl.opengl.GL11.*;

/**
 * A single-line editable text input element with caret, selection, and clipboard support.
 *
 * <p>This {@link TextField} is designed for your fixed-function (legacy OpenGL) UI pipeline and
 * integrates with your {@link Font} system for text measurement and rendering.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *     <li>Single-line text input with caret positioning</li>
 *     <li>Text selection via click-drag and select-all via double-click</li>
 *     <li>Clipboard operations: copy, cut, paste (Ctrl+C / Ctrl+X / Ctrl+V)</li>
 *     <li>Word navigation with Ctrl+Left / Ctrl+Right</li>
 *     <li>Optional masking mode for password entry</li>
 *     <li>Optional character limit</li>
 *     <li>Horizontal scrolling to keep caret visible when text exceeds available width</li>
 *     <li>Scissored drawing region to prevent text overflow</li>
 * </ul>
 *
 * <h2>Input Behavior</h2>
 * <ul>
 *     <li>Typing inserts at the caret (replaces selection if active)</li>
 *     <li>Backspace/Delete remove selection or adjacent characters</li>
 *     <li>Arrow/Home/End move caret (Shift extends selection)</li>
 *     <li>Ctrl+A selects all text</li>
 *     <li>Ctrl+C copies selection, Ctrl+X cuts selection, Ctrl+V pastes clipboard</li>
 *     <li>Enter triggers the configured {@link UIAction}, if provided</li>
 * </ul>
 *
 * <h2>Rendering Notes</h2>
 * <ul>
 *     <li>Background is drawn via {@link Drawable} from {@link TextFieldStyle}</li>
 *     <li>Selection highlight and caret are rendered as GL_QUADS with textures disabled</li>
 *     <li>Text and overlays are clipped using {@link Viewport#applyScissor(float, float, float, float, DrawFunction)}</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TextFieldStyle style = new TextFieldStyle()
 *     .setFontData(myFontData)
 *     .setBackground(myBgDrawable)
 *     .setFocused(myFocusedDrawable);
 *
 * TextField username = new TextField("Username", style, tf -> {
 *     System.out.println("Submitted: " + tf.getText());
 * });
 *
 * username.setPosition(40, 40);
 * username.setSize(320, 48);
 *
 * // Optional:
 * username.setCharacterLimit(24);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 3rd, 2026
 */
public class TextField extends Element {

    private TextFieldStyle style;                 // Visual styling (background/focused drawables, font data, etc.).
    private Drawable current;                     // Currently active background drawable (normal vs focused).
    private Font font;                            // Font renderer used to measure and draw the (display) text.

    private String text = "";                     // Backing text content (unmasked, user-entered).
    private String placeHolder;                   // Placeholder text shown when empty and not focused.
    private UIAction<TextField> action;           // Optional action invoked on Enter (submit/confirm).

    private boolean masking;                      // If true, render text as repeated maskCharacter.
    private char maskCharacter = '*';             // Mask character used when masking is enabled.

    private int cursorIndex = 0;                  // Caret insertion index within 'text' (0..text.length()).

    private int characterLimit;                   // Max allowed characters; <= 0 means "unlimited".

    private int selectionStart = 0;               // Selection anchor index (may be > selectionEnd).
    private int selectionEnd = 0;                 // Selection active index (may be < selectionStart).
    private boolean selecting;                    // True while the user is dragging to select.

    private boolean cursorActive = true;          // Current caret visibility state (blinking toggles this).
    private float cursorBlinkTime;                // Accumulator for caret blink timing.
    private float cursorBlinkInterval = 0.5f;     // Seconds between caret visibility toggles.
    private float maxCaretHeight;                 // Maximum caret height in pixels (derived from font height).

    private float textOffsetX = 0f;               // Horizontal scroll offset applied to keep caret visible.
    private float padding = 10f;                  // Inner padding used for text and hit-testing.

    private float caretWidth = 2f;                // Width of caret quad in pixels.
    private float caretPadY = 8f;                 // Vertical padding used to constrain caret height.

    private float doubleClickTimer = 0f;          // Timer used to detect double click.
    private float doubleClickWindow = 0.25f;      // Seconds allowed between clicks for a double click.
    private boolean pendingClick;                 // True after first click, until timeout or second click.

    private float scissorFudge = 4f;              // Extra padding added to scissor region to avoid edge clipping.
    private DrawFunction draw;                    // Draw callback executed within scissor bounds.

    /**
     * Creates a new single-line TextField.
     *
     * <p>Initialization details:</p>
     * <ul>
     *     <li>Creates a new {@link Font} instance from {@link TextFieldStyle#getFontData()}</li>
     *     <li>Uses the style background as the initial drawable</li>
     *     <li>Marks the element as focusable so it can accept keyboard input</li>
     *     <li>Prepares an internal scissored draw function that renders selection, text, and caret</li>
     * </ul>
     *
     * @param placeHolder placeholder text shown when empty and not focused
     * @param style       visual styling (background/focused drawables and font data)
     * @param action      optional action invoked when Enter is pressed (may be null)
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
        this.draw = () -> {
            if (isFocused() && hasSelection()) drawSelection();

            font.draw();
            drawCaret();
        };
    }

    /**
     * Updates caret blinking and double-click detection timers.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If a click is pending, advances {@link #doubleClickTimer} and clears the pending state
     *     once the window expires.</li>
     *     <li>If not focused, returns early (caret blink only applies to focused fields).</li>
     *     <li>When focused, advances the caret blink timer and toggles {@link #cursorActive}
     *     whenever {@link #cursorBlinkInterval} is reached.</li>
     * </ul>
     *
     * @param delta time in seconds since last update
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

        if (!isFocused()) return;

        cursorBlinkTime += delta;
        if (cursorBlinkTime >= cursorBlinkInterval) {
            cursorBlinkTime = 0f;
            cursorActive = !cursorActive;
        }
    }

    /**
     * Draws the text field background and scissored text region.
     *
     * <p>Draw order:</p>
     * <ol>
     *     <li>Draws the current background ({@link #current}) if non-null</li>
     *     <li>Applies a scissor rectangle to clip text/selection/caret drawing</li>
     *     <li>Within the scissor: draws selection highlight (if any), draws text, draws caret</li>
     * </ol>
     *
     * <p>Scissor region:</p>
     * <ul>
     *     <li>Horizontally clamps to inner area: x + padding .. x + width - padding</li>
     *     <li>Expanded by {@link #scissorFudge} to reduce edge clipping</li>
     * </ul>
     */
    @Override
    public void draw() {
        if (current != null) current.draw(x, y, width, height);

        Viewport viewport = getUI().getViewport();

        float sx = x + padding - scissorFudge * 0.5f;
        float sw = (width - padding * 2) + scissorFudge;

        viewport.applyScissor(sx, y, sw, height, draw);
    }

    /**
     * Draws the selection highlight quad behind the selected character range.
     *
     * <p>This method assumes a selection exists (callers typically guard with {@link #hasSelection()}).</p>
     *
     * <p>Implementation notes:</p>
     * <ul>
     *     <li>Computes selection bounds using min/max of start/end indices</li>
     *     <li>Converts indices into pixel X positions using font width measurement</li>
     *     <li>Draws a translucent quad centered vertically within the field</li>
     *     <li>Temporarily disables texturing while drawing the solid selection rectangle</li>
     * </ul>
     */
    private void drawSelection() {
        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);

        float x1 = font.getX() + getTextWidthUpTo(a);
        float x2 = font.getX() + getTextWidthUpTo(b);

        float caretHeight = getCaretHeight();
        float centerY = y + height * 0.5f;
        float top = centerY - caretHeight * 0.5f;
        float bottom = centerY + caretHeight * 0.5f;

        glDisable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, 0.35f);

        glBegin(GL_QUADS);
        glVertex2f(x1, top);
        glVertex2f(x2, top);
        glVertex2f(x2, bottom);
        glVertex2f(x1, bottom);
        glEnd();

        glEnable(GL_TEXTURE_2D);
    }

    /**
     * Draws the caret (insertion cursor) at the current {@link #cursorIndex}.
     *
     * <p>Visibility rules:</p>
     * <ul>
     *     <li>Only draws if focused</li>
     *     <li>Only draws if {@link #cursorActive} is true (blink state)</li>
     * </ul>
     *
     * <p>Rendering:</p>
     * <ul>
     *     <li>Computes caret X position from font X + measured text width up to cursor index</li>
     *     <li>Draws a solid quad (width {@link #caretWidth}) centered vertically within the field</li>
     *     <li>Disables textures while drawing to ensure solid color</li>
     * </ul>
     */
    private void drawCaret() {
        if (!cursorActive || !isFocused()) return;

        float cx = font.getX() + getCursorX();
        float caretHeight = getCaretHeight();

        float centerY = y + height * 0.5f;
        float top = centerY - caretHeight * 0.5f;
        float bottom = centerY + caretHeight * 0.5f;

        glDisable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, 1f);

        glBegin(GL_QUADS);
        glVertex2f(cx, top);
        glVertex2f(cx + caretWidth, top);
        glVertex2f(cx + caretWidth, bottom);
        glVertex2f(cx, bottom);
        glEnd();

        glEnable(GL_TEXTURE_2D);
    }

    /**
     * Handles keyboard input while the TextField is focused.
     *
     * <p>High-level behavior:</p>
     * <ul>
     *     <li>If not focused, ignores the event</li>
     *     <li>Resets caret blinking to visible on any key press</li>
     *     <li>Supports Ctrl-modified shortcuts for clipboard, selection, and word navigation</li>
     *     <li>Supports standard navigation keys and editing keys</li>
     *     <li>Inserts printable characters via {@link #insertChar(char)}</li>
     * </ul>
     *
     * <p>Ctrl shortcuts:</p>
     * <ul>
     *     <li>Ctrl+V: paste clipboard</li>
     *     <li>Ctrl+C: copy selection</li>
     *     <li>Ctrl+X: cut selection</li>
     *     <li>Ctrl+A: select all</li>
     *     <li>Ctrl+Left / Ctrl+Right: jump by word</li>
     * </ul>
     *
     * <p>Shift behavior:</p>
     * <ul>
     *     <li>If Shift is down, cursor movement extends selection</li>
     *     <li>If Shift is not down, movement clears selection</li>
     * </ul>
     *
     * @param event key press event containing key, character, and modifier state
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        super.onKeyPress(event);
        if (!isFocused()) return;

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
                if (action != null) action.perform(this);
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
     * Handles mouse press for caret placement and selection start.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>Converts screen coordinates to UI world coordinates via the {@link Viewport}</li>
     *     <li>Places caret at the nearest character index under the mouse</li>
     *     <li>Starts selection drag (selectionStart = selectionEnd = caret)</li>
     *     <li>Detects double-click within {@link #doubleClickWindow} and selects all</li>
     * </ul>
     *
     * <p>Double-click implementation:</p>
     * <ul>
     *     <li>First click sets {@link #pendingClick} and resets timer</li>
     *     <li>Second click within time window triggers {@link #selectAll()}</li>
     * </ul>
     *
     * @param event mouse press event with screen coordinates
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        super.onMousePress(event);

        Viewport viewport = getUI().getViewport();
        Vector2f world = viewport.screenToWorld(event.getX(), event.getY());
        if (world == null) return;

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
     * Handles mouse drag to extend selection.
     *
     * <p>While dragging:</p>
     * <ul>
     *     <li>Updates {@link #cursorIndex} to the nearest character under the mouse X</li>
     *     <li>Updates {@link #selectionEnd} to extend or shrink selection</li>
     *     <li>Updates horizontal scroll so caret remains visible</li>
     * </ul>
     *
     * @param event mouse drag event with screen coordinates
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!selecting) return;

        Viewport viewport = getUI().getViewport();
        Vector2f world = viewport.screenToWorld(event.getX(), event.getY());
        if (world == null) return;

        cursorIndex = getIndexAtMouseX(world.getX());
        selectionEnd = cursorIndex;

        updateScroll();
    }

    /**
     * Handles mouse release to end a selection drag.
     *
     * <p>Note: selection remains active after release; only the drag state ends.</p>
     *
     * @param event mouse release event (unused)
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        selecting = false;
    }

    /**
     * @return true if a character limit is enabled (limit > 0)
     */
    private boolean hasCharacterLimit() {
        return characterLimit > 0;
    }

    /**
     * Computes how many more characters may be inserted before hitting the character limit.
     *
     * @return remaining capacity, or {@link Integer#MAX_VALUE} if unlimited
     */
    private int remainingCapacity() {
        if (!hasCharacterLimit()) return Integer.MAX_VALUE;
        return Math.max(0, characterLimit - text.length());
    }

    /**
     * Clamps an input string to fit within the remaining character capacity.
     *
     * <p>This is primarily used during paste operations to ensure the final text does not
     * exceed {@link #characterLimit}.</p>
     *
     * @param s input string (may be null)
     * @return the original string if unlimited, a truncated string if needed, "" if no capacity,
     * or null if input was null
     */
    private String clampToLimit(String s) {
        if (s == null) return null;
        if (!hasCharacterLimit()) return s;

        int cap = remainingCapacity();
        if (cap <= 0) return "";
        if (s.length() <= cap) return s;

        return s.substring(0, cap);
    }

    /**
     * Inserts a printable character at the current caret position.
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>Ignores null/linebreak characters (0, '\n', '\r')</li>
     *     <li>If a selection exists, replaces it by deleting selection first</li>
     *     <li>Enforces {@link #characterLimit} when enabled</li>
     *     <li>Updates caret position and refreshes font text and scroll</li>
     * </ul>
     *
     * @param c character to insert
     */
    private void insertChar(char c) {
        if (c == 0 || c == '\n' || c == '\r') return;

        if (hasSelection()) deleteSelection();

        // Enforce character limit.
        if (hasCharacterLimit() && text.length() >= characterLimit) return;

        text = text.substring(0, cursorIndex) + c + text.substring(cursorIndex);
        cursorIndex++;

        updateFontText();
        updateScroll();
    }

    /**
     * Deletes the character immediately before the caret (Backspace behavior).
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>If a selection exists, deletes the selection instead</li>
     *     <li>If caret is at index 0, nothing happens</li>
     *     <li>Otherwise removes one character to the left and moves caret left by 1</li>
     * </ul>
     */
    private void deleteBackward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (cursorIndex == 0) return;

        text = text.substring(0, cursorIndex - 1) + text.substring(cursorIndex);
        cursorIndex--;

        updateFontText();
        updateScroll();
    }

    /**
     * Deletes the character immediately after the caret (Delete behavior).
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>If a selection exists, deletes the selection instead</li>
     *     <li>If caret is at or beyond end of text, nothing happens</li>
     *     <li>Otherwise removes one character at caret index</li>
     * </ul>
     */
    private void deleteForward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (cursorIndex >= text.length()) return;

        text = text.substring(0, cursorIndex) + text.substring(cursorIndex + 1);

        updateFontText();
        updateScroll();
    }

    /**
     * Deletes the currently selected text range.
     *
     * <p>Implementation details:</p>
     * <ul>
     *     <li>Normalizes selection bounds using min/max</li>
     *     <li>Removes substring [a, b)</li>
     *     <li>Moves caret to the start of the removed range</li>
     *     <li>Clears selection after deletion</li>
     * </ul>
     */
    private void deleteSelection() {
        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);

        text = text.substring(0, a) + text.substring(b);
        cursorIndex = a;
        clearSelection();

        updateFontText();
        updateScroll();
    }

    /**
     * Moves the caret to a new index, optionally extending the selection.
     *
     * <p>Selection behavior:</p>
     * <ul>
     *     <li>If {@code extend} is true, selectionStart is anchored at the old caret position
     *     (if there was no active selection) and selectionEnd follows the new index.</li>
     *     <li>If {@code extend} is false, selection is cleared and caret simply moves.</li>
     * </ul>
     *
     * <p>The provided index is clamped to the valid range [0, text.length()].</p>
     *
     * @param index  target caret index
     * @param extend if true, extend selection; if false, clear selection
     */
    private void moveCursor(int index, boolean extend) {
        index = MathUtils.clamp(index, 0, text.length());

        if (extend) {
            if (!hasSelection()) selectionStart = cursorIndex;
            selectionEnd = index;
        } else {
            clearSelection();
        }

        cursorIndex = index;
        updateScroll();
    }

    /**
     * @return true if selectionStart and selectionEnd differ
     */
    private boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    /**
     * Clears selection by collapsing it to the caret index.
     *
     * <p>After clearing, selectionStart == selectionEnd == cursorIndex.</p>
     */
    private void clearSelection() {
        selectionStart = cursorIndex;
        selectionEnd = cursorIndex;
    }

    /**
     * Selects the entire text contents and moves caret to the end.
     *
     * <p>Also updates horizontal scroll to ensure caret visibility.</p>
     */
    private void selectAll() {
        selectionStart = 0;
        selectionEnd = text.length();
        cursorIndex = selectionEnd;
        updateScroll();
    }

    /**
     * Copies the currently selected range to the system clipboard.
     *
     * <p>No-op if no selection exists.</p>
     */
    private void copySelection() {
        if (!hasSelection()) return;

        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);
        setClipboard(text.substring(a, b));
    }

    /**
     * Cuts the selected range: copies it to clipboard then deletes it.
     *
     * <p>No-op if no selection exists.</p>
     */
    private void cutSelection() {
        copySelection();
        deleteSelection();
    }

    /**
     * Pastes clipboard text at the caret position.
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>No-op if clipboard is empty or unavailable</li>
     *     <li>If a selection exists, it is deleted before insert (paste replaces selection)</li>
     *     <li>Enforces {@link #characterLimit} by clamping pasted content</li>
     *     <li>Updates caret position, font text, and horizontal scroll after insertion</li>
     * </ul>
     */
    private void pasteClipboard() {
        String s = getClipboard();
        if (s == null || s.isEmpty()) return;

        if (hasSelection()) deleteSelection();

        // Enforce character limit.
        s = clampToLimit(s);
        if (s.isEmpty()) {
            updateFontText();
            updateScroll();
            return;
        }

        text = text.substring(0, cursorIndex) + s + text.substring(cursorIndex);
        cursorIndex += s.length();

        updateFontText();
        updateScroll();
    }

    /**
     * Reads plain text from the system clipboard.
     *
     * <p>Returns null if clipboard access fails for any reason.</p>
     *
     * @return clipboard string contents or null
     */
    private String getClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Writes plain text to the system clipboard.
     *
     * @param s string to store in clipboard
     */
    private void setClipboard(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    /**
     * Resets caret blinking so the caret becomes visible immediately.
     *
     * <p>Called on key and mouse interactions to provide typical text-field UX.</p>
     */
    private void resetCursorBlink() {
        cursorBlinkTime = 0f;
        cursorActive = true;
    }

    /**
     * Computes the caret X offset (relative to the font origin) at {@link #cursorIndex}.
     *
     * @return pixel width of display text up to the caret index
     */
    private float getCursorX() {
        return getTextWidthUpTo(cursorIndex);
    }

    /**
     * Measures the pixel width of the display text from index 0 up to {@code index}.
     *
     * <p>Uses {@link #getDisplayText()} so masking is respected for measurement.</p>
     *
     * @param index exclusive character index to measure up to
     * @return width in pixels for substring [0, index)
     */
    private float getTextWidthUpTo(int index) {
        if (index <= 0) return 0;
        return font.getWidth(getDisplayText().substring(0, index));
    }

    /**
     * Returns the string that should be displayed by the font.
     *
     * <p>If masking is enabled, this returns a repeated mask character of equal length to {@link #text}.
     * Otherwise it returns {@link #text} as-is.</p>
     *
     * @return display string (masked or unmasked)
     */
    private String getDisplayText() {
        return masking ? String.valueOf(maskCharacter).repeat(text.length()) : text;
    }

    /**
     * Computes the nearest character index under a given mouse X coordinate.
     *
     * <p>Implementation:</p>
     * <ul>
     *     <li>Converts mouse X into a local coordinate relative to the font origin and padding</li>
     *     <li>Iteratively measures substring widths until width exceeds local X</li>
     *     <li>Returns the first index whose measured width meets/exceeds local X</li>
     * </ul>
     *
     * <p>Note: This is O(n) per query due to repeated substring measurements.</p>
     *
     * @param mouseX mouse X position in world/UI coordinates
     * @return caret index in the range [0, displayText.length()]
     */
    private int getIndexAtMouseX(float mouseX) {
        float local = mouseX - font.getX() - padding;
        if (local <= 0) return 0;

        String d = getDisplayText();
        for (int i = 1; i <= d.length(); i++)
            if (font.getWidth(d.substring(0, i)) >= local) return i;
        return d.length();
    }

    /**
     * Finds the previous "word boundary" index before {@code i}.
     *
     * <p>Definition used:</p>
     * <ul>
     *     <li>Skips whitespace moving left</li>
     *     <li>Then skips contiguous letter-or-digit characters moving left</li>
     * </ul>
     *
     * @param i starting index (typically {@link #cursorIndex})
     * @return new caret index for Ctrl+Left behavior
     */
    private int prevWord(int i) {
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) i--;
        return i;
    }

    /**
     * Finds the next "word boundary" index after {@code i}.
     *
     * <p>Definition used:</p>
     * <ul>
     *     <li>Skips whitespace moving right</li>
     *     <li>Then skips contiguous letter-or-digit characters moving right</li>
     * </ul>
     *
     * @param i starting index (typically {@link #cursorIndex})
     * @return new caret index for Ctrl+Right behavior
     */
    private int nextWord(int i) {
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) i++;
        return i;
    }

    /**
     * Updates the {@link Font} text based on focus and current content.
     *
     * <p>Display rules:</p>
     * <ul>
     *     <li>If {@link #text} is empty and the field is NOT focused, show {@link #placeHolder}</li>
     *     <li>Otherwise show {@link #getDisplayText()} (masked or unmasked)</li>
     * </ul>
     *
     * <p>This method does not reposition the font; call {@link #updateScroll()} or
     * {@link #updateFontPosition()} after text updates when needed.</p>
     */
    private void updateFontText() {
        font.setText(text.isEmpty() && !isFocused() ? placeHolder : getDisplayText());
    }

    /**
     * Ensures the caret remains visible by adjusting horizontal text scrolling.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If available width is non-positive, resets scroll to 0</li>
     *     <li>If the text fits, resets scroll to 0</li>
     *     <li>If text exceeds available width:
     *         <ul>
     *             <li>Computes min/max offset range (min is negative)</li>
     *             <li>Clamps current offset into range</li>
     *             <li>If caret is beyond right edge, shifts left to bring caret to right edge</li>
     *             <li>If caret is before left edge, shifts right to bring caret to left edge</li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * <p>Finally calls {@link #updateFontPosition()} to apply the resulting offset.</p>
     */
    private void updateScroll() {
        float avail = width - padding * 2;

        if (avail <= 0) {
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

        float minOffset = avail - textWidth; // negative
        float maxOffset = 0f;

        textOffsetX = MathUtils.clamp(textOffsetX, minOffset, maxOffset);

        float caretX = cx + textOffsetX;

        if (caretX > avail) {
            textOffsetX = avail - cx; // shift left so caret hits right edge
        } else if (caretX < 0f) {
            textOffsetX = -cx;        // shift right so caret hits left edge
        }

        textOffsetX = MathUtils.clamp(textOffsetX, minOffset, maxOffset);
        updateFontPosition();
    }

    /**
     * Updates the font position based on element alignment, padding, and current scroll offset.
     *
     * <p>Position formula:</p>
     * <ul>
     *     <li>X = alignedX + padding + textOffsetX</li>
     *     <li>Y = alignedY</li>
     * </ul>
     */
    private void updateFontPosition() {
        Vector2f align = Alignment.align(this, font, Alignment.CENTER, Alignment.START);
        font.setPosition(align.getX() + padding + textOffsetX, align.getY());
    }

    /**
     * Sets the element position and immediately updates the font position.
     *
     * <p>This keeps the font aligned to the TextField after layout/movement changes.</p>
     *
     * @param x new x coordinate (world/UI space)
     * @param y new y coordinate (world/UI space)
     */
    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        updateFontPosition();
    }

    /**
     * Sets the element size and immediately updates the font position.
     *
     * <p>This ensures vertical centering and scroll calculations remain visually correct.</p>
     *
     * @param width  new width in pixels
     * @param height new height in pixels
     */
    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        updateFontPosition();
    }

    /**
     * Updates focus state styling and placeholder/display logic.
     *
     * <p>When focus changes:</p>
     * <ul>
     *     <li>Resets caret blink so caret becomes visible immediately</li>
     *     <li>Switches background drawable to focused/normal based on {@code value}</li>
     *     <li>Refreshes font text (placeholder vs actual display)</li>
     *     <li>Recomputes scroll to keep caret valid and visible</li>
     * </ul>
     *
     * @param value true if focused, false otherwise
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
     * @return true if masking mode is enabled
     */
    public boolean isMasking() {
        return masking;
    }

    /**
     * Enables or disables masking mode.
     *
     * <p>When enabled, the display text becomes a repeated {@link #maskCharacter} of the same
     * length as {@link #text}. The backing text remains unchanged.</p>
     *
     * @param masking true to enable masking, false to disable
     */
    public void setMasking(boolean masking) {
        if (this.masking == masking) return;

        this.masking = masking;

        updateFontText();
        updateScroll();
    }

    /**
     * @return the current mask character used for display when masking is enabled
     */
    public char getMaskCharacter() {
        return maskCharacter;
    }

    /**
     * Sets the mask character used when masking is enabled.
     *
     * <p>If masking is currently active, updates the rendered display immediately.</p>
     *
     * @param maskCharacter character used to render masked display text
     */
    public void setMaskCharacter(char maskCharacter) {
        this.maskCharacter = maskCharacter;
        if (masking) {
            updateFontText();
            updateScroll();
        }
    }

    /**
     * Returns the raw backing text content (unmasked).
     *
     * @return current text value
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the raw backing text content.
     *
     * <p>Rules:</p>
     * <ul>
     *     <li>Null is treated as empty string</li>
     *     <li>Applies {@link #characterLimit} if enabled (clamps to max length)</li>
     *     <li>Clamps caret index to the new text range</li>
     *     <li>Clears selection and collapses it to the caret</li>
     *     <li>Refreshes font text and scroll</li>
     * </ul>
     *
     * @param value new text value (null treated as "")
     */
    public void setText(String value) {
        if (value == null) value = "";

        // Enforce character limit.
        if (hasCharacterLimit() && value.length() > characterLimit) {
            value = value.substring(0, characterLimit);
        }

        this.text = value;

        cursorIndex = MathUtils.clamp(cursorIndex, 0, text.length());
        selectionStart = cursorIndex;
        selectionEnd = cursorIndex;

        updateFontText();
        updateScroll();
    }

    /**
     * Sets the maximum character limit for this TextField.
     *
     * <p>Limit rules:</p>
     * <ul>
     *     <li>A value &lt;= 0 means unlimited</li>
     *     <li>If the current text exceeds the new limit, the text is truncated</li>
     *     <li>After truncation, caret/selection are clamped into valid range</li>
     * </ul>
     *
     * @param limit maximum number of characters allowed, or &lt;= 0 for unlimited
     */
    public void setCharacterLimit(int limit) {
        this.characterLimit = limit;

        // If current text exceeds the new limit, clamp it.
        if (hasCharacterLimit() && text.length() > characterLimit) {
            text = text.substring(0, characterLimit);

            cursorIndex = MathUtils.clamp(cursorIndex, 0, text.length());
            selectionStart = cursorIndex;
            selectionEnd = cursorIndex;

            updateFontText();
            updateScroll();
        }
    }

    /**
     * Returns the configured character limit.
     *
     * @return the character limit, or &lt;= 0 if unlimited
     */
    public int getCharacterLimit() {
        return characterLimit;
    }

    /**
     * Computes the caret height used for selection and caret drawing.
     *
     * <p>The caret height is constrained by:</p>
     * <ul>
     *     <li>Available inner height: {@code height - caretPadY * 2}</li>
     *     <li>Maximum caret height: {@link #maxCaretHeight}</li>
     * </ul>
     *
     * @return caret height in pixels
     */
    private float getCaretHeight() {
        float availHeight = height - caretPadY * 2;
        return Math.min(maxCaretHeight, availHeight);
    }

}