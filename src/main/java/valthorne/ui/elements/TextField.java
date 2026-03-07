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

public class TextField extends Element {

    private TextFieldStyle style;
    private Drawable current;
    private Font font;
    private final Texture uiPixel;
    private final Color selectionColor = new Color(1f, 1f, 1f, 0.35f);
    private final Color caretColor = new Color(1f, 1f, 1f, 1f);

    private String text = "";
    private String placeHolder;
    private UIAction<TextField> action;

    private boolean masking;
    private char maskCharacter = '*';

    private String displayText = "";
    private boolean displayTextDirty = true;

    private int cursorIndex = 0;

    private int characterLimit;

    private int selectionStart = 0;
    private int selectionEnd = 0;
    private boolean selecting;

    private boolean cursorActive = true;
    private float cursorBlinkTime;
    private float cursorBlinkInterval = 0.5f;
    private float maxCaretHeight;

    private float textOffsetX = 0f;
    private float padding = 10f;

    private float caretWidth = 2f;
    private float caretPadY = 8f;

    private float doubleClickTimer = 0f;
    private float doubleClickWindow = 0.25f;
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
        this.uiPixel = createWhitePixelTexture();
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
    public void draw(TextureBatch batch) {
        if (current != null) current.draw(batch, x, y, width, height);

        float sx = x + padding - scissorFudge * 0.5f;
        float sw = (width - padding * 2) + scissorFudge;

        batch.beginScissor((int) sx, (int) y, (int) sw, (int) height);

        if (isFocused() && hasSelection()) drawSelection(batch);

        font.draw(batch);
        drawCaret(batch);

        batch.endScissor();
    }

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

    private void drawCaret(TextureBatch batch) {
        if (!cursorActive || !isFocused()) return;

        float cx = font.getX() + getCursorX();
        float caretHeight = getCaretHeight();

        float centerY = y + height * 0.5f;
        float top = centerY - caretHeight * 0.5f;

        batch.draw(uiPixel, cx, top, caretWidth, caretHeight, caretColor);
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

        if (hasCharacterLimit() && text.length() >= characterLimit) return;

        text = text.substring(0, cursorIndex) + c + text.substring(cursorIndex);
        cursorIndex++;

        markDisplayTextDirty();
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

        markDisplayTextDirty();
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

        markDisplayTextDirty();
        updateFontText();
        updateScroll();
    }

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

    private void markDisplayTextDirty() {
        displayTextDirty = true;
    }

    private float getCursorX() {
        return getTextWidthUpTo(cursorIndex);
    }

    private float getTextWidthUpTo(int index) {
        if (index <= 0) return 0f;
        return font.getWidth(getDisplayText(), 0, index);
    }

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
                java.util.Arrays.fill(chars, maskCharacter);
                displayText = new String(chars);
            }
            displayTextDirty = false;
        }

        return displayText;
    }

    private int getIndexAtMouseX(float mouseX) {
        float local = mouseX - font.getX() - padding;
        if (local <= 0f) return 0;

        String d = getDisplayText();
        int length = d.length();
        for (int i = 1; i <= length; i++) {
            if (font.getWidth(d, 0, i) >= local) return i;
        }
        return length;
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

    private void updateFontPosition() {
        Vector2f align = Alignment.align(this, font, Alignment.START, Alignment.CENTER);
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
        markDisplayTextDirty();

        updateFontText();
        updateScroll();
    }

    public char getMaskCharacter() {
        return maskCharacter;
    }

    public void setMaskCharacter(char maskCharacter) {
        if (this.maskCharacter == maskCharacter) return;

        this.maskCharacter = maskCharacter;
        if (masking) {
            markDisplayTextDirty();
            updateFontText();
            updateScroll();
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String value) {
        if (value == null) value = "";

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

    public int getCharacterLimit() {
        return characterLimit;
    }

    private float getCaretHeight() {
        float availHeight = height - caretPadY * 2;
        return Math.min(maxCaretHeight, availHeight);
    }

    public Font getFont() {
        return font;
    }

    public void dispose() {
        font.dispose();
        uiPixel.dispose();
    }

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