package valthorne.math.physics;

import java.util.Arrays;

/**
 * Builds balanced constraints for a validated convex perimeter and its retained
 * center vertex. Shortest-ear removal avoids the high-valence center star that
 * amplifies numerical energy in stiff, densely sampled Jolt meshes. Ears touching
 * the center are preserved; the final three or four rim vertices surround it.
 * Public perimeter indices and the final center index therefore stay unchanged.
 *
 * <p>Construction uses quadratic cold-path triangulation and an ordered primitive
 * edge array, with no boxed collections or native owners. Triangle and edge
 * storage is consumed synchronously during native creation, then reclaimed.
 * Input coordinates are trusted snapshots already validated by geometry binding.
 * Numerically degenerate center containment is rejected before simulation.</p>
 */
final class PhysicsSoftTopology2D {
    /**
     * Matches convex collision outlines and bounds quadratic creation work.
     */
    static final int MAX_VERTICES = 128;

    final int[] triangles; // Packed triangle vertex indices, retaining the original perimeter winding.
    final int[] edges; // Packed unique edge endpoints, ordered by their first and second indices.

    /**
     * Triangulates a convex outline while retaining its last, interior vertex.
     * @param positions packed world XY coordinates, perimeter followed by center
     * @throws IllegalArgumentException if the outline cannot be triangulated at its precision
     */
    PhysicsSoftTopology2D(float[] positions) {
        int count = positions.length / 2 - 1;
        double centerX = positions[count * 2];
        double centerY = positions[count * 2 + 1];
        int[] ring = new int[count];
        triangles = new int[count * 3];
        double area = 0;
        for (int i = 0; i < count; i++) {
            ring[i] = i;
            int next = (i + 1) % count;
            area += ((double) positions[i * 2] - centerX) * ((double) positions[next * 2 + 1] - centerY)
                    - ((double) positions[next * 2] - centerX) * ((double) positions[i * 2 + 1] - centerY);
        }
        if (!Double.isFinite(area) || area == 0) throw new IllegalArgumentException("Soft mesh outline is degenerate");
        int winding = area > 0 ? 1 : -1;
        int remaining = count;
        int used = 0;
        while (remaining > 4) {
            int selected = -1;
            double shortest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < remaining; i++) {
                int a = ring[(i + remaining - 1) % remaining] * 2;
                int b = ring[i] * 2;
                int c = ring[(i + 1) % remaining] * 2;
                double ax = positions[a] - centerX;
                double ay = positions[a + 1] - centerY;
                double bx = positions[b] - centerX;
                double by = positions[b + 1] - centerY;
                double cx = positions[c] - centerX;
                double cy = positions[c + 1] - centerY;
                if (((bx - ax) * (cy - ay) - (by - ay) * (cx - ax)) * winding <= 0) continue;
                double dx = cx - ax;
                double dy = cy - ay;
                double length = dx * dx + dy * dy;
                double tolerance = Math.ulp(1.0) * Math.max(1, length) * 16;
                if ((ax * by - ay * bx) * winding >= -tolerance && (bx * cy - by * cx) * winding >= -tolerance && (cx * ay - cy * ax) * winding >= -tolerance) continue;
                if (length < shortest) {
                    shortest = length;
                    selected = i;
                }
            }
            if (selected < 0) throw new IllegalArgumentException("Soft mesh cannot be triangulated at its coordinate precision");
            triangles[used++] = ring[(selected + remaining - 1) % remaining];
            triangles[used++] = ring[selected];
            triangles[used++] = ring[(selected + 1) % remaining];
            System.arraycopy(ring, selected + 1, ring, selected, remaining - selected - 1);
            remaining--;
        }
        for (int i = 0; i < remaining; i++) {
            triangles[used++] = count;
            triangles[used++] = ring[i];
            triangles[used++] = ring[(i + 1) % remaining];
        }
        long[] keys = new long[triangles.length];
        for (int i = 0; i < triangles.length; i += 3) {
            for (int j = 0; j < 3; j++) {
                int first = triangles[i + j];
                int second = triangles[i + (j + 1) % 3];
                keys[i + j] = ((long) Math.min(first, second) << 32) | (Math.max(first, second) & 0xffffffffL);
            }
        }
        Arrays.sort(keys);
        int unique = 0;
        for (int i = 0; i < keys.length; i++) if (i == 0 || keys[i] != keys[i - 1]) unique++;
        edges = new int[unique * 2];
        int offset = 0;
        for (int i = 0; i < keys.length; i++) {
            if (i != 0 && keys[i] == keys[i - 1]) continue;
            edges[offset++] = (int) (keys[i] >>> 32);
            edges[offset++] = (int) keys[i];
        }
    }
}
