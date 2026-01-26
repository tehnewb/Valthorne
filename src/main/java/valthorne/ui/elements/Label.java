package valthorne.ui.elements;

import valthorne.graphics.font.Font;
import valthorne.math.Vector2f;
import valthorne.ui.Alignment;
import valthorne.ui.Element;
import valthorne.ui.styles.LabelStyle;

public class Label extends Element {

    private String text;
    private LabelStyle style;
    private Font font;
    private Alignment textAlignment;

    public Label(String text, LabelStyle style) {
        this.text = text;
        this.style = style;
        this.textAlignment = Alignment.CENTER_CENTER;
        this.font = new Font(style.getFontData());
        this.font.setText(text);
        this.setSize(font.getWidth(), font.getHeight());
    }

    public Label(String text) {
        this(text, new LabelStyle());
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void draw() {
        font.draw();
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
}
