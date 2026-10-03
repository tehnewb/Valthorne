package valthorne.ui.nodes;

import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;

import java.util.Objects;

/**
 * Retained Slug text for regular or NanoVG UI containers. Text and size changes refresh
 * a reusable glyph layout and the node's measured dimensions. All drawing evaluates
 * live curves through the ordinary texture batch, including composed UI transforms.
 * The font is borrowed and must outlive all labels using it. GPU operations require
 * their owning graphics-context thread.
 * <pre>{@code
 * SlugLabel label = new SlugLabel(font, "Score: 0", 24f);
 * container.add(label);
 * label.text("Score: 100").color(Color.WHITE);
 * }</pre>
 * Container clipping is applied in world coordinates, including rotated labels.
 * The label owns only CPU layout; its root's texture
 * batch owns GPU drawing resources.
 * @author Albert Beaupre
 */
public final class SlugLabel extends UINode {
    private final SlugFont font; // Borrowed live-curve font, which must outlive this label.
    private final SlugTextRun run; // Owned reusable CPU glyph layout borrowing the font.
    private Color color = Color.WHITE; // Borrowed mutable tint applied to subsequent draws.

    /**
     * Creates retained curve text using the owning UI root's texture batch. Callers
     * only manage the font, while {@link UIRoot} owns and disposes drawing resources.
     *
     * @param font borrowed font supplying live curve data and metrics
     * @param text initial text; null is normalized to empty
     * @param size finite nonnegative world units per em
     */
    public SlugLabel(SlugFont font, String text, float size) {
        this.font = Objects.requireNonNull(font, "font");
        run = font.createRun(text, size);
        measure();
    }

    /**
     * Returns the font borrowed by this label so an owning scene item can
     * release a font that it created exclusively for the label.
     *
     * @return borrowed font used for glyph metrics and rendering
     */
    public SlugFont getFont() {
        return font;
    }

    /**
     * Changes text and updates layout dimensions; unchanged content reuses glyph arrays.
     * @param text replacement text, with null treated as empty
     * @return this label for configuration chaining
     */
    public SlugLabel text(String text) {
        run.rebuild(text, run.size());
        measure();
        return this;
    }

    /**
     * Reads the normalized source string retained by the glyph layout.
     * @return current text, never null
     */
    public String text() { return run.text(); }

    /**
     * Changes the em scale, rebuilds glyph positions when necessary, and updates dimensions.
     * @param size finite nonnegative world units per em
     * @return this label
     * @throws IllegalArgumentException if size is negative or nonfinite
     */
    public SlugLabel size(float size) {
        run.rebuild(run.text(), size);
        measure();
        return this;
    }

    /**
     * Reads the retained font scale, which is independent of the node's layout height.
     * @return world units per em
     */
    public float size() { return run.size(); }

    /**
     * Borrows a tint object without copying it; later mutations affect subsequent draws.
     * @param color nonnull text tint
     * @return this label
     * @throws NullPointerException if color is null
     */
    public SlugLabel color(Color color) {
        this.color = Objects.requireNonNull(color, "color");
        return this;
    }

    /**
     * Updates explicit layout width and height from the retained run's measured extent.
     */
    private void measure() {
        getLayout()
                .width(run.width())
                .height(run.height());
    }

    @Override
    public void onCreate() { }

    @Override
    public void onDestroy() { }

    @Override
    public void update(float delta) { }

    @Override
    public void draw(TextureBatch batch) {
        run.draw(batch, getRenderX(), getRenderY() + getHeight() - font.ascent() * run.size(), color);
    }
}
