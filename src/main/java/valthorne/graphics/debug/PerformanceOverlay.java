package valthorne.graphics.debug;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureRegion;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Small atlas-based FPS/frame-time display. Create/dispose on the GL thread.
 * Printable ASCII is rasterized once with AWT into an owned texture atlas. Frame
 * timing measures intervals between calls, independently of simulation time or GPU
 * queries, and updates the display after at least a quarter-second sample window.
 * Drawing reuses atlas regions; sampling still allocates a formatted string when
 * the displayed statistics change.
 *
 * @author Albert Beaupre
 */
public final class PerformanceOverlay implements AutoCloseable {
    private final Texture atlas; // Owned rasterized printable-ASCII atlas.
    private final TextureRegion[] glyphs = new TextureRegion[95]; // Borrowed regions for character codes 32 through 126.
    private long lastFrame, sampleStart; // Last call and sampling-window start timestamps in nanoseconds.
    private int sampleFrames; // Completed frame intervals counted in the current sample window.
    private String text = "FPS --  |  -- MS"; // Latest formatted statistics, or the initial placeholder.

    /**
     * Rasterizes 95 printable ASCII glyphs with a bold 22-point monospaced AWT font,
     * encodes the atlas as PNG, and uploads one texture. Glyph cells are 18 by 28
     * pixels; no application font resource is borrowed.
     *
     * @throws UncheckedIOException if atlas PNG encoding reports an I/O error
     */
    public PerformanceOverlay() {
        BufferedImage image = new BufferedImage(16 * 18, 6 * 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        for (int i = 0; i < glyphs.length; i++) {
            int x = i % 16 * 18, y = i / 16 * 28;
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(String.valueOf((char) (i + 32)), x + 2, y + 24);
            g.setColor(new Color(150, 255, 213));
            g.drawString(String.valueOf((char) (i + 32)), x, y + 22);
        }
        g.dispose();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            atlas = new Texture(bytes.toByteArray());
        } catch (IOException e) {throw new UncheckedIOException(e);}
        for (int i = 0; i < glyphs.length; i++)
            glyphs[i] = new TextureRegion(atlas, i % 16 * 18, image.getHeight() - (i / 16 + 1) * 28, 18, 28);
    }

    /**
     * Call once per presented frame; measures wall time, independently of simulation delta.
     * The first call initializes timing without counting a frame interval. Once a
     * sample spans at least 0.25 seconds, computes average FPS and milliseconds per
     * interval and starts a new sample. Does not query actual display presentation.
     */
    public void frame() {
        long now = System.nanoTime();
        if (lastFrame == 0) {
            lastFrame = sampleStart = now;
            return;
        }
        lastFrame = now;
        sampleFrames++;
        double seconds = (now - sampleStart) * 1e-9;
        if (seconds >= .25) {
            text = String.format(Locale.ROOT, "FPS %.0f  |  %.2f MS", sampleFrames / seconds, seconds * 1000 / sampleFrames);
            sampleFrames = 0;
            sampleStart = now;
        }
    }

    /**
     * Draw inside an active 2D TextureBatch. No font rasterization or texture upload per frame.
     * Uses the most recently sampled text, so calling draw does not advance timing.
     *
     * @param batch active batch with the desired projection
     * @param x left coordinate in batch space
     * @param y glyph-quad origin Y in batch space
     */
    public void draw(TextureBatch batch, float x, float y) {drawText(batch, text, x, y);}

    /**
     * Draws printable ASCII using 18-by-28 quads with a 14-unit advance. Unsupported
     * characters leave an advance-sized gap; newlines do not create additional rows.
     * Coordinates follow the active batch projection and current rendering state.
     *
     * @param batch active texture batch
     * @param value text to draw
     * @param x left origin in batch coordinates
     * @param y quad origin Y in batch coordinates
     * @throws NullPointerException if value is null
     */
    public void drawText(TextureBatch batch, String value, float x, float y) {
        for (int i = 0; i < value.length(); i++) {
            int glyph = value.charAt(i) - 32;
            if (glyph >= 0 && glyph < glyphs.length) batch.draw(glyphs[glyph], x + i * 14, y, 18, 28);
        }
    }

    /**
     * Releases the owned atlas texture through its disposal path. Glyph regions
     * become unusable afterward; close on the owning graphics thread after drawing ends.
     */
    @Override
    public void close() {atlas.dispose();}
}
