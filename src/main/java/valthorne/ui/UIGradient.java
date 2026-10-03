package valthorne.ui;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGPaint;
import valthorne.graphics.Color;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Two-stop linear UI gradient with endpoints expressed as fractions of its bounds.
 * It can be used as any regular UI {@link Drawable} or applied to a NanoVG path.
 * Regular rendering creates one small texture on the first draw and reuses it;
 * call {@link #close()} on the graphics thread when the gradient is no longer used.
 */
public final class UIGradient implements Drawable, AutoCloseable {
    private final Color start; // Color at the first endpoint.
    private final Color end; // Color at the second endpoint.
    private final float startX; // First endpoint X as a fraction of the painted width.
    private final float startY; // First endpoint Y as a fraction of the painted height.
    private final float endX; // Second endpoint X as a fraction of the painted width.
    private final float endY; // Second endpoint Y as a fraction of the painted height.
    private Texture texture; // Lazily uploaded raster for texture-batch painting.
    private boolean closed; // Whether this paint has released its texture.

    /**
     * Creates a linear gradient whose endpoints use normalized local coordinates.
     *
     * @param start color at the first endpoint
     * @param end color at the second endpoint
     * @param startX first endpoint X fraction
     * @param startY first endpoint Y fraction
     * @param endX second endpoint X fraction
     * @param endY second endpoint Y fraction
     */
    public UIGradient(Color start, Color end, float startX, float startY, float endX, float endY) {
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        if (!Float.isFinite(startX) || !Float.isFinite(startY) || !Float.isFinite(endX) || !Float.isFinite(endY)
                || startX == endX && startY == endY)
            throw new IllegalArgumentException("Gradient endpoints must be finite and distinct.");
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    /**
     * Creates a gradient from the top edge to the bottom edge.
     *
     * @param top color at the top
     * @param bottom color at the bottom
     * @return vertical gradient
     */
    public static UIGradient vertical(Color top, Color bottom) {
        /*
         * Normalized endpoints keep the same direction at every element size.
         */
        return new UIGradient(top, bottom, 0f, 0f, 0f, 1f);
    }

    /**
     * Creates a gradient from the left edge to the right edge.
     *
     * @param left color at the left
     * @param right color at the right
     * @return horizontal gradient
     */
    public static UIGradient horizontal(Color left, Color right) {
        /*
         * Normalized endpoints keep the same direction at every element size.
         */
        return new UIGradient(left, right, 0f, 0f, 1f, 0f);
    }

    /**
     * Applies this gradient to the current NanoVG fill path.
     *
     * @param vg active NanoVG context
     * @param x painted bounds X
     * @param y painted bounds Y
     * @param width painted bounds width
     * @param height painted bounds height
     */
    public void fill(long vg, float x, float y, float width, float height) {
        applyFill(vg, x, y, width, height);
        nvgFill(vg);
    }

    /**
     * Selects this gradient as the NanoVG fill paint for a path or text draw.
     *
     * @param vg active NanoVG context
     * @param x painted bounds X
     * @param y painted bounds Y
     * @param width painted bounds width
     * @param height painted bounds height
     */
    public void applyFill(long vg, float x, float y, float width, float height) {
        NVGPaint paint = NVGPaint.calloc();
        try {
            nvgLinearGradient(vg, x + startX * width, y + startY * height,
                    x + endX * width, y + endY * height,
                    NanoUtility.color1(start), NanoUtility.color2(end), paint);
            nvgFillPaint(vg, paint);
        } finally {
            paint.free();
        }
    }

    /**
     * Applies this gradient to the current NanoVG stroke path.
     *
     * @param vg active NanoVG context
     * @param x painted bounds X
     * @param y painted bounds Y
     * @param width painted bounds width
     * @param height painted bounds height
     */
    public void stroke(long vg, float x, float y, float width, float height) {
        applyStroke(vg, x, y, width, height);
        nvgStroke(vg);
    }

    /**
     * Selects this gradient as the NanoVG stroke paint for the next path draw.
     *
     * @param vg active NanoVG context
     * @param x painted bounds X
     * @param y painted bounds Y
     * @param width painted bounds width
     * @param height painted bounds height
     */
    public void applyStroke(long vg, float x, float y, float width, float height) {
        NVGPaint paint = NVGPaint.calloc();
        try {
            nvgLinearGradient(vg, x + startX * width, y + startY * height,
                    x + endX * width, y + endY * height,
                    NanoUtility.color1(start), NanoUtility.color2(end), paint);
            nvgStrokePaint(vg, paint);
        } finally {
            paint.free();
        }
    }

    /**
     * Draws this gradient through the texture batch. The raster is created once on
     * the render thread and stretched over the requested bounds.
     */
    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint) {
        if (closed) throw new IllegalStateException("Gradient has been closed.");
        if (texture == null) createTexture();
        batch.draw(texture, x, y, width, height, originX, originY, rotation, tint);
    }

    /**
     * Draws a rectangular gradient outline with a fixed thickness. Each side
     * samples its corresponding part of the same gradient, keeping corners and
     * edges continuous as the bounds change.
     *
     * @param batch active texture batch
     * @param x outline X
     * @param y outline Y
     * @param width outline width
     * @param height outline height
     * @param thickness inset border thickness
     */
    public void drawBorder(TextureBatch batch, float x, float y, float width, float height, float thickness) {
        if (closed) throw new IllegalStateException("Gradient has been closed.");
        if (width <= 0f || height <= 0f || thickness <= 0f) return;
        if (texture == null) createTexture();
        float side = Math.min(thickness, width * .5f);
        float top = Math.min(thickness, height * .5f);
        float u = side / width;
        float v = top / height;
        batch.drawUV(texture, x, y, width, top, 0f, 0f, 1f, v, null);
        batch.drawUV(texture, x, y + height - top, width, top, 0f, 1f - v, 1f, 1f, null);
        if (height > top * 2f) {
            batch.drawUV(texture, x, y + top, side, height - top * 2f, 0f, v, u, 1f - v, null);
            batch.drawUV(texture, x + width - side, y + top, side, height - top * 2f, 1f - u, v, 1f, 1f - v, null);
        }
    }

    /**
     * Uploads a reusable 128-pixel square gradient. Color interpolation is done
     * once, rather than per frame or per widget draw.
     */
    private void createTexture() {
        final int size = 128;
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        float dx = endX - startX;
        float dy = endY - startY;
        float denominator = dx * dx + dy * dy;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float position = Math.clamp((((x + .5f) / size - startX) * dx + ((size - y - .5f) / size - startY) * dy) / denominator, 0f, 1f);
                pixels.put((byte) (255f * (start.r() + (end.r() - start.r()) * position)));
                pixels.put((byte) (255f * (start.g() + (end.g() - start.g()) * position)));
                pixels.put((byte) (255f * (start.b() + (end.b() - start.b()) * position)));
                pixels.put((byte) (255f * (start.a() + (end.a() - start.a()) * position)));
            }
        }
        pixels.flip();
        texture = new Texture(new TextureData(pixels, size, size));
        texture.setFilter(TextureFilter.LINEAR);
    }

    /**
     * Releases the optional regular-renderer texture. NanoVG use owns no resources.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (texture != null) texture.dispose();
    }

    /**
     * Returns the intrinsic raster width used by the Drawable API.
     *
     * @return raster width
     */
    @Override
    public float getWidth() {
        return 128f;
    }

    /**
     * Returns the intrinsic raster height used by the Drawable API.
     *
     * @return raster height
     */
    @Override
    public float getHeight() {
        return 128f;
    }
}
