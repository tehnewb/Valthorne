package valthorne.ui.elements;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.Alignment;
import valthorne.ui.Element;
import valthorne.ui.UIAction;
import valthorne.ui.styles.TextFieldStyle;
import valthorne.viewport.Viewport;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

import static org.lwjgl.opengl.GL11.*;

public class TextField extends Element {

    private TextFieldStyle style;
    private Drawable current;
    private Font font;

    private String text = "";
    private String placeHolder;
    private UIAction<TextField> action;

    private boolean masking;
    private char maskCharacter = '*';

    private int cursorIndex = 0;

    /**
     * Maximum number of characters allowed in this field.
     * A value <= 0 means "no limit".
     */
    private int characterLimit;

    private int selectionStart = 0;
    private int selectionEnd = 0;
    private boolean selecting;

    private boolean cursorActive = true;
    private float cursorBlinkTime;
    private float cursorBlinkInterval = 0.5f;
    private float maxCaretHeight; // pixels, tweakable

    private float textOffsetX = 0f;
    private float padding = 10f;

    private float caretWidth = 2f;
    private float caretPadY = 8f;

    private float doubleClickTimer = 0f;
    private float doubleClickWindow = 0.25f; // seconds
    private boolean pendingClick;

    private float scissorFudge = 4f;

    public TextField(String placeHolder, TextFieldStyle style, UIAction<TextField> action) {
        this.style = style;
        this.font = new Font(style.getFontData());
        this.placeHolder = placeHolder;
        this.action = action;
        this.font.setText(placeHolder);
        this.current = style.getBackground();
        this.setFocusable(true);
        this.maxCaretHeight = font.getHeight() * 2;
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

        if (!isFocused()) return;

        cursorBlinkTime += delta;
        if (cursorBlinkTime >= cursorBlinkInterval) {
            cursorBlinkTime = 0f;
            cursorActive = !cursorActive;
        }
    }

    @Override
    public void draw() {
        if (current != null) current.draw(x, y, width, height);

        Viewport viewport = getUI().getViewport();

        float sx = x + padding - scissorFudge * 0.5f;
        float sw = (width - padding * 2) + scissorFudge;

        viewport.applyScissor(sx, y, sw, height, () -> {
            if (isFocused() && hasSelection()) drawSelection();

            font.draw();
            drawCaret();
        });
    }

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

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        selecting = false;
    }

    private boolean hasCharacterLimit() {
        return characterLimit > 0;
    }

    private int remainingCapacity() {
        if (!hasCharacterLimit()) return Integer.MAX_VALUE;
        return Math.max(0, characterLimit - text.length());
    }

    private String clampToLimit(String s) {
        if (s == null) return null;
        if (!hasCharacterLimit()) return s;

        int cap = remainingCapacity();
        if (cap <= 0) return "";
        if (s.length() <= cap) return s;

        return s.substring(0, cap);
    }

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

    private void deleteSelection() {
        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);

        text = text.substring(0, a) + text.substring(b);
        cursorIndex = a;
        clearSelection();

        updateFontText();
        updateScroll();
    }

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

    private boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    private void clearSelection() {
        selectionStart = cursorIndex;
        selectionEnd = cursorIndex;
    }

    private void selectAll() {
        selectionStart = 0;
        selectionEnd = text.length();
        cursorIndex = selectionEnd;
        updateScroll();
    }

    private void copySelection() {
        if (!hasSelection()) return;

        int a = Math.min(selectionStart, selectionEnd);
        int b = Math.max(selectionStart, selectionEnd);
        setClipboard(text.substring(a, b));
    }

    private void cutSelection() {
        copySelection();
        deleteSelection();
    }

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

    private String getClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }

    private void setClipboard(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
    }

    private void resetCursorBlink() {
        cursorBlinkTime = 0f;
        cursorActive = true;
    }

    private float getCursorX() {
        return getTextWidthUpTo(cursorIndex);
    }

    private float getTextWidthUpTo(int index) {
        if (index <= 0) return 0;
        return font.getWidth(getDisplayText().substring(0, index));
    }

    private String getDisplayText() {
        return masking ? String.valueOf(maskCharacter).repeat(text.length()) : text;
    }

    private int getIndexAtMouseX(float mouseX) {
        float local = mouseX - font.getX() - padding;
        if (local <= 0) return 0;

        String d = getDisplayText();
        for (int i = 1; i <= d.length(); i++)
            if (font.getWidth(d.substring(0, i)) >= local) return i;
        return d.length();
    }

    private int prevWord(int i) {
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        while (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) i--;
        return i;
    }

    private int nextWord(int i) {
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) i++;
        return i;
    }

    private void updateFontText() {
        font.setText(text.isEmpty() && !isFocused() ? placeHolder : getDisplayText());
    }

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

    private void updateFontPosition() {
        Vector2f align = Alignment.align(this, font, Alignment.CENTER_LEFT);
        font.setPosition(align.getX() + padding + textOffsetX, align.getY());
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        updateFontPosition();
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);
        updateFontPosition();
    }

    @Override
    protected void setFocused(boolean value) {
        super.setFocused(value);

        resetCursorBlink();
        current = value ? style.getFocused() : style.getBackground();

        updateFontText();
        updateScroll();
    }

    public boolean isMasking() {
        return masking;
    }

    public void setMasking(boolean masking) {
        if (this.masking == masking) return;

        this.masking = masking;

        updateFontText();
        updateScroll();
    }

    public char getMaskCharacter() {
        return maskCharacter;
    }

    public void setMaskCharacter(char maskCharacter) {
        this.maskCharacter = maskCharacter;
        if (masking) {
            updateFontText();
            updateScroll();
        }
    }

    public String getText() {
        return text;
    }

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
     * Sets the max character limit for this TextField.
     * A value <= 0 means "no limit".
     *
     * @param limit the maximum number of characters allowed, or <= 0 for unlimited
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
     * @return the configured character limit, or <= 0 if unlimited
     */
    public int getCharacterLimit() {
        return characterLimit;
    }

    private float getCaretHeight() {
        float availHeight = height - caretPadY * 2;
        return Math.min(maxCaretHeight, availHeight);
    }

}
