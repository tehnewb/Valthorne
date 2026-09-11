package valthorne.ui.nodes.nano;

import org.lwjgl.nanovg.NVGColor;
import valthorne.Keyboard;
import valthorne.event.events.*;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import org.joml.Vector2f;
import valthorne.ui.NanoUtility;
import valthorne.ui.NodeAction;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Single-line NanoVG editor backed by the shared TextEditModel for sanitized text,
 * grapheme-aware movement, selection, undo/redo, and validation. Renderer fields
 * are synchronized snapshots of that model. Enter invokes a local or theme action
 * only when validation succeeds; ordinary editing does not invoke the submit action.
 *
 * <p>Masking changes displayed characters and suppresses clipboard export through
 * shared shortcuts; raw text remains available from getText and getEditor. One
 * mask character is emitted per UTF-16 code unit, not per grapheme. Pointer presses
 * within the double-click interval select all, without a spatial proximity test.</p>
 *
 * <p>Attach to a root before operations requiring caret measurement: substring
 * measurement and styled auto layout dereference the root. The root owns NanoVG
 * fonts and frame state. Colors are borrowed and may be replaced by theme layout.
 * Auto dimensions are replaced with measured numbers, so later text changes do
 * not automatically restore content sizing.</p>
 *
 * <pre>{@code
 * NanoTextField field = new NanoTextField("Name");
 * field.getLayout().width(240).height(40);
 * parent.add(field); // Attach before caret/text operations requiring measurement.
 * field.text("Albert");
 * field.action(value -> System.out.println(value.getText()));
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class NanoTextField extends UINode implements NanoNode {

    /**
     * Theme background color used by text-field layout and painting.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.textfield.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme hover background color used by text-field layout and painting.
     */
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.textfield.hoverBackgroundColor", Color.class, new Color(0xFF323232));
    /**
     * Theme focused background color used by text-field layout and painting.
     */
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.textfield.focusedBackgroundColor", Color.class, new Color(0xFF3A3A3A));
    /**
     * Theme border color used by text-field layout and painting.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.textfield.borderColor", Color.class, new Color(0xFF555555));
    /**
     * Theme hover border color used by text-field layout and painting.
     */
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.textfield.hoverBorderColor", Color.class, new Color(0xFF777777));
    /**
     * Theme focused border color used by text-field layout and painting.
     */
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.textfield.focusedBorderColor", Color.class, new Color(0xFF7AA2FF));
    /**
     * Theme text color used by text-field layout and painting.
     */
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.textfield.textColor", Color.class, new Color(0xFFFFFFFF));
    /**
     * Theme placeholder color used by text-field layout and painting.
     */
    public static final StyleKey<Color> PLACEHOLDER_COLOR_KEY = StyleKey.of("nano.textfield.placeholderColor", Color.class, new Color(0xFFAAAAAA));
    /**
     * Theme caret color used by text-field layout and painting.
     */
    public static final StyleKey<Color> CARET_COLOR_KEY = StyleKey.of("nano.textfield.caretColor", Color.class, new Color(0xFFFFFFFF));
    /**
     * Theme selection color used by text-field layout and painting.
     */
    public static final StyleKey<Color> SELECTION_COLOR_KEY = StyleKey.of("nano.textfield.selectionColor", Color.class, new Color(0xFF66A3FF));
    /**
     * Theme default selection color used by text-field layout and painting.
     */
    public static final StyleKey<Color> DEFAULT_SELECTION_COLOR_KEY = StyleKey.of("nano.textfield.defaultSelectionColor", Color.class, new Color(0xFF66A3FF));
    /**
     * Theme font name used by text-field layout and painting.
     */
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.textfield.fontName", String.class, "default");
    /**
     * Theme font size used by text-field layout and painting.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.textfield.fontSize", Float.class, 18f);
    /**
     * Theme padding used by text-field layout and painting.
     */
    public static final StyleKey<Float> PADDING_KEY = StyleKey.of("nano.textfield.padding", Float.class, 10f);
    /**
     * Theme corner radius used by text-field layout and painting.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.textfield.cornerRadius", Float.class, 6f);
    /**
     * Theme border width used by text-field layout and painting.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.textfield.borderWidth", Float.class, 1f);
    /**
     * Theme caret width used by text-field layout and painting.
     */
    public static final StyleKey<Float> CARET_WIDTH_KEY = StyleKey.of("nano.textfield.caretWidth", Float.class, 2f);
    /**
     * Theme caret padding y used by text-field layout and painting.
     */
    public static final StyleKey<Float> CARET_PADDING_Y_KEY = StyleKey.of("nano.textfield.caretPaddingY", Float.class, 8f);
    /**
     * Theme scissor fudge used by text-field layout and painting.
     */
    public static final StyleKey<Float> SCISSOR_FUDGE_KEY = StyleKey.of("nano.textfield.scissorFudge", Float.class, 4f);
    /**
     * Theme valid-Enter callback used when no local action is assigned.
     */
    public static final StyleKey<NodeAction<NanoTextField>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoTextField>>) (Class<?>) NodeAction.class);

    private final valthorne.ui.behavior.TextEditModel editor = new valthorne.ui.behavior.TextEditModel(); // Owned editing, validation, selection, and undo state.
    // Renderer snapshot; all mutations are owned by the shared editor.
    private String text = ""; // Synchronized raw editor text; masking does not alter this snapshot.
    private String placeholder = ""; // Sanitized hint drawn only for empty display text.
    private String displayText = ""; // Cached masked text, one character per UTF-16 unit.
    private boolean displayTextDirty = true; // Whether masked display text needs rebuilding.

    private int caretIndex; // Synchronized active caret UTF-16 offset.
    private int selectionStart; // Synchronized editor anchor UTF-16 offset.
    private int selectionEnd; // Synchronized editor caret UTF-16 offset.
    private boolean selecting; // Whether pointer drags extend the selection.

    private boolean masking; // Whether painting substitutes mask characters and shortcuts block export.
    private char maskChar = '*'; // Unvalidated display character repeated for masked text.

    private boolean caretVisible = true; // Current blink phase used when focused.
    private float blinkTime; // Elapsed seconds since the last blink reset or toggle.
    private float blinkInterval = 0.5f; // Seconds between caret visibility toggles.

    private float padding = 10f; // Content inset on each side in UI units.
    private float cornerRadius = 6f; // Rounded-box radius in UI units.
    private float borderWidth = 1f; // Border stroke width in UI units.
    private float caretWidth = 2f; // Requested caret stroke width, drawn at least one UI unit wide.
    private float caretPadY = 8f; // Vertical inset for caret and selection geometry.
    private float scissorFudge = 4f; // Extra horizontal text-clip width beyond padded content bounds.
    private float textOffsetX; // Nonpositive horizontal text translation keeping the caret visible.

    private float doubleClickTimer; // Elapsed seconds since the pending initial click.
    private float doubleClickWindow = 0.25f; // Maximum interval recognizing a second click as select-all.
    private boolean pendingClick; // Whether an initial click is waiting for a second click.

    private Color backgroundColor = new Color(0xFF2A2A2A); // Borrowed background color, replaceable by resolved styles.
    private Color hoverBackgroundColor = new Color(0xFF323232); // Borrowed hover background color, replaceable by resolved styles.
    private Color focusedBackgroundColor = new Color(0xFF3A3A3A); // Borrowed focused background color, replaceable by resolved styles.
    private Color borderColor = new Color(0xFF555555); // Borrowed border color, replaceable by resolved styles.
    private Color hoverBorderColor = new Color(0xFF777777); // Borrowed hover border color, replaceable by resolved styles.
    private Color focusedBorderColor = new Color(0xFF7AA2FF); // Borrowed focused border color, replaceable by resolved styles.
    private Color textColor = new Color(0xFFFFFFFF); // Borrowed text color, replaceable by resolved styles.
    private Color placeholderColor = new Color(0xFFAAAAAA); // Borrowed placeholder color, replaceable by resolved styles.
    private Color caretColor = new Color(0xFFFFFFFF); // Borrowed caret color, replaceable by resolved styles.
    private Color selectionColor = new Color(0xFF66A3FF); // Borrowed selection color, replaceable by resolved styles.
    private Color defaultSelectionColor = new Color(0xFF66A3FF); // Borrowed default selection color, replaceable by resolved styles.

    private String fontName = "default"; // Borrowed NanoVG font registration name.
    private float fontSize = 18f; // Font size in UI units.
    private boolean fontLoaded; // Reserved load flag reset on font changes; no font loading occurs here.

    private NodeAction<NanoTextField> action; // Optional local callback for valid Enter submission.

    /**
     * Creates an empty clickable, focusable field and subscribes its renderer to the
     * owned editor's synchronous change signal. Marks layout dirty for initial sizing.
     */
    public NanoTextField() {
        editor.onChange(this::syncEditor);
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        markLayoutDirty();
    }

    /**
     * Creates an empty field with a sanitized placeholder. The placeholder is paint
     * metadata and does not become editable text.
     *
     * @param placeholder hint text; null becomes empty
     */
    public NanoTextField(String placeholder) {
        this();
        placeholder(placeholder);
    }

    /**
     * Returns the latest raw sanitized editor text, including when masking is enabled.
     *
     * @return non-null text snapshot
     */
    public String getText() {
        return text;
    }

    /**
     * Replaces text through the editor's programmatic assignment path: sanitizes,
     * clamps the caret to a valid boundary, collapses selection, clears history, and
     * notifies renderer synchronization. Does not enforce insertion-length limits
     * or invoke the submit action. Attach before nontrivial caret measurement.
     *
     * @param text replacement text, possibly null
     * @return this field
     */
    public NanoTextField text(String text) {
        editor.text(text);
        return this;
    }

    /**
     * Reads the sanitized hint displayed when actual display text is empty.
     *
     * @return non-null placeholder
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Sanitizes hint text through the shared single-line sanitizer and dirties layout.
     * Does not alter editable contents, selection, or undo history.
     *
     * @param placeholder replacement hint, possibly null
     * @return this field
     */
    public NanoTextField placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : sanitize(placeholder);
        markLayoutDirty();
        return this;
    }

    /**
     * Reads the active caret's UTF-16 offset from the synchronized editor snapshot.
     *
     * @return caret offset in raw text, aligned to an editor boundary
     */
    public int getCaretIndex() {
        return caretIndex;
    }

    /**
     * Moves to a clamped editor boundary and collapses selection, synchronizing
     * blink and scrolling through the editor listener. The submit action is not fired.
     *
     * @param caretIndex requested UTF-16 offset
     * @return this field
     */
    public NanoTextField caretIndex(int caretIndex) {
        editor.move(caretIndex, false);
        return this;
    }

    /**
     * Reads whether display substitution and clipboard-export suppression are enabled.
     * Raw text remains accessible through the public editor/text APIs.
     *
     * @return current masking flag
     */
    public boolean isMasking() {
        return masking;
    }

    /**
     * Changes display substitution, invalidates its cache and layout, and updates
     * caret scrolling. Repeating the current flag is a no-op; raw text is unchanged.
     *
     * @param masking true to display mask characters and suppress shortcut export
     * @return this field
     */
    public NanoTextField masking(boolean masking) {
        if (this.masking == masking)
            return this;
        this.masking = masking;
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    /**
     * Reads the single UTF-16 character repeated for masked display.
     *
     * @return configured mask character
     */
    public char getMaskChar() {
        return maskChar;
    }

    /**
     * Changes the unvalidated mask character and refreshes display/layout/scrolling.
     * Even control or surrogate characters are accepted; choose a renderable glyph.
     * Repeating the current value is a no-op.
     *
     * @param maskChar character repeated once per UTF-16 unit
     * @return this field
     */
    public NanoTextField maskChar(char maskChar) {
        if (this.maskChar == maskChar)
            return this;
        this.maskChar = maskChar;
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    /**
     * Reads only the local Enter-submit callback, without resolving theme fallback.
     *
     * @return local action, or null
     */
    public NodeAction<NanoTextField> getAction() {
        return action;
    }

    /**
     * Replaces the synchronous valid-Enter callback. Null allows theme fallback;
     * assignment and ordinary text edits do not invoke this action.
     *
     * @param action local submit callback, or null
     * @return this field
     */
    public NanoTextField action(NodeAction<NanoTextField> action) {
        this.action = action;
        return this;
    }

    /**
     * Retains a nonblank registered font name, resets the reserved font-loaded flag,
     * and dirties layout even if the argument is null or blank. Does not load a font.
     *
     * @param fontName NanoVG registration name
     * @return this field
     */
    public NanoTextField fontName(String fontName) {
        if (fontName != null && !fontName.isBlank())
            this.fontName = fontName;
        fontLoaded = false;
        markLayoutDirty();
        return this;
    }

    /**
     * Sets font size with a minimum of 1.
     * Marks layout dirty without restoring numeric dimensions to auto.
     *
     * @param fontSize finite requested value in UI units
     * @return this field
     */
    public NanoTextField fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        markLayoutDirty();
        return this;
    }

    /**
     * Sets content inset on each side with a minimum of 0.
     * Marks layout dirty without restoring numeric dimensions to auto.
     *
     * @param padding finite requested value in UI units
     * @return this field
     */
    public NanoTextField padding(float padding) {
        this.padding = Math.max(0f, padding);
        markLayoutDirty();
        return this;
    }

    /**
     * Sets rounded-box radius with a minimum of 0.
     * Changes painting without dirtying layout.
     *
     * @param cornerRadius finite requested value in UI units
     * @return this field
     */
    public NanoTextField cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    /**
     * Sets border stroke width with a minimum of 0.
     * Changes painting without dirtying layout.
     *
     * @param borderWidth finite requested value in UI units
     * @return this field
     */
    public NanoTextField borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    /**
     * Retains a non-null background color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param backgroundColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField backgroundColor(Color backgroundColor) {
        if (backgroundColor != null)
            this.backgroundColor = backgroundColor;
        return this;
    }

    /**
     * Retains a non-null hover background color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param hoverBackgroundColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField hoverBackgroundColor(Color hoverBackgroundColor) {
        if (hoverBackgroundColor != null)
            this.hoverBackgroundColor = hoverBackgroundColor;
        return this;
    }

    /**
     * Retains a non-null focused background color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param focusedBackgroundColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField focusedBackgroundColor(Color focusedBackgroundColor) {
        if (focusedBackgroundColor != null)
            this.focusedBackgroundColor = focusedBackgroundColor;
        return this;
    }

    /**
     * Retains a non-null border color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param borderColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField borderColor(Color borderColor) {
        if (borderColor != null)
            this.borderColor = borderColor;
        return this;
    }

    /**
     * Retains a non-null hover border color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param hoverBorderColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField hoverBorderColor(Color hoverBorderColor) {
        if (hoverBorderColor != null)
            this.hoverBorderColor = hoverBorderColor;
        return this;
    }

    /**
     * Retains a non-null focused border color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param focusedBorderColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField focusedBorderColor(Color focusedBorderColor) {
        if (focusedBorderColor != null)
            this.focusedBorderColor = focusedBorderColor;
        return this;
    }

    /**
     * Retains a non-null text color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param textColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField textColor(Color textColor) {
        if (textColor != null)
            this.textColor = textColor;
        return this;
    }

    /**
     * Retains a non-null placeholder color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param placeholderColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField placeholderColor(Color placeholderColor) {
        if (placeholderColor != null)
            this.placeholderColor = placeholderColor;
        return this;
    }

    /**
     * Retains a non-null caret color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param caretColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField caretColor(Color caretColor) {
        if (caretColor != null)
            this.caretColor = caretColor;
        return this;
    }

    /**
     * Retains a non-null selection color without copying it. Null keeps the current
     * reference; resolved style application may overwrite it.
     *
     * @param selectionColor mutable paint color, or null
     * @return this field
     */
    public NanoTextField selectionColor(Color selectionColor) {
        if (selectionColor != null)
            this.selectionColor = selectionColor;
        return this;
    }

    /**
     * Advances the double-click window and focused caret blink. Unfocused fields hide
     * the caret and reset blink time. At most one blink toggle occurs per update and
     * excess interval time is discarded; supply nonnegative finite elapsed time.
     *
     * @param delta elapsed seconds
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
     * Enters root-managed mixed-renderer dispatch for the NanoVG painting callback.
     *
     * @param batch active texture batch used by UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Delegates ordinary hover/movement behavior to the base node without editing text.
     *
     * @param event routed pointer movement
     */
    @Override
    public void onMouseMove(MouseMoveEvent event) {
        super.onMouseMove(event);
    }

    /**
     * Handles left presses as caret placement or Shift-extended selection. A second
     * press within the timing window selects all regardless of click distance.
     * Starts selection dragging for ordinary presses and resets blink/scrolling.
     * Enabled/focus routing is supplied by the root rather than checked here.
     *
     * @param event routed pointer press in screen coordinates
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (event.getButton() != valthorne.Mouse.LEFT) return;
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
        editor.move(getIndexAtMouseX(mouseX), event.isShiftDown());

        resetCursorBlink();
        updateScroll();
    }

    /**
     * Extends selection to the pointer-derived editor boundary only while selecting.
     * Does not independently filter buttons; resets caret blink and keeps it visible
     * through horizontal scrolling.
     *
     * @param event routed pointer drag
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!selecting)
            return;

        float mouseX = getEventX(event);
        editor.move(getIndexAtMouseX(mouseX), true);

        resetCursorBlink();
        updateScroll();
    }

    /**
     * Stops pointer selection for any delivered release without consuming the event
     * or changing selected text.
     *
     * @param event routed pointer release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        selecting = false;
    }

    /**
     * Processes shared editing shortcuts only while focused and enabled, consuming
     * recognized commands even when no change occurs. Otherwise Enter checks current
     * validation, invokes local/theme submit action when valid, and is consumed even
     * when invalid or no action exists. Callback errors propagate synchronously.
     *
     * @param event routed key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (!isFocused() || isDisabled()) return;
        if (valthorne.ui.behavior.TextEditing.key(editor, event, masking)) {
            event.consume();
        } else if (event.getKey() == Keyboard.ENTER) {
            if (editor.isValid()) {
                NodeAction<NanoTextField> resolved = action;
                if (resolved == null && getStyle() != null) resolved = getStyle().get(ACTION_KEY);
                if (resolved != null) resolved.perform(this);
            }
            event.consume();
        }
    }

    /**
     * Applies non-null theme paint/font settings and measures content/placeholder
     * when a style exists. Replaces only dimensions still auto, then delegates layout
     * and scrolls the caret into view. Styled measurement requires root attachment;
     * caret padding and scissor expansion are retained without nonnegative clamping.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Color resolvedBackgroundColor = style.get(BACKGROUND_COLOR_KEY);
            Color resolvedHoverBackgroundColor = style.get(HOVER_BACKGROUND_COLOR_KEY);
            Color resolvedFocusedBackgroundColor = style.get(FOCUSED_BACKGROUND_COLOR_KEY);
            Color resolvedBorderColor = style.get(BORDER_COLOR_KEY);
            Color resolvedHoverBorderColor = style.get(HOVER_BORDER_COLOR_KEY);
            Color resolvedFocusedBorderColor = style.get(FOCUSED_BORDER_COLOR_KEY);
            Color resolvedTextColor = style.get(TEXT_COLOR_KEY);
            Color resolvedPlaceholderColor = style.get(PLACEHOLDER_COLOR_KEY);
            Color resolvedCaretColor = style.get(CARET_COLOR_KEY);
            Color resolvedSelectionColor = style.get(SELECTION_COLOR_KEY);
            Color resolvedDefaultSelectionColor = style.get(DEFAULT_SELECTION_COLOR_KEY);
            String resolvedFontName = style.get(FONT_NAME_KEY);
            Float resolvedFontSize = style.get(FONT_SIZE_KEY);
            Float resolvedPadding = style.get(PADDING_KEY);
            Float resolvedCornerRadius = style.get(CORNER_RADIUS_KEY);
            Float resolvedBorderWidth = style.get(BORDER_WIDTH_KEY);
            Float resolvedCaretWidth = style.get(CARET_WIDTH_KEY);
            Float resolvedCaretPadY = style.get(CARET_PADDING_Y_KEY);
            Float resolvedScissorFudge = style.get(SCISSOR_FUDGE_KEY);

            if (resolvedBackgroundColor != null)
                backgroundColor = resolvedBackgroundColor;
            if (resolvedHoverBackgroundColor != null)
                hoverBackgroundColor = resolvedHoverBackgroundColor;
            if (resolvedFocusedBackgroundColor != null)
                focusedBackgroundColor = resolvedFocusedBackgroundColor;
            if (resolvedBorderColor != null)
                borderColor = resolvedBorderColor;
            if (resolvedHoverBorderColor != null)
                hoverBorderColor = resolvedHoverBorderColor;
            if (resolvedFocusedBorderColor != null)
                focusedBorderColor = resolvedFocusedBorderColor;
            if (resolvedTextColor != null)
                textColor = resolvedTextColor;
            if (resolvedPlaceholderColor != null)
                placeholderColor = resolvedPlaceholderColor;
            if (resolvedCaretColor != null)
                caretColor = resolvedCaretColor;
            if (resolvedSelectionColor != null)
                selectionColor = resolvedSelectionColor;
            if (resolvedDefaultSelectionColor != null)
                defaultSelectionColor = resolvedDefaultSelectionColor;
            if (resolvedFontName != null && !resolvedFontName.isBlank()) {
                if (!resolvedFontName.equals(fontName))
                    fontLoaded = false;
                fontName = resolvedFontName;
            }
            if (resolvedFontSize != null)
                fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedPadding != null)
                padding = Math.max(0f, resolvedPadding);
            if (resolvedCornerRadius != null)
                cornerRadius = Math.max(0f, resolvedCornerRadius);
            if (resolvedBorderWidth != null)
                borderWidth = Math.max(0f, resolvedBorderWidth);
            if (resolvedCaretWidth != null)
                caretWidth = Math.max(0f, resolvedCaretWidth);
            if (resolvedCaretPadY != null)
                caretPadY = resolvedCaretPadY;
            if (resolvedScissorFudge != null)
                scissorFudge = resolvedScissorFudge;

            String measured = getDisplayText();
            String fallback = placeholder == null ? "" : placeholder;
            float measuredWidth = Math.max(measureTextWidth(measured), measureTextWidth(fallback)) + padding * 2f;
            float measuredHeight = NanoUtility.measureTextHeight(this.getRoot().getNanoVGHandle(), fontName, fontSize) + padding * 2f;

            if (getLayout().getWidth().isAuto())
                getLayout().width(measuredWidth);

            if (getLayout().getHeight().isAuto())
                getLayout().height(measuredHeight);
        }

        super.applyLayout();
        updateScroll();
    }

    /**
     * Paints state background/border and clipped text or placeholder, followed by
     * focused selection and blinking caret. Focus colors precede hover colors.
     * Masking affects measurements and display, not stored text. Saves/restores local
     * NanoVG scissor state on normal completion; the root owns enclosing frame state.
     *
     * @param vg borrowed active NanoVG context, or zero to skip
     */
    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L)
            return;


        String visibleText = getDisplayText();
        boolean empty = visibleText.isEmpty();
        String drawText = empty ? placeholder : visibleText;

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();

        Color drawBackgroundColor = backgroundColor;
        Color drawBorderColor = borderColor;

        if (isFocused()) {
            drawBackgroundColor = focusedBackgroundColor;
            drawBorderColor = focusedBorderColor;
        } else if (isHovered()) {
            drawBackgroundColor = hoverBackgroundColor;
            drawBorderColor = hoverBorderColor;
        }

        NVGColor bg = NanoUtility.color1(drawBackgroundColor);
        NVGColor border = NanoUtility.color2(drawBorderColor);
        NVGColor fg = NanoUtility.color3(empty ? placeholderColor : textColor);
        NVGColor aux = NanoUtility.color4(selectionColor != null ? selectionColor : defaultSelectionColor);

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFillColor(vg, bg);
        nvgFill(vg);

        if (borderWidth > 0f) {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x + borderWidth * 0.5f, y + borderWidth * 0.5f, width - borderWidth, height - borderWidth, cornerRadius);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, border);
            nvgStroke(vg);
        }

        nvgSave(vg);
        nvgIntersectScissor(vg, x + padding - scissorFudge * 0.5f, y, Math.max(0f, width - padding * 2f + scissorFudge), height);
        nvgFontSize(vg, fontSize);
        nvgFontFace(vg, fontName);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

        float drawX = x + padding + textOffsetX;
        float drawY = y + height * 0.5f;

        if (isFocused() && hasSelection()) {
            int a = Math.min(selectionStart, selectionEnd);
            int b = Math.max(selectionStart, selectionEnd);

            float x1 = drawX + measureTextWidth(visibleText, 0, a);
            float x2 = drawX + measureTextWidth(visibleText, 0, b);
            float selectionH = Math.max(0f, height - caretPadY * 2f);
            float selectionY = y + (height - selectionH) * 0.5f;

            nvgBeginPath(vg);
            nvgRect(vg, x1, selectionY, Math.max(0f, x2 - x1), selectionH);
            nvgFillColor(vg, aux);
            nvgFill(vg);
        }

        nvgFillColor(vg, fg);
        nvgText(vg, drawX, drawY, drawText);

        if (isFocused() && caretVisible) {
            float caretX = drawX + measureTextWidth(visibleText, 0, Math.min(caretIndex, visibleText.length()));
            float caretH = Math.max(0f, height - caretPadY * 2f);
            float caretY1 = y + (height - caretH) * 0.5f;
            float caretY2 = caretY1 + caretH;

            nvgBeginPath(vg);
            nvgMoveTo(vg, caretX, caretY1);
            nvgLineTo(vg, caretX, caretY2);
            nvgStrokeWidth(vg, Math.max(1f, caretWidth));
            nvgStrokeColor(vg, NanoUtility.toNano(caretColor, aux));
            nvgStroke(vg);
        }

        nvgRestore(vg);
    }

    /**
     * Applies base focus state, then resets blink and scrolling on an actual change.
     * Losing focus also stops selection dragging; update subsequently hides the caret.
     *
     * @param focused requested focus state
     */
    @Override
    public void setFocused(boolean focused) {
        boolean previous = isFocused();
        super.setFocused(focused);

        if (previous == focused)
            return;

        resetCursorBlink();
        if (!focused)
            selecting = false;

        updateScroll();
    }

    /**
     * Allocates no native resources; the owned editor subscription is installed by
     * construction and fonts/context belong to the root/application.
     */
    @Override
    public void onCreate() {

    }

    /**
     * Performs no explicit disposal. The editor and its internal callback share this
     * field's lifetime; native NanoVG resources are borrowed.
     */
    @Override
    public void onDestroy() {
    }

    /**
     * Converts a press position through the inherited content-space transform.
     *
     * @param event pointer press with screen coordinates
     * @return content-space X used for caret hit testing
     */
    private float getEventX(MousePressEvent event) {
        return getEventPosition(event.getX(), event.getY()).x();
    }

    /**
     * Converts the drag endpoint through the inherited content-space transform.
     *
     * @param event drag with screen endpoint coordinates
     * @return content-space X used for selection hit testing
     */
    private float getEventX(MouseDragEvent event) {
        return getEventPosition(event.getToX(), event.getToY()).x();
    }

    /**
     * Delegates screen-to-content conversion, accounting for the root and inherited
     * scroll/content transforms. The returned vector is used immediately by callers.
     *
     * @param screenX screen-space pointer X
     * @param screenY screen-space pointer Y
     * @return converted content-space position
     */
    private Vector2f getEventPosition(float screenX, float screenY) {
        return screenToContent(screenX, screenY);
    }

    /**
     * Restarts blink timing and makes the caret immediately visible. Focus state
     * is checked by drawing/update rather than by this helper.
     */
    private void resetCursorBlink() {
        blinkTime = 0f;
        caretVisible = true;
    }

    /**
     * Exposes the live owned editing/validation/history model. Changes synchronously
     * update renderer snapshots, blink state, and scrolling through its listener.
     * Raw text remains accessible even for a masked field.
     *
     * @return owned mutable editor
     */
    public valthorne.ui.behavior.TextEditModel getEditor() {return editor;}

    /**
     * Copies current text, caret, and anchor into renderer state after any editor
     * notification. Text changes invalidate display/layout; all notifications reset
     * blink and recompute caret scrolling. Does not invoke the submit action.
     */
    private void syncEditor() {
        boolean changed = !text.equals(editor.text());
        text = editor.text();
        caretIndex = editor.caret();
        selectionStart = editor.anchor();
        selectionEnd = editor.caret();
        resetCursorBlink();
        if (changed) {
            markDisplayTextDirty();
            markLayoutDirty();
        }
        updateScroll();
    }

    /**
     * Inserts routed text through the editor only while focused and enabled, then
     * consumes the event even if input constraints prevent a change. The editor owns
     * sanitization, selection replacement, length limits, and undo behavior.
     *
     * @param event committed text-input event
     */
    @Override
    public void onTextInput(valthorne.event.events.TextInputEvent event) {
        if (isFocused() && isEnabled()) {
            editor.insert(event.getText());
            event.consume();
        }
    }

    /**
     * Runs base cancellation and clears selection dragging and pending double-click
     * recognition without changing the editor's existing selection.
     */
    @Override
    public void onPointerCancel() {
        super.onPointerCancel();
        selecting = false;
        pendingClick = false;
    }

    /**
     * Reads whether the editor's anchor and caret form a nonempty selection.
     *
     * @return true for selected text
     */
    private boolean hasSelection() {
        return editor.hasSelection();
    }

    /**
     * Collapses selection at the current caret through normal editor movement and
     * notification, preserving the text and undo history.
     */
    private void clearSelection() {
        editor.move(editor.caret(), false);
    }

    /**
     * Selects the entire raw text through the editor, allowing its change listener
     * to refresh caret, blink, and scrolling.
     */
    private void selectAll() {
        editor.selectAll();
    }

    /**
     * Moves through the editor's boundary clamping and synchronous notification path.
     *
     * @param index requested UTF-16 offset
     * @param extend true to preserve the anchor, false to collapse selection
     */
    private void moveCursor(int index, boolean extend) {
        editor.move(index, extend);
    }

    /**
     * Deletes selected text or the preceding grapheme through the editor's normal
     * history-aware edit path. This wrapper does not perform word deletion.
     */
    private void deleteBackward() {
        editor.deleteBackward(false);
    }

    /**
     * Deletes selected text or the following grapheme through the editor's normal
     * history-aware edit path. This wrapper does not perform word deletion.
     */
    private void deleteForward() {
        editor.deleteForward(false);
    }

    /**
     * Deletes a nonempty selection through the editor's insertion/history machinery.
     * A collapsed selection is a no-op.
     */
    private void deleteSelection() {
        editor.deleteSelection();
    }

    /**
     * Attempts synchronous clipboard export through the shared adapter. Masking and
     * empty selections suppress export; common clipboard access failures are ignored.
     */
    private void copySelection() {
        valthorne.ui.behavior.TextEditing.copy(editor, masking);
    }

    /**
     * Deletes the selection only after successful clipboard export. Masking or an
     * unavailable clipboard leaves selected text unchanged.
     */
    private void cutSelection() {
        if (valthorne.ui.behavior.TextEditing.copy(editor, masking)) editor.deleteSelection();
    }

    /**
     * Reads supported clipboard text through the shared adapter and inserts it with
     * the editor's normal constraints. Common access failures leave editing usable.
     */
    private void pasteClipboard() {
        valthorne.ui.behavior.TextEditing.paste(editor);
    }

    /**
     * Returns raw text when unmasked/empty; otherwise lazily caches one mask character
     * per UTF-16 unit. The cache is invalidated by text or mask-setting changes.
     *
     * @return text used for rendering and caret-width measurement
     */
    private String getDisplayText() {
        if (!masking || text.isEmpty())
            return text;

        if (displayTextDirty) {
            displayText = String.valueOf(maskChar).repeat(text.length());
            displayTextDirty = false;
        }

        return displayText;
    }

    /**
     * Invalidates only the masked-display cache. Layout and scrolling updates are
     * requested separately by the caller.
     */
    private void markDisplayTextDirty() {
        displayTextDirty = true;
    }

    /**
     * Maps content-space X to the first grapheme boundary whose measured prefix
     * reaches the pointer. Uses right-boundary selection rather than nearest-glyph
     * midpoint selection; positions before/after the text clamp to its endpoints.
     *
     * @param mouseX pointer X in the content coordinate space
     * @return raw UTF-16 editor boundary
     */
    private int getIndexAtMouseX(float mouseX) {
        String visibleText = getDisplayText();
        float local = mouseX - (getAbsoluteX() + padding + textOffsetX);
        if (local <= 0f)
            return 0;

        int length = visibleText.length();
        for (int i = editor.next(0); i <= length && i > 0; i = i == length ? length + 1 : editor.next(i)) {
            if (measureTextWidth(visibleText, 0, i) >= local)
                return i;
        }

        return length;
    }

    /**
     * Delegates previous word-segment navigation to the editor without moving the caret.
     * Segmentation is grapheme-based and uses character classes, not locale analysis.
     *
     * @param index starting UTF-16 offset
     * @return preceding word boundary
     */
    private int prevWord(int index) {
        return editor.previousWord(index);
    }

    /**
     * Delegates next word-segment navigation to the editor without editing state.
     *
     * @param index starting UTF-16 offset
     * @return following word boundary
     */
    private int nextWord(int index) {
        return editor.nextWord(index);
    }

    /**
     * Keeps the measured caret inside the padded horizontal viewport, clamping the
     * text offset between the full-text left limit and zero. Resets offset if content
     * fits or available width is nonpositive. Substring measurement requires a root
     * when that path is reached.
     */
    private void updateScroll() {
        float available = getWidth() - padding * 2f;
        if (available <= 0f) {
            textOffsetX = 0f;
            return;
        }

        String visibleText = getDisplayText();
        float textWidth = measureTextWidth(visibleText);
        float caretX = measureTextWidth(visibleText, 0, Math.min(caretIndex, visibleText.length()));

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
     * Measures full text only with a nonzero root NanoVG handle. Unlike NanoUtility's
     * fallback, this overload returns zero when detached or the handle is zero.
     *
     * @param value text to measure
     * @return glyph-bounds width, or zero without a usable handle
     */
    private float measureTextWidth(String value) {
        long vg = getRoot() == null ? 0L : getRoot().getNanoVGHandle();
        if (vg == 0L) return 0f;
        return NanoUtility.measureTextWidth(vg, fontName, fontSize, value);
    }

    /**
     * Measures a clamped UTF-16 substring through NanoUtility. Requires a root; a
     * zero handle then uses the utility's half-font-size-per-unit estimate.
     *
     * @param value source text
     * @param start inclusive requested UTF-16 index
     * @param end exclusive requested UTF-16 index
     * @return measured or estimated substring width
     * @throws NullPointerException if the field is detached
     */
    private float measureTextWidth(String value, int start, int end) {
        long vg = this.getRoot().getNanoVGHandle();
        return NanoUtility.measureTextWidth(vg, fontName, fontSize, value, start, end);
    }

    /**
     * Applies the shared single-line text sanitizer, removing unsupported controls
     * and malformed surrogate content according to TextEditModel policy.
     *
     * @param value raw text, possibly null
     * @return sanitized non-null text
     */
    private String sanitize(String value) {
        return valthorne.ui.behavior.TextEditModel.sanitize(value);
    }
}
