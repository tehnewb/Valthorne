package valthorne.ui;

import valthorne.graphics.Color;
import valthorne.graphics.font.TextRun;
import valthorne.graphics.texture.TextureBatch;

/**
 * Owner-thread paint configuration for one label's optional outline and shadow.
 * Colors are copied and disk offsets are cached when the radius changes. Drawing
 * reuses the caller's shaped run, batch and clipping without modifying its font.
 * Effects do not expand layout, selection or hit bounds. Offsets and radius use
 * logical UI units; positive shadow Y points down. No native resources are owned.
 * Enabled outlines submit one extra glyph copy per disk offset; use small radii
 * for UI. Reused tint values are internal scratch and must not be retained.
 */
public final class NanoTextEffects {
    /**
     * Largest radius, bounding disk storage and glyph-copy submissions.
     */
    private static final int MAX_OUTLINE_WIDTH = 16;
    private final Color outlineColor = new Color(0f, 0f, 0f, 1f); // Owned copied outline tint.
    private final Color shadowColor = new Color(0f, 0f, 0f, .5f); // Owned copied shadow tint.
    private final Color scratch = new Color(0f, 0f, 0f, 1f); // Reused color submitted immediately by either backend.
    private int[] offsets; // Cached outline disk offset pairs, null until first configuration.
    private int width = 1; // Configured logical outline radius.
    private float shadowX = 2f; // Logical horizontal shadow offset, positive right.
    private float shadowY = 2f; // Logical vertical shadow offset, positive down.
    private boolean outline; // Whether the outline is currently enabled.
    private boolean shadow; // Whether the shadow is currently enabled.

    /**
     * Creates disabled effect configuration; no atlas or native resource is created.
     */
    public NanoTextEffects() {
    }

    /**
     * Configures and enables a round outline, copying a supplied color.
     * @param radius logical radius from 1 through 16
     * @param color copied tint, or null to retain the previous tint
     * @throws IllegalArgumentException if radius is outside the supported range
     */
    public void outline(int radius, Color color) {
        if (radius < 1 || radius > MAX_OUTLINE_WIDTH)
            throw new IllegalArgumentException("Outline width must be between 1 and " + MAX_OUTLINE_WIDTH);
        if (offsets == null || radius != width) {
            int count = 0;
            for (int y = -radius; y <= radius; y++)
                for (int x = -radius; x <= radius; x++)
                    if ((x != 0 || y != 0) && x * x + y * y <= radius * radius) count++;
            int[] disk = new int[count * 2];
            int index = 0;
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    if ((x == 0 && y == 0) || x * x + y * y > radius * radius) continue;
                    disk[index++] = x;
                    disk[index++] = y;
                }
            }
            offsets = disk;
        }
        width = radius;
        if (color != null) outlineColor.set(color);
        outline = true;
    }

    /**
     * Enables the retained outline or disables it without discarding its configuration.
     * @param enabled whether to draw the outline
     */
    public void outline(boolean enabled) {
        if (enabled) outline(width, null);
        else outline = false;
    }

    /**
     * Configures and enables the shadow without changing text geometry.
     * @param x finite logical offset, positive right
     * @param y finite logical offset, positive down
     * @param color copied tint, or null to retain the previous tint
     * @throws IllegalArgumentException if an offset is non-finite
     */
    public void shadow(float x, float y, Color color) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Shadow offsets must be finite");
        shadowX = x;
        shadowY = y;
        if (color != null) shadowColor.set(color);
        shadow = true;
    }

    /**
     * Enables or disables the retained shadow configuration.
     * @param enabled whether to draw the shadow
     */
    public void shadow(boolean enabled) { shadow = enabled; }

    /**
     * Reports whether any effect is enabled.
     * @return whether extra paint passes are required
     */
    public boolean isEnabled() { return outline || shadow; }

    /**
     * Reports whether outlining is enabled.
     * @return outline state
     */
    public boolean isOutlineEnabled() { return outline; }

    /**
     * Reports whether the shadow is enabled.
     * @return shadow state
     */
    public boolean isShadowEnabled() { return shadow; }

    /**
     * Borrows enabled disk offsets for immediate rendering.
     * @return cached pairs, or null when the outline is disabled
     */
    int[] outlineOffsets() { return outline ? offsets : null; }

    /**
     * Returns the configured horizontal shadow offset.
     * @return logical offset, positive right
     */
    float shadowX() { return shadowX; }

    /**
     * Returns the configured vertical shadow offset.
     * @return logical offset, positive down
     */
    float shadowY() { return shadowY; }

    /**
     * Prepares the borrowed outline submission tint with the fill opacity applied.
     * @param fillAlpha corresponding text alpha
     * @return scratch color valid until the next tint request
     */
    Color outlineTint(float fillAlpha) {
        scratch.set(outlineColor);
        scratch.a(outlineColor.a() * fillAlpha);
        return scratch;
    }

    /**
     * Prepares the borrowed shadow submission tint with the fill opacity applied.
     * @param fillAlpha corresponding text alpha
     * @return scratch color valid until the next tint request
     */
    Color shadowTint(float fillAlpha) {
        scratch.set(shadowColor);
        scratch.a(shadowColor.a() * fillAlpha);
        return scratch;
    }

    /**
     * Paints shadow, outline and fill through one borrowed retained layout and clip.
     * Each copy includes the font's configured glyph styles; the font is never mutated.
     * @param layout borrowed shaped line
     * @param batch active clipped batch
     * @param x baseline X
     * @param y bottom-up baseline Y
     * @param tint text tint, or null for white
     * @param scale positive effect scale used by projected previews
     * @param pass requested paint stage, or ALL for one complete line
     */
    void draw(TextRun layout, TextureBatch batch, float x, float y, Color tint, float scale, NanoTextPass pass) {
        float alpha = tint == null ? 1f : tint.a();
        if (shadow && (pass == NanoTextPass.ALL || pass == NanoTextPass.SHADOW)) layout.draw(batch, x + shadowX * scale, y - shadowY * scale, shadowTint(alpha));
        if (outline && (pass == NanoTextPass.ALL || pass == NanoTextPass.OUTLINE)) {
            Color color = outlineTint(alpha);
            for (int i = 0; i < offsets.length; i += 2)
                layout.draw(batch, x + offsets[i] * scale, y - offsets[i + 1] * scale, color);
        }
        if (pass == NanoTextPass.ALL || pass == NanoTextPass.FILL) layout.draw(batch, x, y, tint);
    }
}
