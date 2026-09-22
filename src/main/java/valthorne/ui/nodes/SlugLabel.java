package valthorne.ui.nodes;

import org.joml.Matrix4f;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugBatch;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;
import valthorne.ui.SlugRenderable;
import valthorne.ui.UIRoot;

import java.util.Objects;
import static org.lwjgl.opengl.GL11.*;

/**
 * Retained Slug text for regular or NanoVG UI containers. Text and size changes refresh
 * a reusable glyph layout and the node's measured dimensions. Normal UI drawing uses the
 * font's cached atlas through the existing texture batch; {@link #liveCurves(boolean)} can
 * opt unusually large text into curve evaluation. The font is borrowed and must outlive
 * all labels using it. GPU operations require their owning graphics-context thread.
 * <pre>{@code
 * SlugLabel label = new SlugLabel(font, "Score: 0", 24f);
 * container.add(label);
 * label.text("Score: 100").color(Color.WHITE);
 * }</pre>
 * Container clipping is forwarded for glyph rejection and CPU quad cropping in the
 * text's world-coordinate space. The legacy constructor can still select a caller-owned
 * batch, which the UI context manages without taking ownership.
 * @author Albert Beaupre
 */
public final class SlugLabel extends UINode implements SlugRenderable {
    private final SlugFont font; // Borrowed live-curve font, which must outlive this label.
    private final SlugBatch renderer; // Optional compatibility renderer; null selects the root-owned batch.
    private final SlugTextRun run; // Owned reusable CPU glyph layout borrowing the font.
    private final Matrix4f projection = new Matrix4f(); // Scratch copy of the window projection for the Slug pass.
    private final int[] viewport = new int[4]; // Reusable four-component GL viewport query storage.
    private Color color = Color.WHITE; // Borrowed mutable tint applied to subsequent draws.
    private boolean liveCurves; // Explicit quality mode; cached rendering is the fast default.

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
     * Creates retained curve text using the owning UI root's shared renderer. This is
     * the preferred constructor: callers only manage the font, while {@link UIRoot}
     * batches and disposes the renderer automatically.
     *
     * @param font borrowed font supplying live curve data and metrics
     * @param text initial text; null is normalized to empty
     * @param size finite nonnegative world units per em
     */
    public SlugLabel(SlugFont font, String text, float size) {
        this.font = Objects.requireNonNull(font, "font");
        renderer = null;
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
     * Selects live curve evaluation or the default cached-atlas path. Live curves
     * are intended for unusually large or transformed text; cached rendering is
     * optimized for normal UI labels.
     *
     * @param enabled true for live curves, false for cached rendering
     * @return this label
     */
    public SlugLabel liveCurves(boolean enabled) { liveCurves = enabled; return this; }

    /** @return whether live curve rendering is enabled */
    public boolean isLiveCurves() { return liveCurves; }

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
     * Draws from the shared raster cache by default. Legacy caller-owned renderers use
     * the live path when explicitly selected; the UI render context handles root-owned
     * live rendering through {@link #drawSlug(SlugBatch, TextureBatch)}.
     * @param batch active UI texture batch whose pending geometry must precede this label
     */
    @Override public void draw(TextureBatch batch) {
        if (!liveCurves) {
            font.drawCached(batch, run.text(), getRenderX() + batch.getTranslationX(),
                    getRenderY() + batch.getTranslationY(), run.size(), color);
            return;
        }
        if (renderer == null) {
            throw new IllegalStateException("Render this SlugLabel through its owning UIRoot.");
        }
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

    /** Returns the optional compatibility renderer, or null for the root-owned batch. */
    @Override public SlugBatch getSlugBatch() { return renderer; }

    /** Selects the specialized backend only for explicit live-curve rendering. */
    @Override public boolean usesSlugBackend() { return liveCurves; }

    /** Submits an explicitly live label into the render context's active Slug pass. */
    @Override public void drawSlug(SlugBatch slugBatch, TextureBatch uiBatch) {
        if (run.glyphCount() == 0 || color.a() <= 0 || run.size() == 0) return;
        if (uiBatch.isClipEnabled()) {
            // SlugBatch has no nested clip stack; every node writes the current effective clip.
            slugBatch.setClip(uiBatch.getClipX(), uiBatch.getClipY(), uiBatch.getClipWidth(), uiBatch.getClipHeight());
        } else slugBatch.clearClip();
        run.draw(slugBatch, getRenderX() + uiBatch.getTranslationX(),
                getRenderY() + getHeight() - font.ascent() * run.size() + uiBatch.getTranslationY(), color);
    }
}
