package valthorne.ui.elements;

import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.ui.Element;
import valthorne.ui.UIAction;
import valthorne.ui.styles.CheckboxStyle;

public class Checkbox extends Element {

    private CheckboxStyle style;
    private Drawable current;
    private UIAction<Checkbox> action;
    private boolean checked;

    public Checkbox(CheckboxStyle style) {
        this.style = style;
        this.current = style.getBackground();
        this.setFocusable(true);
    }

    public Checkbox() {
        this(new CheckboxStyle());
    }

    @Override
    public void update(float delta) {

    }

    @Override
    public void draw() {
        if (current == null)
            return;

        current.draw(this.x, this.y, this.width, this.height);
        if (checked && style.getCheckmark() != null) {
            Drawable checkmark = style.getCheckmark();
            float cw = this.width / 1.5f;
            float ch = this.height / 1.5f;
            style.getCheckmark().draw(this.x + (this.width - cw) / 2, this.y + (this.height - ch) / 2, cw, ch);
        }
    }

    @Override
    public void onKeyRelease(KeyReleaseEvent event) {
        if (style.getBackground() != null)
            current = style.getBackground();
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        if (style.getPressed() != null)
            current = style.getPressed();
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (isHovered() && style.getHovered() != null)
            current = style.getHovered();
        else current = style.getBackground();

        checked = !checked;

        if (action == null)
            return;
        if (checked)
            action.perform(this);
    }

    public Checkbox setStyle(CheckboxStyle style) {
        this.style = style;
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

    public Checkbox setAction(UIAction<Checkbox> action) {
        this.action = action;
        return this;
    }

    public boolean isChecked() {
        return checked;
    }

    public Checkbox setChecked(boolean checked) {
        this.checked = checked;
        return this;
    }
}
