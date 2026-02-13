package valthorne.ui.elements;

import valthorne.graphics.font.Font;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.enums.Alignment;
import valthorne.ui.Element;
import valthorne.ui.styles.ProgressBarStyle;

public class ProgressBar extends Element {

    private ProgressBarStyle style;
    private final Font font;
    private final float min;
    private final float max;

    private float progress;
    private float displayedProgress;
    private boolean displayPercentage;

    public ProgressBar(float min, float max, ProgressBarStyle style) {
        this.min = min;
        this.max = max;
        this.progress = min;
        this.style = style;
        this.font = new Font(style.getFontData());
        this.font.setText("0.00%");
    }

    @Override
    public void update(float delta) {
        this.displayedProgress = MathUtils.lerp(displayedProgress, progress, delta * 20f);
        if (displayPercentage && displayedProgress != progress)
            this.font.setText(String.format("%.2f%%", this.displayedProgress));
    }

    @Override
    public void draw() {
        float percentage = displayedProgress / (max - min);

        style.getBackground().draw(this.x, this.y, this.width, this.height);
        style.getForeground().draw(this.x, this.y, this.width * percentage, this.height);
        if (displayPercentage)
            font.draw();
    }

    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);

        Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);

        Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    public void setProgress(float progress) {
        this.progress = MathUtils.clamp(progress, min, max);
        if (displayPercentage) {
            this.font.setText(String.format("%.2f%%", this.progress));
            Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
            font.setPosition(fontPosition.getX(), fontPosition.getY());
        }
    }

    public float getProgress() {
        return progress;
    }

    public void setDisplayPercentage(boolean displayPercentage) {
        this.displayPercentage = displayPercentage;

        if (displayPercentage) {
            this.font.setText(String.format("%.2f%%", this.progress));
            Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
            font.setPosition(fontPosition.getX(), fontPosition.getY());
        }
    }

    public boolean isDisplayPercentage() {
        return displayPercentage;
    }

    public ProgressBar setStyle(ProgressBarStyle style) {
        this.style = style;
        return this;
    }
}
