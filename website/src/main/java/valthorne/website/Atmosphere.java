package valthorne.website;

import valthorne.ui.Canvas2D;

/**
 * Static, asset-free atmosphere for the public website's dark theme.
 *
 * <p>Dim construction lines, a handful of stars, and smooth illuminated ribbons
 * sit outside the central reading area. The decoration uses only Valthorne's
 * portable Canvas2D primitives. It has no clock, randomness, parallax, input,
 * or animation loop, so reduced-motion visitors see exactly the same texture.
 * Resizing simply redraws the geometry in logical viewport pixels.</p>
 *
 * <p>Curve samples and star positions are reused, rather than allocating meshes
 * on each frame. The main ribbon takes 40 segments; this keeps the decoration
 * inexpensive when the lab requests additional frames for its own animation.</p>
 */
public final class Atmosphere {
    private static final int BACKGROUND = 0x030a12;
    private static final int TEAL = 0x58eee0;
    private static final int BLUE = 0x70cbff;
    private static final int SEGMENTS = 40;
    private static final float[] CURVE_X = new float[SEGMENTS + 1];
    private static final float[] CURVE_Y = new float[SEGMENTS + 1];
    private static final float[] SWEEP_X = new float[SEGMENTS + 1];
    private static final float[] SWEEP_Y = new float[SEGMENTS + 1];
    private static final int GLOW_LAYERS = 64, ELLIPSE_SEGMENTS = 32;
    private static final float[] ELLIPSE_X = new float[ELLIPSE_SEGMENTS + 1];
    private static final float[] ELLIPSE_Y = new float[ELLIPSE_SEGMENTS + 1];
    private static final int[] GLOW_COLORS = new int[GLOW_LAYERS];
    private static final int[] COMPACT_GLOW_COLORS = new int[GLOW_LAYERS];
    // Normalized x/y and radius. None sit directly behind the hero's headline.
    private static final float[] STARS = {
            .075f, .18f, 5, .16f, .41f, 4, .09f, .71f, 3,
            .14f, .10f, 3, .18f, .88f, 4, .84f, .12f, 4,
            .86f, .29f, 4, .92f, .13f, 5, .96f, .51f, 3,
            .87f, .79f, 5, .91f, .93f, 3, .05f, .94f, 3
    };

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / SEGMENTS;
            CURVE_X[i] = cubic(.57f, .79f, .98f, 1.15f, t);
            CURVE_Y[i] = cubic(1.24f, .88f, .40f, .18f, t);
            SWEEP_X[i] = cubic(-.15f, .25f, .75f, 1.16f, t);
            SWEEP_Y[i] = cubic(1.16f, .94f, .94f, .67f, t);
        }
        for (int i = 0; i <= ELLIPSE_SEGMENTS; i++) {
            double angle = i * (Math.PI * 2 / ELLIPSE_SEGMENTS);
            ELLIPSE_X[i] = (float) Math.cos(angle);
            ELLIPSE_Y[i] = (float) Math.sin(angle);
        }
        for (int i = 0; i < GLOW_LAYERS; i++) {
            float intensity = (float) i / (GLOW_LAYERS - 1);
            GLOW_COLORS[i] = blend(BACKGROUND, 0x174651, intensity * .42f);
            COMPACT_GLOW_COLORS[i] = blend(BACKGROUND, 0x174651, intensity * .23f);
        }
    }

    private Atmosphere() {}

    /**
     * Paints a complete opaque background, without changing text state.
     *
     * @param vg active Canvas2D context
     * @param width logical viewport width in CSS pixels
     * @param height logical viewport height in CSS pixels
     * @param scroll document scroll position, intentionally unused: decoration
     *               stays fixed and introduces no scroll-linked motion
     */
    public static void draw(long vg, float width, float height, float scroll) {
        if (width <= 0 || height <= 0) return;
        Canvas2D.beginPath(vg);
        Canvas2D.rect(vg, 0, 0, width, height);
        Canvas2D.color(vg, BACKGROUND, 1);
        Canvas2D.fill(vg);

        boolean compact = width < 700;
        float strength = compact ? .50f : 1;
        glow(vg, width * .83f, height * .19f,
                width * .40f, height * .66f, strength);
        grid(vg, width, height, strength);
        ribbon(vg, SWEEP_X, SWEEP_Y, width, height, .13f,
                0x102c39, BLUE, strength * .30f);
        ribbon(vg, CURVE_X, CURVE_Y, width, height, compact ? .17f : .12f,
                0x102c39, TEAL, strength * .78f);
        stars(vg, width, height, strength);
        Canvas2D.strokeWidth(vg, 1);
    }

    /**
     * Fine palette steps soften the glow without large concentric bands. Colors
     * are blended against the known background before drawing, avoiding repeated
     * low-alpha rounding on an eight-bit canvas. Precomputed unit-circle points
     * and colors avoid trigonometry and allocations during frame painting.
     */
    private static void glow(long vg, float cx, float cy, float rx, float ry,
                             float strength) {
        int[] colors = strength < 1 ? COMPACT_GLOW_COLORS : GLOW_COLORS;
        for (int layer = 0; layer < GLOW_LAYERS; layer++) {
            float radius = 1 - layer * (.72f / (GLOW_LAYERS - 1));
            Canvas2D.beginPath(vg);
            for (int i = 0; i <= ELLIPSE_SEGMENTS; i++) {
                float x = cx + ELLIPSE_X[i] * rx * radius;
                float y = cy + ELLIPSE_Y[i] * ry * radius;
                if (i == 0) Canvas2D.moveTo(vg, x, y);
                else Canvas2D.lineTo(vg, x, y);
            }
            Canvas2D.color(vg, colors[layer], 1);
            Canvas2D.fill(vg);
        }
    }

    /** Short segments let the grid gently fade toward all four boundaries. */
    private static void grid(long vg, float width, float height, float strength) {
        int columns = width < 700 ? 6 : 12;
        int rows = 8;
        float left = width * .17f, top = height * .015f;
        float cellWidth = width * .68f / columns, cellHeight = height * .66f / rows;
        Canvas2D.strokeWidth(vg, .7f);
        for (int row = 0; row <= rows; row++) {
            for (int column = 0; column <= columns; column++) {
                float edge = Math.min(1, Math.min(column, columns - column) / 2f);
                float fade = Math.max(0, 1 - Math.abs(row - 2.5f) / 5.5f);
                Canvas2D.color(vg, 0x2c7983, .11f * edge * fade * strength);
                float x = left + column * cellWidth, y = top + row * cellHeight;
                Canvas2D.beginPath(vg);
                if (column < columns) {
                    Canvas2D.moveTo(vg, x, y);
                    Canvas2D.lineTo(vg, x + cellWidth, y);
                }
                if (row < rows) {
                    Canvas2D.moveTo(vg, x, y);
                    Canvas2D.lineTo(vg, x, y + cellHeight);
                }
                Canvas2D.stroke(vg);
            }
        }
    }

    /** A shaded curved band, a narrow bright rim, and two quiet trailing seams. */
    private static void ribbon(long vg, float[] xs, float[] ys, float width,
                               float height, float depth, int body, int light,
                               float strength) {
        Canvas2D.beginPath(vg);
        for (int i = 0; i <= SEGMENTS; i++) point(vg, xs, ys, i, width, height, 0, i == 0);
        for (int i = SEGMENTS; i >= 0; i--) point(vg, xs, ys, i, width, height, depth, false);
        point(vg, xs, ys, 0, width, height, 0, false);
        Canvas2D.color(vg, body, .80f * strength);
        Canvas2D.fill(vg);

        // Wide translucent strokes form a restrained halo around the same curve.
        curve(vg, xs, ys, width, height, 0, light, .025f * strength, 18);
        curve(vg, xs, ys, width, height, 0, light, .045f * strength, 9);
        curve(vg, xs, ys, width, height, 0, light, .12f * strength, 4);
        curve(vg, xs, ys, width, height, 0, light, .78f * strength, 1.5f);
        curve(vg, xs, ys, width, height, .012f, BLUE, .20f * strength, 1);
        curve(vg, xs, ys, width, height, depth * .35f, BLUE, .09f * strength, 1);
        curve(vg, xs, ys, width, height, depth, BLUE, .18f * strength, 1);
    }

    private static void curve(long vg, float[] xs, float[] ys, float width,
                              float height, float offset, int color, float alpha,
                              float thickness) {
        Canvas2D.beginPath(vg);
        for (int i = 0; i <= SEGMENTS; i++) point(vg, xs, ys, i, width, height, offset, i == 0);
        Canvas2D.color(vg, color, alpha);
        Canvas2D.strokeWidth(vg, thickness);
        Canvas2D.stroke(vg);
    }

    private static void point(long vg, float[] xs, float[] ys, int index,
                              float width, float height, float offset, boolean first) {
        float x = (xs[index] + offset) * width, y = ys[index] * height;
        if (first) Canvas2D.moveTo(vg, x, y);
        else Canvas2D.lineTo(vg, x, y);
    }

    /** Fine four-point stars keep the field deliberate and free of visual noise. */
    private static void stars(long vg, float width, float height, float strength) {
        for (int i = 0; i < STARS.length; i += 3) {
            float x = STARS[i] * width, y = STARS[i + 1] * height;
            float r = STARS[i + 2] * (width < 700 ? .7f : 1);
            Canvas2D.beginPath(vg);
            Canvas2D.moveTo(vg, x, y - r);
            Canvas2D.lineTo(vg, x + .65f, y - .65f);
            Canvas2D.lineTo(vg, x + r, y);
            Canvas2D.lineTo(vg, x + .65f, y + .65f);
            Canvas2D.lineTo(vg, x, y + r);
            Canvas2D.lineTo(vg, x - .65f, y + .65f);
            Canvas2D.lineTo(vg, x - r, y);
            Canvas2D.lineTo(vg, x - .65f, y - .65f);
            Canvas2D.lineTo(vg, x, y - r);
            Canvas2D.color(vg, i % 6 == 0 ? 0xc4f4f2 : TEAL, .28f * strength);
            Canvas2D.fill(vg);
        }
    }

    private static float cubic(float a, float b, float c, float d, float t) {
        float s = 1 - t;
        return s * s * s * a + 3 * s * s * t * b + 3 * s * t * t * c + t * t * t * d;
    }

    private static int blend(int from, int to, float amount) {
        int r = Math.round(((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int g = Math.round(((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int b = Math.round((from & 255) * (1 - amount) + (to & 255) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
