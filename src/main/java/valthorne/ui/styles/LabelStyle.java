package valthorne.ui.styles;

import valthorne.graphics.font.FontData;

public class LabelStyle {

    private FontData fontData;

    public static LabelStyle of() {
        return new LabelStyle();
    }

    public FontData getFontData() {
        return fontData;
    }

    public LabelStyle fontData(FontData fontData) {
        this.fontData = fontData;
        return this;
    }

}
