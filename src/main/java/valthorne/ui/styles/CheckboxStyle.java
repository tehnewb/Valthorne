package valthorne.ui.styles;

import valthorne.graphics.Drawable;

public class CheckboxStyle {

    private Drawable checkmark;
    private Drawable background;    // Drawable for button's default state
    private Drawable hover;     // Drawable for button's hover state
    private Drawable pressed;   // Drawable for button's pressed state
    private Drawable disabled;  // Drawable for button's disabled state
    private Drawable focused;  // Drawable for button's disabled state

    public static CheckboxStyle of() {
        return new CheckboxStyle();
    }

    public Drawable getCheckmark() {
        return checkmark;
    }

    public CheckboxStyle checkmark(Drawable checkmark) {
        this.checkmark = checkmark;
        return this;
    }

    public Drawable getBackground() {
        return background;
    }

    public CheckboxStyle background(Drawable background) {
        this.background = background;
        return this;
    }

    public Drawable getHovered() {
        return hover;
    }

    public CheckboxStyle hovered(Drawable hover) {
        this.hover = hover;
        return this;
    }

    public Drawable getPressed() {
        return pressed;
    }

    public CheckboxStyle pressed(Drawable pressed) {
        this.pressed = pressed;
        return this;
    }

    public Drawable getDisabled() {
        return disabled;
    }

    public CheckboxStyle disabled(Drawable disabled) {
        this.disabled = disabled;
        return this;
    }

    public Drawable getFocused() {
        return focused;
    }

    public CheckboxStyle focused(Drawable focused) {
        this.focused = focused;
        return this;
    }

}
