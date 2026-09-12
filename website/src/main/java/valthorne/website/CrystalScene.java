package valthorne.website;

import java.util.Arrays;
import java.util.Comparator;
import valthorne.ui.Canvas2D;

/**
 * A small, asset-free 3D illustration for the Valthorne website.
 *
 * <p>The crystal, orbital bands, and pedestal are ordinary three-dimensional
 * meshes. Java rotates their vertices, computes face lighting, projects them
 * through a perspective camera, and sorts their triangles from back to front.
 * Valthorne's Canvas2D then paints the projected faces. This demonstrates a
 * custom UI illustration; it does not exercise the engine's 3D renderer,
 * physics system, or a game-performance benchmark.</p>
 *
 * <p>A fixed triangle pool avoids allocating a mesh every animation frame.
 * Like the surrounding UI renderer, this class is intended for sequential use
 * on the application's rendering thread. The caller owns animation timing and
 * input, including reduced-motion and offscreen-pause behavior.</p>
 */
public final class CrystalScene {
    private static final float TAU = (float) (Math.PI * 2);
    private static final Scene SCENE = new Scene();

    private CrystalScene() {}

    /**
     * Draws a centered, perspective-projected crystal installation.
     *
     * @param vg active Valthorne Canvas2D/NanoVG context
     * @param x left edge of the illustration in logical pixels
     * @param y top edge of the illustration in logical pixels
     * @param width available width in logical pixels
     * @param height available height in logical pixels
     * @param time animation time in seconds; leave unchanged to pause
     * @param orbit additional horizontal camera rotation in radians
     * @param tilt additional vertical camera rotation in radians, clamped here
     */
    public static void draw(long vg, float x, float y, float width, float height,
                            float time, float orbit, float tilt) {
        if (width <= 0 || height <= 0) return;
        SCENE.draw(vg, x, y, width, height, time, orbit, tilt);
    }

    /** Reusable mesh builder, camera, light, and painter for one illustration. */
    private static final class Scene {
        private static final Comparator<Triangle> BACK_TO_FRONT =
                (left, right) -> Float.compare(right.depth, left.depth);
        private final Triangle[] triangles = new Triangle[768];
        private final float[] ringA = new float[12], ringB = new float[12];
        private final float[] upper = new float[24], lower = new float[24];
        private int count;
        private float centerX, centerY, scale, yawSin, yawCos, pitchSin, pitchCos;
        private float left, top, right, bottom;

        Scene() {
            for (int i = 0; i < triangles.length; i++) triangles[i] = new Triangle();
        }

        void draw(long vg, float x, float y, float width, float height,
                  float time, float orbit, float tilt) {
            left = x; top = y; right = x + width; bottom = y + height;
            centerX = x + width / 2;
            centerY = y + height * .46f;
            scale = Math.min(width / 5.8f, height / 5.3f) * 7;
            float yaw = .55f + orbit, pitch = .22f + clamp(tilt, -.14f, .36f);
            yawSin = (float) Math.sin(yaw); yawCos = (float) Math.cos(yaw);
            pitchSin = (float) Math.sin(pitch); pitchCos = (float) Math.cos(pitch);
            count = 0;

            floor(vg);
            cylinder(1.08f, -1.60f, -1.43f, 12, 0x142538, 0x243b50);
            paint(vg);
            cylinder(.94f, -1.43f, -1.17f, 12, 0x1d3146, 0x31465a);
            paint(vg);
            cylinder(.96f, -1.20f, -1.16f, 12, 0xb18a4b, 0xe2ba79);
            paint(vg);
            cylinder(.81f, -1.16f, -1.10f, 12, 0x152b3f, 0x203e56);
            paint(vg);
            band(.76f, .72f, 0, 0, 0, -1.096f, 0x65beff, false);
            paint(vg);

            // Differently inclined bands make their front/back relationship
            // visible as the camera moves. Their phase changes the small seams.
            band(1.39f, 1.34f, time * .10f, 1.03f, .48f, .25f, 0xe2ba79, true);
            band(1.15f, 1.13f, -time * .08f, -.64f, -.60f, .22f, 0x587fa0, false);
            crystal(time);
            paint(vg);
        }

        /**
         * Flushes one nonintersecting mesh group. Pedestal tiers are painted
         * bottom-up so their broad top faces cannot interleave with the next
         * tier under an average-depth painter. The suspended meshes share one
         * group so orbital bands pass in front of and behind crystal facets.
         */
        private void paint(long vg) {
            Arrays.sort(triangles, 0, count, BACK_TO_FRONT);
            for (int i = 0; i < count; i++) {
                Triangle face = triangles[i];
                Canvas2D.beginPath(vg);
                Canvas2D.moveTo(vg, face.ax, face.ay);
                Canvas2D.lineTo(vg, face.bx, face.by);
                Canvas2D.lineTo(vg, face.cx, face.cy);
                Canvas2D.lineTo(vg, face.ax, face.ay);
                Canvas2D.color(vg, face.color, 1);
                Canvas2D.fill(vg);
                // Cover subpixel seams between neighboring, opaque triangles.
                Canvas2D.strokeWidth(vg, .45f);
                Canvas2D.stroke(vg);
                if (face.edge) {
                    Canvas2D.color(vg, 0xb5e5ff, .22f);
                    Canvas2D.strokeWidth(vg, .7f);
                    Canvas2D.stroke(vg);
                }
            }
            count = 0;
        }

        /** Restrained projected floor lines establish scale without a backdrop image. */
        private void floor(long vg) {
            Canvas2D.strokeWidth(vg, 1);
            for (int ring = 0; ring < 3; ring++) {
                float radius = 1.30f + ring * .40f;
                Canvas2D.color(vg, ring == 0 ? 0x456b86 : 0x304b64, ring == 0 ? .52f : .34f);
                for (int i = 0; i < 72; i++) {
                    float a = i * TAU / 72, b = (i + 1) * TAU / 72;
                    line(vg, (float) Math.sin(a) * radius, -1.625f, (float) Math.cos(a) * radius,
                            (float) Math.sin(b) * radius, -1.625f, (float) Math.cos(b) * radius);
                }
            }
            Canvas2D.color(vg, 0x304b64, .28f);
            for (int i = 0; i < 12; i++) {
                float angle = i * TAU / 12, sx = (float) Math.sin(angle), sz = (float) Math.cos(angle);
                line(vg, sx * 1.13f, -1.625f, sz * 1.13f, sx * 2.12f, -1.625f, sz * 2.12f);
            }
        }

        /** Two eight-sided belts form distinct, light-catching crystal facets. */
        private void crystal(float time) {
            float spin = time * .12f, lift = .055f * (float) Math.sin(time * .85f);
            for (int i = 0; i < 8; i++) {
                float a = i * TAU / 8 + spin;
                upper[i * 3] = (float) Math.sin(a) * .47f;
                upper[i * 3 + 1] = .79f + lift;
                upper[i * 3 + 2] = (float) Math.cos(a) * .47f;
                lower[i * 3] = (float) Math.sin(a + .16f) * .67f;
                lower[i * 3 + 1] = .08f + lift;
                lower[i * 3 + 2] = (float) Math.cos(a + .16f) * .67f;
            }
            for (int i = 0; i < 8; i++) {
                int a = i * 3, b = ((i + 1) % 8) * 3;
                int color = i % 3 == 0 ? 0x58c7f4 : i % 3 == 1 ? 0x278fc7 : 0x437ddd;
                triangle(0, 1.83f + lift, 0,
                        upper[a], upper[a + 1], upper[a + 2],
                        upper[b], upper[b + 1], upper[b + 2], color, true);
                triangle(upper[a], upper[a + 1], upper[a + 2],
                        lower[a], lower[a + 1], lower[a + 2],
                        lower[b], lower[b + 1], lower[b + 2], color, true);
                triangle(upper[a], upper[a + 1], upper[a + 2],
                        lower[b], lower[b + 1], lower[b + 2],
                        upper[b], upper[b + 1], upper[b + 2], mix(color, 0x93dfff, .19f), true);
                triangle(lower[a], lower[a + 1], lower[a + 2],
                        0, -.86f + lift, 0,
                        lower[b], lower[b + 1], lower[b + 2], mix(color, 0x173b91, .22f), true);
            }
        }

        /** Low-sided stacked cylinders give the pedestal a machined silhouette. */
        private void cylinder(float radius, float low, float high, int sides, int wall, int lid) {
            for (int i = 0; i < sides; i++) {
                float a = i * TAU / sides, b = (i + 1) * TAU / sides;
                float ax = (float) Math.sin(a) * radius, az = (float) Math.cos(a) * radius;
                float bx = (float) Math.sin(b) * radius, bz = (float) Math.cos(b) * radius;
                triangle(ax, low, az, bx, low, bz, bx, high, bz, wall, false);
                triangle(ax, low, az, bx, high, bz, ax, high, az, wall, false);
                triangle(0, high, 0, ax, high, az, bx, high, bz, lid, false);
            }
        }

        /** A narrow annular mesh, optionally with an outer metal edge. */
        private void band(float outerRadius, float innerRadius, float phase,
                          float pitch, float roll, float elevation, int color, boolean thick) {
            for (int i = 0; i < 64; i++) {
                float a = i * TAU / 64 + phase, b = (i + 1) * TAU / 64 + phase;
                ringPoint(ringA, 0, outerRadius, a, .016f, pitch, roll, elevation);
                ringPoint(ringA, 3, innerRadius, a, .016f, pitch, roll, elevation);
                ringPoint(ringB, 0, outerRadius, b, .016f, pitch, roll, elevation);
                ringPoint(ringB, 3, innerRadius, b, .016f, pitch, roll, elevation);
                int face = i % 16 == 0 ? mix(color, 0xfaf0cb, .42f) : color;
                triangle(ringA, 0, ringB, 0, ringB, 3, face);
                triangle(ringA, 0, ringB, 3, ringA, 3, face);
                if (thick) {
                    ringPoint(ringA, 6, outerRadius, a, -.018f, pitch, roll, elevation);
                    ringPoint(ringB, 6, outerRadius, b, -.018f, pitch, roll, elevation);
                    triangle(ringA, 6, ringB, 6, ringB, 0, mix(color, 0x6e4822, .30f));
                    triangle(ringA, 6, ringB, 0, ringA, 0, mix(color, 0x6e4822, .30f));
                }
            }
        }

        /** Builds a ring vertex in its own tilted plane before camera rotation. */
        private void ringPoint(float[] result, int offset, float radius, float angle,
                               float depth, float pitch, float roll, float elevation) {
            float x = (float) Math.sin(angle) * radius, z = (float) Math.cos(angle) * radius;
            float py = depth * (float) Math.cos(pitch) - z * (float) Math.sin(pitch);
            float pz = depth * (float) Math.sin(pitch) + z * (float) Math.cos(pitch);
            result[offset] = x * (float) Math.cos(roll) - py * (float) Math.sin(roll);
            result[offset + 1] = x * (float) Math.sin(roll) + py * (float) Math.cos(roll) + elevation;
            result[offset + 2] = pz;
        }

        private void triangle(float[] a, int ai, float[] b, int bi, float[] c, int ci, int color) {
            triangle(a[ai], a[ai + 1], a[ai + 2], b[bi], b[bi + 1], b[bi + 2],
                    c[ci], c[ci + 1], c[ci + 2], color, false, true);
        }

        private void triangle(float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, int color, boolean edge) {
            triangle(ax, ay, az, bx, by, bz, cx, cy, cz, color, edge, false);
        }

        /** Lights in world space, then transforms and projects each face. */
        private void triangle(float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, int color, boolean edge, boolean twoSided) {
            float ux = bx - ax, uy = by - ay, uz = bz - az;
            float vx = cx - ax, vy = cy - ay, vz = cz - az;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < .000001f) return;
            Triangle face = triangles[count];
            float ad = depth(ax, ay, az), bd = depth(bx, by, bz), cd = depth(cx, cy, cz);
            face.ax = screenX(ax, az, ad); face.ay = screenY(ax, ay, az, ad);
            face.bx = screenX(bx, bz, bd); face.by = screenY(bx, by, bz, bd);
            face.cx = screenX(cx, cz, cd); face.cy = screenY(cx, cy, cz, cd);
            float winding = (face.bx - face.ax) * (face.cy - face.ay)
                    - (face.by - face.ay) * (face.cx - face.ax);
            // Solid convex meshes hide their back faces. Thin orbital bands
            // remain visible from either side as the camera travels around them.
            if (!twoSided && winding >= 0) return;
            if (twoSided && winding > 0) { nx = -nx; ny = -ny; nz = -nz; }
            // A warm key light above the installation and a weaker blue rim.
            float key = Math.max(0, (-nx * .44f + ny * .79f + nz * .42f) / length);
            float rim = Math.max(0, (nx * .72f + ny * .18f - nz * .67f) / length);
            float light = .40f + key * .67f + rim * .17f;
            count++;
            face.color = lit(color, light);
            face.edge = edge;
            face.depth = (ad + bd + cd) / 3;
        }

        private void line(long vg, float ax, float ay, float az, float bx, float by, float bz) {
            float ad = depth(ax, ay, az), bd = depth(bx, by, bz);
            float x1 = screenX(ax, az, ad), y1 = screenY(ax, ay, az, ad);
            float x2 = screenX(bx, bz, bd), y2 = screenY(bx, by, bz, bd);
            // The floor extends beyond the object; reject its peripheral lines
            // when a very shallow container cannot contain the full projection.
            if (x1 < left || x1 > right || x2 < left || x2 > right ||
                    y1 < top || y1 > bottom || y2 < top || y2 > bottom) return;
            Canvas2D.beginPath(vg);
            Canvas2D.moveTo(vg, x1, y1);
            Canvas2D.lineTo(vg, x2, y2);
            Canvas2D.stroke(vg);
        }

        /** A seven-unit camera distance keeps every authored vertex in front of the eye. */
        private float depth(float x, float y, float z) {
            return 7 - (y * pitchSin + (-x * yawSin + z * yawCos) * pitchCos);
        }

        private float screenX(float x, float z, float depth) {
            return centerX + (x * yawCos + z * yawSin) * scale / depth;
        }

        private float screenY(float x, float y, float z, float depth) {
            return centerY - (y * pitchCos - (-x * yawSin + z * yawCos) * pitchSin) * scale / depth;
        }
    }

    /** Screen-space face plus the average camera distance used by the painter. */
    private static final class Triangle {
        float ax, ay, bx, by, cx, cy, depth;
        int color;
        boolean edge;
    }

    private static int lit(int rgb, float light) {
        int red = Math.min(255, Math.round(((rgb >> 16) & 255) * light));
        int green = Math.min(255, Math.round(((rgb >> 8) & 255) * light));
        int blue = Math.min(255, Math.round((rgb & 255) * light));
        return (red << 16) | (green << 8) | blue;
    }

    private static int mix(int a, int b, float amount) {
        int red = Math.round(((a >> 16) & 255) * (1 - amount) + ((b >> 16) & 255) * amount);
        int green = Math.round(((a >> 8) & 255) * (1 - amount) + ((b >> 8) & 255) * amount);
        int blue = Math.round((a & 255) * (1 - amount) + (b & 255) * amount);
        return (red << 16) | (green << 8) | blue;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
