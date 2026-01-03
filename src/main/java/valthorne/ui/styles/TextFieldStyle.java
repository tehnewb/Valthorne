package valthorne.ui.styles;

import valthorne.graphics.Drawable;
import valthorne.graphics.font.FontData;

public class TextFieldStyle {

    private FontData fontData;
    private Drawable background;
    private Drawable focused;

    public static TextFieldStyle of() {
        return new TextFieldStyle();
    }

    public TextFieldStyle fontData(FontData fontData) {
        this.fontData = fontData;
        return this;
    }

    public FontData getFontData() {
        return fontData;
    }

    public TextFieldStyle background(Drawable background) {
        this.background = background;
        return this;
    }

    public Drawable getBackground() {
        return background;
    }

    public TextFieldStyle focused(Drawable focused) {
        this.focused = focused;
        return this;
    }

    public Drawable getFocused() {
        return focused;
    }

}
