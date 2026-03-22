package valthorne.ui.nodes.nano;

import org.lwjgl.nanovg.NVGColor;
import valthorne.Keyboard;
import valthorne.event.events.*;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.NanoUtility;
import valthorne.ui.NodeAction;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.viewport.Viewport;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoTextField  extends UINode implements NanoNode {

    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.textfield.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.textfield.hoverBackgroundColor", Color.class, new Color(0xFF323232));
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.textfield.focusedBackgroundColor", Color.class, new Color(0xFF3A3A3A));
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.textfield.borderColor", Color.class, new Color(0xFF555555));
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.textfield.hoverBorderColor", Color.class, new Color(0xFF777777));
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.textfield.focusedBorderColor", Color.class, new Color(0xFF7AA2FF));
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.textfield.textColor", Color.class, new Color(0xFFFFFFFF));
    public static final StyleKey<Color> PLACEHOLDER_COLOR_KEY = StyleKey.of("nano.textfield.placeholderColor", Color.class, new Color(0xFFAAAAAA));
    public static final StyleKey<Color> CARET_COLOR_KEY = StyleKey.of("nano.textfield.caretColor", Color.class, new Color(0xFFFFFFFF));
    public static final StyleKey<Color> SELECTION_COLOR_KEY = StyleKey.of("nano.textfield.selectionColor", Color.class, new Color(0xFF66A3FF));
    public static final StyleKey<Color> DEFAULT_SELECTION_COLOR_KEY = StyleKey.of("nano.textfield.defaultSelectionColor", Color.class, new Color(0xFF66A3FF));
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.textfield.fontName", String.class, "default");
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.textfield.fontSize", Float.class, 18f);
    public static final StyleKey<Float> PADDING_KEY = StyleKey.of("nano.textfield.padding", Float.class, 10f);
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.textfield.cornerRadius", Float.class, 6f);
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.textfield.borderWidth", Float.class, 1f);
    public static final StyleKey<Float> CARET_WIDTH_KEY = StyleKey.of("nano.textfield.caretWidth", Float.class, 2f);
    public static final StyleKey<Float> CARET_PADDING_Y_KEY = StyleKey.of("nano.textfield.caretPaddingY", Float.class, 8f);
    public static final StyleKey<Float> SCISSOR_FUDGE_KEY = StyleKey.of("nano.textfield.scissorFudge", Float.class, 4f);
    public static final StyleKey<NodeAction<NanoTextField>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoTextField>>) (Class<?>) NodeAction.class);

    private String text = "";
    private String placeholder = "";
    private String displayText = "";
    private boolean displayTextDirty = true;

    private int caretIndex;
    private int selectionStart;
    private int selectionEnd;
    private boolean selecting;

    private boolean masking;
    private char maskChar = '*';

    private boolean caretVisible = true;
    private float blinkTime;
    private float blinkInterval = 0.5f;

    private float padding = 10f;
    private float cornerRadius = 6f;
    private float borderWidth = 1f;
    private float caretWidth = 2f;
    private float caretPadY = 8f;
    private float scissorFudge = 4f;
    private float textOffsetX;

    private float doubleClickTimer;
    private float doubleClickWindow = 0.25f;
    private boolean pendingClick;

    private Color backgroundColor = new Color(0xFF2A2A2A);
    private Color hoverBackgroundColor = new Color(0xFF323232);
    private Color focusedBackgroundColor = new Color(0xFF3A3A3A);
    private Color borderColor = new Color(0xFF555555);
    private Color hoverBorderColor = new Color(0xFF777777);
    private Color focusedBorderColor = new Color(0xFF7AA2FF);
    private Color textColor = new Color(0xFFFFFFFF);
    private Color placeholderColor = new Color(0xFFAAAAAA);
    private Color caretColor = new Color(0xFFFFFFFF);
    private Color selectionColor = new Color(0xFF66A3FF);
    private Color defaultSelectionColor = new Color(0xFF66A3FF);

    private String fontName = "default";
    private float fontSize = 18f;
    private boolean fontLoaded;

    private NodeAction<NanoTextField> action;

    public NanoTextField() {
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        markLayoutDirty();
    }

    public NanoTextField(String placeholder) {
        this();
        placeholder(placeholder);
    }

    public String getText() {
        return text;
    }

    public NanoTextField text(String text) {
        this.text = sanitize(text);
        caretIndex = MathUtils.clamp(caretIndex, 0, this.text.length());
        clearSelection();
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public NanoTextField placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : sanitize(placeholder);
        markLayoutDirty();
        return this;
    }

    public int getCaretIndex() {
        return caretIndex;
    }

    public NanoTextField caretIndex(int caretIndex) {
        this.caretIndex = MathUtils.clamp(caretIndex, 0, text.length());
        clearSelection();
        resetCursorBlink();
        updateScroll();
        return this;
    }

    public boolean isMasking() {
        return masking;
    }

    public NanoTextField masking(boolean masking) {
        if (this.masking == masking)
            return this;
        this.masking = masking;
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    public char getMaskChar() {
        return maskChar;
    }

    public NanoTextField maskChar(char maskChar) {
        if (this.maskChar == maskChar)
            return this;
        this.maskChar = maskChar;
        markDisplayTextDirty();
        markLayoutDirty();
        updateScroll();
        return this;
    }

    public NodeAction<NanoTextField> getAction() {
        return action;
    }

    public NanoTextField action(NodeAction<NanoTextField> action) {
        this.action = action;
        return this;
    }

    public NanoTextField fontName(String fontName) {
        if (fontName != null && !fontName.isBlank())
            this.fontName = fontName;
        fontLoaded = false;
        markLayoutDirty();
        return this;
    }

    public NanoTextField fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        markLayoutDirty();
        return this;
    }

    public NanoTextField padding(float padding) {
        this.padding = Math.max(0f, padding);
        markLayoutDirty();
        return this;
    }

    public NanoTextField cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    public NanoTextField borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    public NanoTextField backgroundColor(Color backgroundColor) {
        if (backgroundColor != null)
            this.backgroundColor = backgroundColor;
        return this;
    }

    public NanoTextField hoverBackgroundColor(Color hoverBackgroundColor) {
        if (hoverBackgroundColor != null)
            this.hoverBackgroundColor = hoverBackgroundColor;
        return this;
    }

    public NanoTextField focusedBackgroundColor(Color focusedBackgroundColor) {
        if (focusedBackgroundColor != null)
            this.focusedBackgroundColor = focusedBackgroundColor;
        return this;
    }

    public NanoTextField borderColor(Color borderColor) {
        if (borderColor != null)
            this.borderColor = borderColor;
        return this;
    }

    public NanoTextField hoverBorderColor(Color hoverBorderColor) {
        if (hoverBorderColor != null)
            this.hoverBorderColor = hoverBorderColor;
        return this;
    }

    public NanoTextField focusedBorderColor(Color focusedBorderColor) {
        if (focusedBorderColor != null)
            this.focusedBorderColor = focusedBorderColor;
        return this;
    }

    public NanoTextField textColor(Color textColor) {
        if (textColor != null)
            this.textColor = textColor;
        return this;
    }

    public NanoTextField placeholderColor(Color placeholderColor) {
        if (placeholderColor != null)
            this.placeholderColor = placeholderColor;
        return this;
    }

    public NanoTextField caretColor(Color caretColor) {
        if (caretColor != null)
            this.caretColor = caretColor;
        return this;
    }

    public NanoTextField selectionColor(Color selectionColor) {
        if (selectionColor != null)
            this.selectionColor = selectionColor;
        return this;
    }

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

    @Override
    public void draw(TextureBatch batch) {

    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {
        super.onMouseMove(event);
    }

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

    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!selecting)
            return;

        float mouseX = getEventX(event);
        caretIndex = getIndexAtMouseX(mouseX);
        selectionEnd = caretIndex;

        resetCursorBlink();
        updateScroll();
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        selecting = false;
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (!isFocused())
            return;

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
                        NodeAction<NanoTextField> resolved = style.get(ACTION_KEY);
                        if (resolved != null)
                            resolved.perform(this);
                    }
                }
                return;
            }
            default -> {
                char c = event.getChar();

                if (c == 0 || c == '\n' || c == '\r')
                    return;

                if (Character.isISOControl(c))
                    return;

                if (hasSelection())
                    deleteSelection();

                text = text.substring(0, caretIndex) + c + text.substring(caretIndex);
                caretIndex++;
            }
        }

        updateScroll();
        markDisplayTextDirty();
        markLayoutDirty();
    }

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
            float measuredHeight = NanoUtility.measureTextHeight(getNanoHandle(), fontName, fontSize) + padding * 2f;

            if (getLayout().getWidth().isAuto())
                getLayout().width(measuredWidth);

            if (getLayout().getHeight().isAuto())
                getLayout().height(measuredHeight);
        }

        super.applyLayout();
        updateScroll();
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L)
            return;

        ensureFontLoaded();

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

    @Override
    public void onCreate() {
        ensureFontLoaded();
    }

    @Override
    public void onDestroy() {
    }

    private void ensureFontLoaded() {
        UIRoot root = getRoot();
        if (root == null)
            return;

        long vg = root.getNanoVGHandle();
        if (vg == 0L)
            return;

        if (fontLoaded && nvgFindFont(vg, fontName) != -1)
            return;

        if (nvgFindFont(vg, fontName) != -1) {
            fontLoaded = true;
            return;
        }

        int handle = nvgCreateFont(vg, fontName, "./assets/ui/font.otf");
        if (handle != -1)
            fontLoaded = true;
    }

    private long getNanoHandle() {
        UIRoot root = getRoot();
        return root == null ? 0L : root.getNanoVGHandle();
    }

    private float getEventX(MousePressEvent event) {
        return getEventPosition(event.getX(), event.getY()).getX();
    }

    private float getEventX(MouseDragEvent event) {
        return getEventPosition(event.getX(), event.getY()).getX();
    }

    private Vector2f getEventPosition(float screenX, float screenY) {
        UIRoot root = getRoot();
        if (root != null) {
            Viewport viewport = root.getViewport();
            if (viewport != null) {
                Vector2f world = viewport.screenToWorld(screenX, screenY);
                if (world != null)
                    return world;
            }
        }

        return new Vector2f(screenX, screenY);
    }

    private void resetCursorBlink() {
        blinkTime = 0f;
        caretVisible = true;
    }

    private boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    private void clearSelection() {
        selectionStart = caretIndex;
        selectionEnd = caretIndex;
    }

    private void selectAll() {
        selectionStart = 0;
        selectionEnd = text.length();
        caretIndex = text.length();
        updateScroll();
    }

    private void moveCursor(int index, boolean extend) {
        index = MathUtils.clamp(index, 0, text.length());

        if (extend) {
            if (!hasSelection())
                selectionStart = caretIndex;
            selectionEnd = index;
        } else {
            selectionStart = index;
            selectionEnd = index;
        }

        caretIndex = index;
        updateScroll();
    }

    private void deleteBackward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (caretIndex <= 0)
            return;

        text = text.substring(0, caretIndex - 1) + text.substring(caretIndex);
        caretIndex--;
        clearSelection();
        markDisplayTextDirty();
        markLayoutDirty();
    }

    private void deleteForward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }

        if (caretIndex >= text.length())
            return;

        text = text.substring(0, caretIndex) + text.substring(caretIndex + 1);
        clearSelection();
        markDisplayTextDirty();
        markLayoutDirty();
    }

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

    private void copySelection() {
        if (!hasSelection())
            return;

        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.substring(a, b)), null);
    }

    private void cutSelection() {
        if (!hasSelection())
            return;

        copySelection();
        deleteSelection();
    }

    private void pasteClipboard() {
        try {
            String value = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (value == null || value.isEmpty())
                return;

            if (hasSelection())
                deleteSelection();

            value = sanitize(value);
            text = text.substring(0, caretIndex) + value + text.substring(caretIndex);
            caretIndex += value.length();
            clearSelection();
            markDisplayTextDirty();
            markLayoutDirty();
            updateScroll();
        } catch (Throwable ignored) {
        }
    }

    private String getDisplayText() {
        if (!masking || text.isEmpty())
            return text;

        if (displayTextDirty) {
            displayText = String.valueOf(maskChar).repeat(text.length());
            displayTextDirty = false;
        }

        return displayText;
    }

    private void markDisplayTextDirty() {
        displayTextDirty = true;
    }

    private int getIndexAtMouseX(float mouseX) {
        String visibleText = getDisplayText();
        float local = mouseX - (getAbsoluteX() + padding + textOffsetX);
        if (local <= 0f)
            return 0;

        int length = visibleText.length();
        for (int i = 1; i <= length; i++) {
            if (measureTextWidth(visibleText, 0, i) >= local)
                return i;
        }

        return length;
    }

    private int prevWord(int index) {
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1)))
            index--;
        while (index > 0 && Character.isLetterOrDigit(text.charAt(index - 1)))
            index--;
        return index;
    }

    private int nextWord(int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index)))
            index++;
        while (index < text.length() && Character.isLetterOrDigit(text.charAt(index)))
            index++;
        return index;
    }

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

    private float measureTextWidth(String value) {
        long vg = getNanoHandle();
        return NanoUtility.measureTextWidth(vg, fontName, fontSize, value);
    }

    private float measureTextWidth(String value, int start, int end) {
        long vg = getNanoHandle();
        return NanoUtility.measureTextWidth(vg, fontName, fontSize, value, start, end);
    }

    private String sanitize(String value) {
        if (value == null || value.isEmpty())
            return "";

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

        return builder.toString();
    }
}