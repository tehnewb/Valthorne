package valthorne.graphics.lighting2d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Broad-phase XY grid used to reduce candidate occluders for radial shadow maps.
 * Fixed 256-world-unit cells contain borrowed polygon references based on their
 * translated axis-aligned bounds. Polygons spanning more than 17 cells along
 * either axis are kept in a separate list included in every ordinary query.
 *
 * <p>The owning Lighting2D detects geometry changes and calls rebuild; this
 * index does not observe occluder movement or membership changes itself. Query
 * results are conservative candidates. The shadow builder still checks category
 * masks and actual bounds overlap before processing polygon edges.</p>
 *
 * <p>A query spanning more than 33 cells along either axis returns the supplied
 * full list rather than visiting every grid cell. Ordinary queries reuse one
 * mutable result list, so callers must consume results before querying again
 * and must not modify returned lists. This helper is not thread-safe or reentrant.</p>
 *
 * @author Albert Beaupre
 */
final class OccluderIndex2D {
    /**
     * Square grid-cell extent in XY world units, shared by insertion and lookup.
     */
    private static final float CELL = 256;
    private final HashMap<Long, ArrayList<Occluder2D>> cells = new HashMap<>(); // Packed cell coordinates mapped to borrowed occluder buckets.
    private final ArrayList<Occluder2D> large = new ArrayList<>(), result = new ArrayList<>(); // Oversized polygons and the reusable ordinary-query result.
    private final IdentityHashMap<Occluder2D, Boolean> seen = new IdentityHashMap<>(); // Identity membership used to avoid repeated bucket candidates.

    /**
     * Maps a world coordinate to its containing grid cell using floor division.
     * Negative positions therefore occupy negative cells rather than truncating
     * toward zero. No validation is performed; the final Java narrowing conversion
     * determines behavior for non-finite or out-of-integer-range results.
     *
     * @param value the world coordinate along either grid axis
     * @return the signed cell coordinate
     */
    private static int cell(float value) {
        return (int) Math.floor(value / CELL);
    }

    /**
     * Packs two signed cell coordinates into distinct halves of a long without
     * losing either coordinate's bit pattern. Masking Y prevents sign extension
     * from overwriting the high X bits.
     *
     * @param x the signed horizontal cell coordinate
     * @param y the signed vertical cell coordinate
     * @return the unique packed key for this pair of integer coordinates
     */
    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /**
     * Replaces grid buckets and the oversized-polygon list using current world
     * bounds. Cell ranges include both endpoints, so a bound on a cell boundary
     * also enters that boundary's cell. Each ordinary polygon occupies at most
     * 17 by 17 cells; larger spans bypass bucket insertion.
     *
     * <p>References are retained rather than copied. Call again after geometry or
     * list membership changes, before querying. Supply unique polygon instances:
     * duplicate oversized entries are retained in their separate list.</p>
     *
     * @param occluders the current nonnull list of nonnull, valid occluders
     * @throws NullPointerException if the list or an entry is null
     */
    void rebuild(List<Occluder2D> occluders) {
        cells.clear();
        large.clear();
        for (Occluder2D o : occluders) {
            int x0 = cell(o.x + o.minX), x1 = cell(o.x + o.maxX), y0 = cell(o.y + o.minY), y1 = cell(o.y + o.maxY);
            if ((long) x1 - x0 > 16 || (long) y1 - y0 > 16) {
                large.add(o);
                continue;
            }
            for (long y = y0; y <= y1; y++)
                for (long x = x0; x <= x1; x++)
                    cells.computeIfAbsent(key((int) x, (int) y), k -> new ArrayList<>()).add(o);
        }
    }

    /**
     * Collects candidates from cells intersecting the square enclosing the light's
     * influence circle. Oversized polygons are added first in insertion order;
     * buckets follow increasing Y then X, with repeated bucket references removed
     * by identity. No category, enabled-state or exact polygon filtering occurs.
     *
     * <p>Spans above 33 cells on either axis return all directly without clearing
     * scratch results. Otherwise the shared result is cleared and reused. The
     * caller must treat either returned list as borrowed and read-only, and ensure
     * all corresponds to the population used in the latest rebuild.</p>
     *
     * @param light the nonnull light with valid position and radius
     * @param all   the complete occluder list used for large-query fallback
     * @return the reused candidate list, or all for a large query
     * @throws NullPointerException if light is null
     */
    List<Occluder2D> query(PointLight2D light, List<Occluder2D> all) {
        int x0 = cell(light.x - light.radius), x1 = cell(light.x + light.radius), y0 = cell(light.y - light.radius), y1 = cell(light.y + light.radius);
        if ((long) x1 - x0 > 32 || (long) y1 - y0 > 32) return all;
        result.clear();
        seen.clear();
        for (Occluder2D o : large) {
            seen.put(o, Boolean.TRUE);
            result.add(o);
        }
        for (long y = y0; y <= y1; y++)
            for (long x = x0; x <= x1; x++) {
                List<Occluder2D> bucket = cells.get(key((int) x, (int) y));
                if (bucket == null) continue;
                for (Occluder2D o : bucket) if (seen.put(o, Boolean.TRUE) == null) result.add(o);
            }
        return result;
    }
}
