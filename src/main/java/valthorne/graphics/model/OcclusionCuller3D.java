package valthorne.graphics.model;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.primitives.AABBf;

/**
 * Conservative, current-frame CPU visibility tests using actual opaque triangles.
 * A bound is occluded only when its entire projected rectangle lies strictly behind
 * one accepted triangle. Holes between triangles are never filled by a proxy bound.
 * Near-plane crossings, invalid projections and uncertain coverage remain visible.
 * This deliberately misses some occlusion rather than hide visible geometry.
 *
 * <p>Begin each camera pass, add its occluders, then test candidates. Occluders must
 * actually be drawn with depth writes in that pass. The caller must preserve objects
 * needed by other passes, shadows, refraction and reflection. This class does not own
 * models or change rendering state. It is not thread-safe or reentrant.</p>
 * <pre>{@code
 * OcclusionCuller3D culler = new OcclusionCuller3D();
 * culler.begin(camera.getCombined(), viewportWidth, viewportHeight);
 * culler.addOccluder(wallModel, wallTransform, wallMaterial);
 * if (culler.test(candidateModel, candidateTransform) == OcclusionCuller3D.Visibility.VISIBLE) {
 *     // Submit the candidate to this camera pass.
 * }
 * }</pre>
 * The camera matrix must already be rebuilt. Bounds and occluders must match the
 * geometry actually drawn in this pass; repeat begin after camera or viewport changes.
 *
 * @author Albert Beaupre
 */
public final class OcclusionCuller3D {
    /**
     * Per-pass limits: retain at most 128 projected triangles and examine at most 2048 source triangles.
     */
    private static final int LIMIT = 128, SCAN_LIMIT = 2048;
    private final Matrix4f projection = new Matrix4f(), clip = new Matrix4f(); // Captured camera projection and reusable object-to-clip transform.
    private final float[] triangles = new float[LIMIT * 7], points = new float[24]; // Packed retained triangles and reusable projected vertex/corner scratch.
    private int count, scanned; // Number of retained occluders and source triangles examined this pass.
    private float marginX, marginY, minX, minY, maxX, maxY, minZ; // Viewport safety margins and current candidate rectangle/depth extrema in normalized device coordinates.
    private boolean active; // Whether begin has initialized a valid camera pass.

    /**
     * Evaluates the signed two-dimensional edge cross product without normalization.
     * Positive values lie left of the directed edge; zero lies on its infinite line.
     *
     * @param ax edge start x
     * @param ay edge start y
     * @param bx edge end x
     * @param by edge end y
     * @param x  tested point x
     * @param y  tested point y
     * @return signed cross product in squared coordinate units
     */
    private static float edge(float ax, float ay, float bx, float by, float x, float y) {
        return (bx - ax) * (y - ay) - (by - ay) * (x - ax);
    }

    /**
     * Resets fixed-size scratch for a camera pass; no previous-frame decisions survive.
     *
     * @param viewProjection current projection multiplied by view
     * @param width          viewport width in pixels, positive
     * @param height         viewport height in pixels, positive
     */
    public void begin(Matrix4fc viewProjection, int width, int height) {
        if (viewProjection == null) throw new NullPointerException("viewProjection");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Positive viewport required");
        projection.set(viewProjection);
        marginX = 4f / width;
        marginY = 4f / height;
        count = scanned = 0;
        active = true;
    }

    /**
     * Reads retained occluder count without testing another candidate. The count is
     * zero before the first begin and is reset at each subsequent begin.
     *
     * @return number of projected triangles retained for the current pass
     */
    public int getOccluderCount() {
        return count;
    }

    /**
     * Adds a bounded sample of actual triangles, without reading textures or allocating.
     * Textured, transparent, transmissive, back-face-culled and non-depth-writing
     * materials are conservatively excluded. At most 2048 triangles are examined and
     * 128 sufficiently large projected triangles retained per pass.
     *
     * @param model    triangle model (expand OBJ parts before calling)
     * @param world    current model-to-world transform
     * @param material effective drawn material
     */
    public void addOccluder(Model3D model, Matrix4fc world, Material3D material) {
        requireActive();
        if (model == null || material == null || count == LIMIT || scanned >= SCAN_LIMIT
                || material.getTexture() != null || material.getRenderPass() != RenderPass3D.OPAQUE
                || material.getTransmission() > 0 || !material.isDepthTest() || !material.isDepthWrite()
                || material.isCullBackFaces() || !(material.getTint().a() >= 1) || material.getAlphaCutoff() >= 1)
            return;
        clip.set(projection).mul(world);
        for (var triangle : model.triangles()) {
            if (count == LIMIT || scanned++ >= SCAN_LIMIT) break;
            if (!(triangle.color().a() >= 1)) continue;
            if (!project(triangle.a(), 0) || !project(triangle.b(), 3) || !project(triangle.c(), 6)) continue;
            float area = edge(points[0], points[1], points[3], points[4], points[6], points[7]);
            if (!Float.isFinite(area) || Math.abs(area) < .005f) continue;
            // Canonical winding makes all three inward edge tests positive.
            int b = area > 0 ? 3 : 6, c = area > 0 ? 6 : 3, at = count * 7;
            triangles[at] = points[0];
            triangles[at + 1] = points[1];
            triangles[at + 2] = points[b];
            triangles[at + 3] = points[b + 1];
            triangles[at + 4] = points[c];
            triangles[at + 5] = points[c + 1];
            triangles[at + 6] = Math.max(points[2], Math.max(points[5], points[8]));
            count++;
        }
    }

    /**
     * Tests a model's conservative bounds against the camera and accepted occluders.
     *
     * @param model triangle model
     * @param world current model-to-world transform
     * @return visible whenever rejection cannot be proven
     */
    public Visibility test(Model3D model, Matrix4fc world) {
        requireActive();
        if (model == null) return Visibility.VISIBLE;
        clip.set(projection).mul(world);
        return testBounds(model.localBounds());
    }

    /**
     * Tests a world-space bound, for example a prepared scene subtree.
     *
     * @param bounds conservative world-space bounds
     * @return visibility result
     */
    public Visibility test(AABBf bounds) {
        requireActive();
        clip.set(projection);
        return testBounds(bounds);
    }

    /**
     * Projects all eight corners using the prepared clip transform. A shared outside
     * plane proves offscreen status; otherwise only a safely projected rectangle strictly
     * behind one retained triangle can be occluded. Invalid bounds remain visible.
     *
     * @param b conservative bound in the coordinate space expected by clip
     * @return proven rejection or visible when coverage is uncertain
     */
    private Visibility testBounds(AABBf b) {
        if (b == null || !(b.minX <= b.maxX && b.minY <= b.maxY && b.minZ <= b.maxZ)) return Visibility.VISIBLE;
        minX = minY = minZ = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;
        int commonOutside = 63;
        boolean safeProjection = true;
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? b.minX : b.maxX, y = (i & 2) == 0 ? b.minY : b.maxY;
            float z = (i & 4) == 0 ? b.minZ : b.maxZ;
            float px = clip.m00() * x + clip.m10() * y + clip.m20() * z + clip.m30();
            float py = clip.m01() * x + clip.m11() * y + clip.m21() * z + clip.m31();
            float pz = clip.m02() * x + clip.m12() * y + clip.m22() * z + clip.m32();
            float w = clip.m03() * x + clip.m13() * y + clip.m23() * z + clip.m33();
            if (!(Float.isFinite(px) && Float.isFinite(py) && Float.isFinite(pz) && Float.isFinite(w)))
                return Visibility.VISIBLE;
            commonOutside &= (px < -(1 + marginX) * w ? 1 : 0) | (px > (1 + marginX) * w ? 2 : 0)
                    | (py < -(1 + marginY) * w ? 4 : 0) | (py > (1 + marginY) * w ? 8 : 0)
                    | (pz < -w - 1e-4f ? 16 : 0) | (pz > w + 1e-4f ? 32 : 0);
            if (w <= 1e-5f || pz <= -w + 1e-5f) {
                safeProjection = false;
                continue;
            }
            px /= w;
            py /= w;
            pz /= w;
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
            minZ = Math.min(minZ, pz);
        }
        if (commonOutside != 0) return Visibility.OFFSCREEN;
        if (!safeProjection) return Visibility.VISIBLE;
        minX -= marginX;
        maxX += marginX;
        minY -= marginY;
        maxY += marginY;
        for (int i = 0; i < count * 7; i += 7) {
            if (minZ <= triangles[i + 6] + 1e-4f) continue;
            if (inside(i, minX, minY) && inside(i, minX, maxY)
                    && inside(i, maxX, minY) && inside(i, maxX, maxY)) return Visibility.OCCLUDED;
        }
        return Visibility.VISIBLE;
    }

    /**
     * Checks strict interior coverage against the three inward-facing triangle edges.
     * The positive tolerance leaves edge-touching candidates visible.
     *
     * @param i first component of a retained seven-float triangle record
     * @param x horizontal normalized device coordinate
     * @param y vertical normalized device coordinate
     * @return whether the point clears every edge tolerance
     */
    private boolean inside(int i, float x, float y) {
        return edge(triangles[i], triangles[i + 1], triangles[i + 2], triangles[i + 3], x, y) > 1e-6f
                && edge(triangles[i + 2], triangles[i + 3], triangles[i + 4], triangles[i + 5], x, y) > 1e-6f
                && edge(triangles[i + 4], triangles[i + 5], triangles[i], triangles[i + 1], x, y) > 1e-6f;
    }

    /**
     * Transforms one vertex into normalized device coordinates in reusable scratch.
     * Rejects nonfinite output, near-plane crossings, points behind the eye, and points
     * at or beyond the far plane rather than trusting an unsafe occluder projection.
     *
     * @param v  vertex in the prepared clip transform's source space
     * @param at first of three destination scratch components
     * @return whether all three projected components are usable
     */
    private boolean project(Vector3f v, int at) {
        float x = clip.m00() * v.x + clip.m10() * v.y + clip.m20() * v.z + clip.m30();
        float y = clip.m01() * v.x + clip.m11() * v.y + clip.m21() * v.z + clip.m31();
        float z = clip.m02() * v.x + clip.m12() * v.y + clip.m22() * v.z + clip.m32();
        float w = clip.m03() * v.x + clip.m13() * v.y + clip.m23() * v.z + clip.m33();
        if (!Float.isFinite(w) || w <= 1e-5f || z <= -w + 1e-5f || z >= w) return false;
        points[at] = x / w;
        points[at + 1] = y / w;
        points[at + 2] = z / w;
        return Float.isFinite(points[at]) && Float.isFinite(points[at + 1]) && Float.isFinite(points[at + 2]);
    }

    /**
     * Checks that a camera pass has initialized projection and viewport tolerances.
     *
     * @throws IllegalStateException if begin has never completed successfully
     */
    private void requireActive() {
        if (!active) throw new IllegalStateException("begin must precede visibility tests");
    }

    /**
     * Result of a conservative test for one camera pass. A visible result includes
     * uncertain projections and does not promise that a pixel will be drawn. Rejections
     * apply only to this pass and must not remove geometry needed by secondary effects.
     *
     * @author Albert Beaupre
     */
    public enum Visibility {
        /**
         * No rejection was proven, including invalid or uncertain projected bounds.
         */
        VISIBLE,
        /**
         * All bound corners lie outside a common expanded camera clip plane.
         */
        OFFSCREEN,
        /**
         * The expanded bound lies strictly behind and inside one retained opaque triangle.
         */
        OCCLUDED
    }
}
