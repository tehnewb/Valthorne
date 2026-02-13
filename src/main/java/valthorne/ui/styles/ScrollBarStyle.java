package valthorne.ui.styles;

import valthorne.graphics.Drawable;

public class ScrollBarStyle {

    private Drawable bar;
    private Drawable thumb;

    public static ScrollBarStyle of() {
        return new ScrollBarStyle();
    }

    public Drawable getBar() {
        return bar;
    }

    public ScrollBarStyle bar(Drawable bar) {
        this.bar = bar;
        return this;
    }

    public Drawable getThumb() {
        return thumb;
    }

    public ScrollBarStyle thumb(Drawable thumb) {
        this.thumb = thumb;
        return this;
    }
}
