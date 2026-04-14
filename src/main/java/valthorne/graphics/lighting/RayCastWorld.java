package valthorne.graphics.lighting;

import java.util.Collections;
import java.util.List;

public interface RayCastWorld {

    RayCastHit rayCast(float startX, float startY, float endX, float endY);

    default RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY) {
        return rayCast(startX, startY, endX, endY);
    }

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

    default void prepare() {
    }

    default List<LightOccluder> getLightOccluders() {
        return Collections.emptyList();
    }
}
