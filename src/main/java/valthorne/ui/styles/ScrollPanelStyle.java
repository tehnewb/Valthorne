package valthorne.ui.styles;

import valthorne.graphics.Drawable;

public class ScrollPanelStyle {

    private Drawable background;

    public static ScrollPanelStyle of() {
        return new ScrollPanelStyle();
    }

    public Drawable getBackground() {
        return background;
    }

    public ScrollPanelStyle background(Drawable background) {
        this.background = background;
        return this;
    }

}
