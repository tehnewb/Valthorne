package valthorne.graphics.font.slug;

import java.util.List;

/**
 * Converts font outline commands into Slug's quadratic representation. Quadratic
 * outlines retain their controls exactly. Cubics are reduced adaptively using a
 * control-polygon error bound, preserving curved CFF contours and closed loops
 * instead of sampling them into a fixed number of straight segments.
 */
final class SlugCurves {
    /**
     * Maximum cubic-to-quadratic control error in normalized em coordinates.
     */
    private static final float CUBIC_ERROR = 1f / 32768f;
    /**
     * Bounds subdivision for malformed or extremely large outline coordinates.
     */
    private static final int MAX_DEPTH = 16;

    /**
     * Prevents construction of the outline converter.
     */
    private SlugCurves() {}

    /**
     * Appends a nondegenerate line as a midpoint quadratic.
     *
     * @param curves destination records
     * @param x0 start X
     * @param y0 start Y
     * @param x1 end X
     * @param y1 end Y
     */
    static void line(List<SlugCurve> curves, float x0, float y0, float x1, float y1) {
        /*
         * Preserve short but visible strokes; discard only identical endpoints.
         */
        if (x0 == x1 && y0 == y1) return;
        curves.add(new SlugCurve(x0, y0, (x0 + x1) * .5f, (y0 + y1) * .5f, x1, y1));
    }

    /**
     * Appends a quadratic without flattening its curvature or removing closed loops.
     *
     * @param curves destination records
     * @param x0 start X
     * @param y0 start Y
     * @param cx control X
     * @param cy control Y
     * @param x1 end X
     * @param y1 end Y
     */
    static void quadratic(List<SlugCurve> curves, float x0, float y0, float cx, float cy, float x1, float y1) {
        /*
         * Equal endpoints do not imply an empty curve when the control differs.
         */
        if (x0 == x1 && y0 == y1 && x0 == cx && y0 == cy) return;
        curves.add(new SlugCurve(x0, y0, cx, cy, x1, y1));
    }

    /**
     * Approximates a cubic with bounded-error quadratic segments.
     *
     * @param curves destination records
     * @param x0 start X
     * @param y0 start Y
     * @param ax first control X
     * @param ay first control Y
     * @param bx second control X
     * @param by second control Y
     * @param x1 end X
     * @param y1 end Y
     */
    static void cubic(List<SlugCurve> curves, float x0, float y0, float ax, float ay, float bx, float by, float x1, float y1) {
        /*
         * The recursion is cold-path geometry preparation, never a render-loop operation.
         */
        cubic(curves, x0, y0, ax, ay, bx, by, x1, y1, 0);
    }

    /**
     * Degree-reduces one cubic or bisects it when its control error is excessive.
     *
     * @param curves destination records
     * @param x0 start X
     * @param y0 start Y
     * @param ax first control X
     * @param ay first control Y
     * @param bx second control X
     * @param by second control Y
     * @param x1 end X
     * @param y1 end Y
     * @param depth bounded subdivision depth
     */
    private static void cubic(List<SlugCurve> curves, float x0, float y0, float ax, float ay, float bx, float by, float x1, float y1, int depth) {
        /*
         * Re-elevate the candidate quadratic to a cubic. The largest difference
         * between corresponding controls bounds the whole Bezier curve's error.
         */
        float qx = (3 * (ax + bx) - x0 - x1) * .25f;
        float qy = (3 * (ay + by) - y0 - y1) * .25f;
        float error = Math.max(Math.max(Math.abs((x0 + 2 * qx) / 3 - ax), Math.abs((y0 + 2 * qy) / 3 - ay)), Math.max(Math.abs((x1 + 2 * qx) / 3 - bx), Math.abs((y1 + 2 * qy) / 3 - by)));
        if (error <= CUBIC_ERROR || depth == MAX_DEPTH) {
            quadratic(curves, x0, y0, qx, qy, x1, y1);
            return;
        }
        float xa = (x0 + ax) * .5f;
        float ya = (y0 + ay) * .5f;
        float abx = (ax + bx) * .5f;
        float aby = (ay + by) * .5f;
        float bx1 = (bx + x1) * .5f;
        float by1 = (by + y1) * .5f;
        float leftX = (xa + abx) * .5f;
        float leftY = (ya + aby) * .5f;
        float rightX = (abx + bx1) * .5f;
        float rightY = (aby + by1) * .5f;
        float middleX = (leftX + rightX) * .5f;
        float middleY = (leftY + rightY) * .5f;
        cubic(curves, x0, y0, xa, ya, leftX, leftY, middleX, middleY, depth + 1);
        cubic(curves, middleX, middleY, rightX, rightY, bx1, by1, x1, y1, depth + 1);
    }
}
