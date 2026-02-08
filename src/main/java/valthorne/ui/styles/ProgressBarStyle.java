package valthorne.ui.styles;

import valthorne.graphics.Drawable;
import valthorne.graphics.font.FontData;

public class ProgressBarStyle {

    private FontData fontData;
    private Drawable background;
    private Drawable foreground;

    public static ProgressBarStyle of() {
        return new ProgressBarStyle();
    }

    public FontData getFontData() {
        return fontData;
    }

    public ProgressBarStyle fontData(FontData fontData) {
        this.fontData = fontData;
        return this;
    }

    public Drawable getBackground() {
        return background;
    }

    public ProgressBarStyle background(Drawable background) {
        this.background = background;
        return this;
    }

    public Drawable getForeground() {
        return foreground;
    }

    public ProgressBarStyle foreground(Drawable foreground) {
        this.foreground = foreground;
        return this;
    }
}