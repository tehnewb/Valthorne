package valthorne.graphics.lighting;

import java.util.Collections;
import java.util.List;

/**
 * Supplies segment queries for two-dimensional light occlusion. Implementations
 * define geometry storage and hit-object reuse. The output-parameter overload allows
 * lighting code to reuse a result; the default adapter can still allocate through
 * the simpler query method. Queries and geometry preparation run synchronously.
 *
 * @author Albert Beaupre
 */
public interface RayCastWorld {

    /**
     * Queries a segment using the implementation's default filtering. Implementations
     * may return null or a result marked as a miss; callers must not assume a returned
     * mutable hit is an independent snapshot.
     *
     * @param startX segment start X
     * @param startY segment start Y
     * @param endX segment end X
     * @param endY segment end Y
     * @return hit result, miss result, or null according to implementation
     */
    RayCastHit rayCast(float startX, float startY, float endX, float endY);

    /**
     * Delegates to the unfiltered segment method. This default ignores the light;
     * implementations supporting light-specific masks should override it.
     *
     * @param light requesting light, ignored by this default
     * @param startX segment start X
     * @param startY segment start Y
     * @param endX segment end X
     * @param endY segment end Y
     * @return implementation's segment result
     */
    default RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY) {
        return rayCast(startX, startY, endX, endY);
    }

    /**
     * Adapts the object-returning query into optional reusable output. A miss clears
     * non-null output; a hit copies scalar values and shares collider identity. Null
     * output requests only the boolean result. No allocation-free guarantee is made.
     *
     * @param light requesting light passed to the query
     * @param startX segment start X
     * @param startY segment start Y
     * @param endX segment end X
     * @param endY segment end Y
     * @param outHit optional result storage
     * @return whether the returned query result reports a hit
     */
    default boolean rayCast(Light light, float startX, float startY, float endX, float endY, RayCastHit outHit) {
        RayCastHit hit = rayCast(light, startX, startY, endX, endY);
        if (hit == null || !hit.isHit()) {
            if (outHit != null) {
                outHit.clear();
            }
            return false;
        }

        if (outHit != null) {
            outHit.set(true, hit.getX(), hit.getY(), hit.getFraction(), hit.getCollider());
        }
        return true;
    }

    /**
     * Default no-op preparation hook. Implementations may refresh acceleration or
     * geometry state before a group of light queries.
     */
    default void prepare() {
    }

    /**
     * Returns an empty immutable list by default. Worlds exposing polygon vertices
     * can override this to support lights that cast extra rays near occluder corners.
     *
     * @return available occluders, empty in the default implementation
     */
    default List<LightOccluder> getLightOccluders() {
        return Collections.emptyList();
    }
}
