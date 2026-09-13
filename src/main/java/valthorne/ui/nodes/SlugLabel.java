package valthorne.ui.nodes;

import org.joml.Matrix4f;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugBatch;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;

import java.util.Objects;
import static org.lwjgl.opengl.GL11.*;

/**
 * Retained curve-rendered text for regular or NanoVG UI containers. Text and size changes
 * refresh a reusable glyph layout and the node's measured dimensions; each draw renders
 * live curves through a shared Slug batch. The font and renderer are borrowed and must
 * outlive all labels using them. GPU operations require their owning GL context thread.
 * <pre>{@code
 * SlugLabel label = new SlugLabel(font, slugBatch, "Score: 0", 24f);
 * container.add(label);
 * label.text("Score: 100").color(Color.WHITE);
 * }</pre>
 * The shared batch must be idle when this node draws. Container clipping is forwarded
 * for glyph rejection and CPU quad cropping in the text's world-coordinate space.
 * @author Albert Beaupre
 */
public final class SlugLabel extends UINode {
    private final SlugFont font; // Borrowed live-curve font, which must outlive this label.
    private final SlugBatch renderer; // Borrowed shared batch, required to be idle on entry to draw.
    private final SlugTextRun run; // Owned reusable CPU glyph layout borrowing the font.
    private final Matrix4f projection = new Matrix4f(); // Scratch copy of the window projection for the Slug pass.
    private final int[] viewport = new int[4]; // Reusable four-component GL viewport query storage.
    private Color color = Color.WHITE; // Borrowed mutable tint applied to subsequent draws.

    /**
     * Creates retained text and initializes measured node dimensions from its layout.
     * @param font borrowed font supplying live curve data and metrics
     * @param renderer borrowed batch, idle whenever this label draws
     * @param text initial text; null is normalized to empty
     * @param size finite nonnegative world units per em
     * @throws NullPointerException if font or renderer is null
     * @throws IllegalArgumentException if size is negative or nonfinite
     */
    public SlugLabel(SlugFont font, SlugBatch renderer, String text, float size) {
        this.font = Objects.requireNonNull(font, "font");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        run = font.createRun(text, size);
        measure();
    }

    /**
     * Changes text and updates layout dimensions; unchanged content reuses glyph arrays.
     * @param text replacement text, with null treated as empty
     * @return this label for configuration chaining
     */
    public SlugLabel text(String text) { run.rebuild(text, run.size()); measure(); return this; }
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
    public SlugLabel size(float size) { run.rebuild(run.text(), size); measure(); return this; }
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
    public SlugLabel color(Color color) { this.color = Objects.requireNonNull(color, "color"); return this; }
    /**
     * Updates explicit layout width and height from the retained run's measured extent.
     */
    private void measure() { getLayout().width(run.width()).height(run.height()); }

    /**
     * Requires no additional resources because construction already created the CPU run.
     */
    @Override public void onCreate() { }
    /**
     * Leaves the borrowed font and renderer alive for other labels; their owner disposes them.
     */
    @Override public void onDestroy() { }
    /**
     * Performs no timed updates; text layout changes only through explicit setters.
     * @param delta elapsed update time supplied by the UI lifecycle
     */
    @Override public void update(float delta) { }

    /**
     * Flushes preceding texture geometry, renders retained curves using the window projection
     * and current viewport, then restores the Slug pass state. Applies batch translation
     * and forwards its clip rectangle. Empty, zero-sized, and fully transparent labels skip
     * work. A finally block cancels unfinished submission and clears the shared CPU clip.
     * @param batch active UI texture batch whose pending geometry must precede this label
     */
    @Override public void draw(TextureBatch batch) {
        if (run.glyphCount() == 0 || color.a() <= 0 || run.size() == 0) return;
        batch.flush();
        projection.set(Window.getProjectionMatrix());
        glGetIntegerv(GL_VIEWPORT, viewport);
        renderer.begin(projection, viewport[2], viewport[3]);
        try {
            if (batch.isClipEnabled()) renderer.setClip(batch.getClipX(), batch.getClipY(), batch.getClipWidth(), batch.getClipHeight());
            else renderer.clearClip();
            run.draw(renderer, getRenderX() + batch.getTranslationX(),
                    getRenderY() + getHeight() - font.ascent() * run.size() + batch.getTranslationY(), color);
            renderer.end();
        } finally {
            renderer.cancel();
            renderer.clearClip();
        }
    }
}
