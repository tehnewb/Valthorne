package valthorne.graphics.lighting;

import java.util.Collections;
import java.util.List;

public interface RayCastWorld {

    RayCastHit rayCast(float startX, float startY, float endX, float endY);

    default RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY) {
        return rayCast(startX, startY, endX, endY);
    }

    default List<LightOccluder> getLightOccluders() {
        return Collections.emptyList();
    }
}
