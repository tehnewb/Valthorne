package valthorne.ui;

import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.texture.TextureBatch;

import java.util.LinkedHashMap;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;

/**
 * Owns bounded, physical-size-specific hinted Slug geometry for one UI root.
 * Exact source fonts and logical HarfBuzz layouts are borrowed. At most 32 hinted
 * views and 16 MiB of GPU geometry are retained; one oversized active view may
 * exceed the byte budget. CPU layouts/staging are additional. Each view owns its
 * curve/band textures and prepared runs.
 * Stable draws allocate neither cache keys nor hinted geometry. A cache miss
 * flushes queued drawing before evicting GPU resources. Rotated, zoomed, or
 * nonuniformly scaled world text retains ordinary scalable Slug outlines.
 */
final class SlugUIText implements AutoCloseable {
    /**
     * Maximum exact-font/physical-size views retained by one root.
     */
    private static final int MAX_VIEWS = 32;
    /**
     * Maximum cached hinted GPU geometry per root, excluding CPU staging/layouts.
     */
    private static final long MAX_GEOMETRY_BYTES = 16L * 1024 * 1024;
    private final UIRoot root; // Borrowed owner whose viewport defines logical UI units.
    private final int[] viewport = new int[4]; // Reused physical framebuffer viewport query storage.
    private final SlugHintKey lookup = new SlugHintKey(); // Reused mutable cache lookup, never inserted.
    private final LinkedHashMap<SlugHintKey, SlugFont> views = new LinkedHashMap<>(16, .75f, true); // Owned least-recently-used hinted geometry views.
    private long retainedBytes; // Accounted row-padded geometry bytes across cached views.

    /**
     * Creates a lazy GPU-independent UI resource owner.
     *
     * @param root owning logical UI root
     */
    SlugUIText(UIRoot root) { this.root = root; }

    /**
     * Draws using actual physical size while preserving logical shaping and phase.
     * Caller transform policy has already excluded rotations and camera zoom.
     *
     * @param batch active painter-order batch
     * @param source exact borrowed font
     * @param text complete UTF-16 text
     * @param x logical baseline X before batch translation
     * @param baseline logical baseline Y before batch translation
     * @param size logical DIPs per em
     * @param color optional foreground override
     * @return NONE when handled, otherwise the reason scalable geometry is needed
     */
    UITextFallbackReason draw(TextureBatch batch, SlugFont source, String text, float x, float baseline, float size, Color color) {
        float[] projection = Window.getProjectionMatrix();
        if (projection[1] != 0 || projection[4] != 0 || projection[0] <= 0 || projection[5] <= 0) return UITextFallbackReason.UNSUPPORTED_PROJECTION;
        glGetIntegerv(GL_VIEWPORT, viewport);
        var selectedViewport = root.getViewport();
        float width = selectedViewport == null ? Window.getWidth() : selectedViewport.getWorldWidth();
        float height = selectedViewport == null ? Window.getHeight() : selectedViewport.getWorldHeight();
        float sx = viewport[2] / width;
        float sy = viewport[3] / height;
        if (viewport[2] <= 0 || viewport[3] <= 0) {
            root.recordTextRendering(UITextRenderingMode.NOT_DRAWN, UITextFallbackReason.EMPTY_VIEWPORT, sx, sy);
            return UITextFallbackReason.NONE;
        }
        float physicalSize = size * sy;
        if (!Float.isFinite(sx) || !Float.isFinite(sy) || Math.abs(sx - sy) > Math.max(sx, sy) * .00001f || physicalSize < 1 || physicalSize > 4096) return UITextFallbackReason.UNSUPPORTED_SCALE;
        if (source.isDisposed()) throw new IllegalStateException("Slug font is disposed");
        int quantizedSize = Math.round(physicalSize * 64);
        lookup.set(source, quantizedSize);
        SlugFont hinted = views.get(lookup);
        if (hinted == null) {
            batch.flush();
            hinted = source.createHinted(quantizedSize / 64f);
            views.put(new SlugHintKey(lookup), hinted);
            retainedBytes += hinted.geometryBytes();
        }
        long previousBytes = hinted.geometryBytes();
        hinted.draw(batch, text, x, baseline, size, color);
        retainedBytes += hinted.geometryBytes() - previousBytes;
        if (views.size() > MAX_VIEWS || retainedBytes > MAX_GEOMETRY_BYTES && views.size() > 1) {
            batch.flush();
            while (views.size() > MAX_VIEWS || retainedBytes > MAX_GEOMETRY_BYTES && views.size() > 1) {
                SlugFont removed = views.pollFirstEntry().getValue();
                retainedBytes -= removed.geometryBytes();
                removed.dispose();
            }
        }
        root.recordTextRendering(UITextRenderingMode.SLUG_HINTED, UITextFallbackReason.NONE, sx, sy);
        return UITextFallbackReason.NONE;
    }

    @Override
    public void close() {
        for (SlugFont view : views.values()) view.dispose();
        views.clear();
        retainedBytes = 0;
    }
}
