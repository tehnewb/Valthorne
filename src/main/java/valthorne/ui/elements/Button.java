package valthorne.ui.elements;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.math.Vector2f;
import valthorne.ui.Alignment;
import valthorne.ui.Element;
import valthorne.ui.UIAction;
import valthorne.ui.styles.ButtonStyle;

public class Button extends Element {

    private final Font font;
    private ButtonStyle style;
    private Drawable current;
    private UIAction<Button> action;
    private Alignment textAlignment;

    public Button(String text) {
        this(text, Alignment.CENTER_CENTER);
    }

    public Button(String text, Alignment alignment) {
        this(text, alignment, null, new ButtonStyle());
    }

    public Button(String text, Alignment alignment, ButtonStyle style) {
        this(text, alignment, null, style);
    }

    public Button(String text, ButtonStyle style) {
        this(text, Alignment.CENTER_CENTER, null, style);
    }

    public Button(String text, UIAction<Button> action) {
        this(text, Alignment.CENTER_CENTER, action, new ButtonStyle());
    }

    public Button(String text, Alignment alignment, UIAction<Button> action) {
        this(text, alignment, action, new ButtonStyle());
    }

    public Button(String text, UIAction<Button> action, ButtonStyle style) {
        this(text, Alignment.CENTER_CENTER, action, style);
    }

    public Button(String text, Alignment alignment, UIAction<Button> action, ButtonStyle style) {
        this.style = style;
        this.textAlignment = alignment;
        this.current = style.getBackground();
        this.font = new Font(style.getFontData());
        this.font.setText(text);
        this.action = action;
        this.setFocusable(true);
    }

    @Override
    public void update(float delta) {

    }

    @Override
    public void draw() {
        if (current == null)
            return;

        this.getUI().getViewport().applyScissor(this.x, this.y, this.width, this.height, () -> {
            current.draw(this.x, this.y, this.width, this.height);

            font.draw();
        });
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (action == null)
            return;

        if (event.getKey() == Keyboard.ENTER)
            current = style.getPressed();
    }

    @Override
    public void onKeyRelease(KeyReleaseEvent event) {
        current = style.getBackground();
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        current = style.getPressed();
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (isHovered() && style.getHovered() != null)
            current = style.getHovered();
        else current = style.getBackground();

        if (action == null)
            return;

        action.perform(this);
    }

    public Button setStyle(ButtonStyle style) {
        this.style = style;
        return this;
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);

        Vector2f fontPosition = Alignment.align(this, font, textAlignment);

        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);

        Vector2f fontPosition = Alignment.align(this, font, textAlignment);

        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    public Font getFont() {
        return font;
    }

    public Button setText(String text) {
        if (text == null) {
            font.setText("");
            return this;
        }

        font.setText(text);
        return this;
    }

    public Button setTextAlignment(Alignment alignment) {
        this.textAlignment = alignment;
        return this;
    }

    public Button setAction(UIAction<Button> action) {
        this.action = action;
        return this;
    }

    @Override
    public void setDisabled(boolean value) {
        super.setDisabled(value);

        if (style.getDisabled() == null)
            return;

        current = value ? style.getDisabled() : style.getBackground();
    }

    @Override
    protected void setFocused(boolean value) {
        super.setFocused(value);

        if (style.getFocused() == null)
            return;

        current = value ? style.getFocused() : style.getBackground();
    }

    @Override
    protected void setHovered(boolean value) {
        super.setHovered(value);

        if (style.getHovered() == null)
            return;

        if (value) {
            current = style.getHovered();
        } else {
            current = style.getBackground();
        }
    }
}
