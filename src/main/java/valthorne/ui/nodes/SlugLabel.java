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
 * Retained curve-rendered text usable under regular or NanoVG containers.
 * Borrows its font and shared batch: dispose those once, after all labels are gone.
 * All rendering and GPU resource operations must use the owning GL context thread.
 */
public final class SlugLabel extends UINode {
    private final SlugFont font;
    private final SlugBatch renderer;
    private final SlugTextRun run;
    private final Matrix4f projection = new Matrix4f();
    private final int[] viewport = new int[4];
    private Color color = Color.WHITE;

    public SlugLabel(SlugFont font, SlugBatch renderer, String text, float size) {
        this.font = Objects.requireNonNull(font, "font");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        run = font.createRun(text, size);
        measure();
    }

    /** Updates retained layout; unchanged content does not rebuild glyph arrays. */
    public SlugLabel text(String text) { run.rebuild(text, run.size()); measure(); return this; }
    public String text() { return run.text(); }
    public SlugLabel size(float size) { run.rebuild(run.text(), size); measure(); return this; }
    public float size() { return run.size(); }
    /** Borrows a mutable tint, like the other UI text nodes. */
    public SlugLabel color(Color color) { this.color = Objects.requireNonNull(color, "color"); return this; }
    private void measure() { getLayout().width(run.width()).height(run.height()); }

    @Override public void onCreate() { }
    @Override public void onDestroy() { }
    @Override public void update(float delta) { }

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
